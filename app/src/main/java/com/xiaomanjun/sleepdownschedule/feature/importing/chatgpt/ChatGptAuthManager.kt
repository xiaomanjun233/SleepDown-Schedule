package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import com.xiaomanjun.sleepdownschedule.feature.importing.ChatGptUsageStatus
import com.xiaomanjun.sleepdownschedule.feature.importing.ChatGptInferenceSessions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-owned OAuth operation, independent of Activity rotation / onStop. All session writes,
 * refresh rotation, and sign-out are serialized. Only safe account metadata leaves this object.
 */
object ChatGptAuthManager {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(ChatGptAuthState())
    val state = mutableState.asStateFlow()
    @Volatile private var appContext: Context? = null
    @Volatile private var vault: ChatGptVault? = null
    @Volatile private var signingOut = false
    private var loginJob: Job? = null
    private var timeoutJob: Job? = null
    private var serviceReady: CompletableDeferred<Unit>? = null
    @Volatile private var listener: ChatGptLoopback? = null
    @Volatile private var loginHttp: ChatGptHttp? = null
    @Volatile private var loginGeneration = 0L
    @Volatile private var loginCommit: ChatGptLoginCommit? = null
    private val initializing = AtomicBoolean(false)

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (vault != null || !initializing.compareAndSet(false, true)) return
        // App startup and Compose need not block on AndroidKeyStore / disk.
        scope.launch(Dispatchers.IO) {
            try { synchronized(lock) { load(context); publish() } }
            catch (e: ChatGptAuthException) { mutableState.update { it.copy(errorMessage = e.message) } }
            finally { initializing.set(false) }
        }
    }

    fun hasSession(context: Context): Boolean {
        appContext = context.applicationContext
        if (vault == null) initialize(context)
        if (signingOut) return false
        val tokens = vault?.active()?.tokens
        return tokens != null && ChatGptProtocol.DIRECT_SCOPE in tokens.scopes &&
            (tokens.expiresAt > now() || !tokens.refreshToken.isNullOrBlank())
    }

    fun cachedModels(context: Context): List<ChatGptModel> {
        if (vault == null) initialize(context)
        return if (signingOut) emptyList() else state.value.models
    }
    fun cachedModels(): List<ChatGptModel> = appContext?.let(::cachedModels).orEmpty()

    /** Must be called off the main thread; refresh happens just before a request. */
    fun requireAccessToken(context: Context): String = synchronized(lock) { accessToken(context, null) }
    fun requireAccessToken(): String = requireAccessToken(requireContext())
    fun requireAccessToken(model: String): String = synchronized(lock) { accessToken(requireContext(), model) }
    fun requireAvailableModel(model: String) = synchronized(lock) {
        val account = load(requireContext()).active()
        if (account?.models?.none { it.slug == model } != false || model.isBlank())
            throw ChatGptAuthException("请先刷新当前 ChatGPT 账号的模型列表并选择可用模型。")
    }

    private fun accessToken(context: Context, model: String?): String {
        if (signingOut) throw ChatGptAuthException("ChatGPT 正在退出登录。")
        var current = load(context)
        var account = current.active() ?: throw ChatGptAuthException("请先登录 ChatGPT。")
        account.pendingTokens?.let { pending ->
            // A prior refresh rotated successfully but identity verification was interrupted.
            // Resume validation of the saved replacement; never resend the consumed old token.
            if (account.pendingIdentityValidation) pending.idToken?.let {
                val identity = verifyIdentity(ChatGptHttp(), it, account.clientId, null)
                if (identity.subject != account.subject) throw ChatGptAuthException("ChatGPT 续期账号校验失败，请重新登录。")
            }
            val replacement = copyVault(current)
            replacement.active()!!.apply { tokens = pending; pendingTokens = null; pendingIdentityValidation = false }
            commit(context, replacement)
            current = replacement
            account = replacement.active()!!
        }
        if (model != null && (model.isBlank() || account.models.none { it.slug == model }))
            throw ChatGptAuthException("请先刷新当前 ChatGPT 账号的模型列表并选择可用模型。")
        val tokens = account.tokens ?: throw ChatGptAuthException("请重新登录 ChatGPT。")
        if (ChatGptProtocol.DIRECT_SCOPE !in tokens.scopes) throw ChatGptAuthException("此账号尚未授权使用 ChatGPT 套餐，请在设置中启用。")
        val seconds = now()
        if (tokens.expiresAt > seconds + 60 || (tokens.expiresAt > seconds && seconds < tokens.earliestRefreshAt)) return tokens.accessToken
        val refresh = tokens.refreshToken ?: throw ChatGptAuthException("ChatGPT 登录已过期，请重新登录。")
        if (seconds < tokens.earliestRefreshAt) throw ChatGptAuthException("ChatGPT 暂时无法续期，请稍后重试。")
        val http = ChatGptHttp()
        // Fetch trusted keys before consuming a rotating refresh token, so a metadata outage
        // leaves the old renewable session untouched.
        val discovery = http.json("${ChatGptProtocol.ISSUER}/.well-known/openid-configuration")
        val jwks = http.json(authEndpoint(discovery, "jwks_uri"))
        val response = try {
            http.json(ChatGptProtocol.TOKEN, mapOf("grant_type" to "refresh_token", "client_id" to account.clientId,
                "refresh_token" to refresh, "resource" to ChatGptProtocol.RESOURCE))
        } catch (e: ChatGptAuthException) {
            if (e.oauthCode in TERMINAL_REFRESH_ERRORS) {
                ChatGptInferenceSessions.invalidate()
                val replacement = copyVault(current)
                replacement.active()?.apply { this.tokens = null; pendingTokens = null; models = emptyList() }
                commit(context, replacement)
                throw ChatGptAuthException("ChatGPT 登录已失效，请重新登录。")
            }
            throw e // Temporary failures must not erase the renewable session.
        }
        val replacementTokens = parseTokens(response, tokens.scopes, tokens.idToken, requireRefresh = true)
        val pending = copyVault(current)
        pending.active()!!.pendingTokens = replacementTokens
        pending.active()!!.pendingIdentityValidation = response.nullableString("id_token") != null
        commit(context, pending)
        response.nullableString("id_token")?.let {
            val identity = ChatGptProtocol.verifyIdToken(it, jwks, account.clientId, null, now())
            if (identity.subject != account.subject) throw ChatGptAuthException("ChatGPT 续期账号校验失败，请重新登录。")
        }
        val replacement = copyVault(current)
        replacement.active()!!.tokens = replacementTokens
        commit(context, replacement)
        if (signingOut) throw ChatGptAuthException("ChatGPT 正在退出登录。")
        if (ChatGptProtocol.DIRECT_SCOPE !in replacementTokens.scopes) throw ChatGptAuthException("ChatGPT 套餐使用权限已失效，请重新授权。")
        return replacementTokens.accessToken
    }

    fun startLogin(context: Context, addAccount: Boolean = false, accountId: String? = null, requestConsent: Boolean = false) {
        appContext = context.applicationContext
        scope.launch {
            if (loginJob?.isCompleted == false || signingOut) return@launch
            val generation = ++loginGeneration
            val gate = ChatGptLoginCommit()
            loginCommit = gate
            val ready = CompletableDeferred<Unit>()
            serviceReady = ready
            mutableState.update { it.copy(isSigningIn = true, errorMessage = null) }
            // Install the deadline before Main.immediate can fail foreground-service startup.
            timeoutJob = scope.launch {
                delay(150_000)
                if (generation == loginGeneration) cancelLogin("ChatGPT 登录已超时，请重新登录。")
            }
            loginJob = scope.launch {
                try {
                    // Start while the user's Activity is visible, and await foreground promotion.
                    ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), ChatGptLoginService::class.java)
                        .putExtra(ChatGptLoginService.EXTRA_GENERATION, generation))
                    withTimeout(5_000) { ready.await() }
                    withContext(Dispatchers.IO) {
                        val selected = synchronized(lock) {
                            val current = load(requireContext())
                            // Persist host identity before any authorization attempt.
                            commit(requireContext(), current)
                            if (addAccount) null else if (accountId != null) current.registrations.find { it.clientId == accountId }
                                ?: throw ChatGptAuthException("找不到已保存的 ChatGPT 账号。") else current.active()
                        }
                        ensureActive()
                        val callbackServer = ChatGptLoopback()
                        listener = callbackServer
                        val http = ChatGptHttp()
                        loginHttp = http
                        callbackServer.use {
                            val attempt = AuthAttempt(selected?.clientId ?: synchronized(lock) { load(requireContext()).pendingClientId }, it.redirectUri)
                            val url = ChatGptProtocol.authorizeUrl(attempt, synchronized(lock) { load(requireContext()).hostId }, selected?.tokens?.idToken, requestConsent)
                            withContext(Dispatchers.Main) {
                                ensureActive()
                                val browser = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                requireContext().startActivity(browser)
                            }
                            val callback = it.awaitCallback(attempt)
                            ensureActive()
                            if (selected == null) synchronized(lock) {
                                val pending = copyVault(load(requireContext()))
                                pending.pendingClientId = callback.clientId
                                commit(requireContext(), pending)
                            }
                            val response = http.json(ChatGptProtocol.TOKEN, mapOf("grant_type" to "authorization_code",
                                "client_id" to callback.clientId, "code" to callback.code, "code_verifier" to attempt.verifier,
                                "redirect_uri" to attempt.redirectUri, "resource" to ChatGptProtocol.RESOURCE))
                            val idToken = response.nullableString("id_token") ?: throw ChatGptAuthException("ChatGPT 未返回可验证的身份。")
                            val identity = verifyIdentity(http, idToken, callback.clientId, attempt.nonce)
                            if (selected != null && selected.subject != identity.subject) throw ChatGptAuthException("ChatGPT 账号与所选账号不一致，请重试。")
                            val tokens = parseTokens(response, null, null, requireRefresh = false)
                            ensureActive()
                            synchronized(lock) {
                                if (generation != loginGeneration || signingOut) throw CancellationException()
                                val current = load(requireContext())
                                val replacement = copyVault(current)
                                val existing = replacement.registrations.find { it.clientId == callback.clientId }
                                if (existing != null && existing.subject != identity.subject) throw ChatGptAuthException("ChatGPT 账号身份不一致。")
                                replacement.registrations.removeAll { it.clientId == callback.clientId }
                                replacement.registrations += ChatGptRegistration(callback.clientId, identity.subject, identity.email, identity.name, tokens)
                                replacement.activeClientId = callback.clientId
                                replacement.pendingClientId = null
                                val store = ChatGptCredentialStore(requireContext())
                                gate.install(write = { store.write(replacement) }, restore = { store.write(current) },
                                    publish = { ChatGptInferenceSessions.invalidate(); vault = replacement; publish() })
                                if (current.activeClientId != callback.clientId) ChatGptUsageStatus.clear()
                            }
                        }
                    }
                } catch (_: CancellationException) { /* Explicit cancel/timeout owns the UI message. */ }
                catch (e: ChatGptAuthException) { if (generation == loginGeneration) mutableState.update { it.copy(errorMessage = e.message) } }
                catch (_: Exception) { if (generation == loginGeneration) mutableState.update { it.copy(errorMessage = "无法完成 ChatGPT 登录，请检查浏览器和网络后重试。") } }
                finally {
                    if (generation == loginGeneration) finishLogin()
                }
            }
        }
    }

    fun cancelLogin() = cancelLogin(null)
    internal fun cancelLogin(message: String?) {
        ++loginGeneration // Blocks a late code exchange from installing credentials after cancel/logout.
        loginCommit?.cancel()
        listener?.close()
        loginHttp?.cancel()
        loginJob?.cancel()
        finishLogin()
        if (message != null) mutableState.update { it.copy(errorMessage = message) }
    }

    internal fun onLoginServiceReady(generation: Long) { if (generation == loginGeneration) serviceReady?.complete(Unit) }
    internal fun onLoginServiceStopped(generation: Long) {
        if (generation == loginGeneration && mutableState.value.isSigningIn) cancelLogin("ChatGPT 登录已中断，请重新登录。")
    }
    private fun finishLogin() {
        timeoutJob?.cancel(); timeoutJob = null
        listener?.close(); listener = null
        loginHttp?.cancel(); loginHttp = null
        serviceReady?.cancel(); serviceReady = null
        mutableState.update { it.copy(isSigningIn = false) }
        appContext?.let { it.stopService(Intent(it, ChatGptLoginService::class.java)) }
    }

    suspend fun models(context: Context): List<ChatGptModel> = withContext(Dispatchers.IO) {
        mutableState.update { it.copy(isLoadingModels = true) }
        try {
            synchronized(lock) {
                val token = accessToken(context, null)
                val catalog = ChatGptProtocol.models(ChatGptHttp().json("${ChatGptProtocol.RESOURCE}/models", accessToken = token))
                if (signingOut) throw ChatGptAuthException("ChatGPT 正在退出登录。")
                val replacement = copyVault(load(context))
                replacement.active()!!.models = catalog
                commit(context, replacement)
                catalog
            }
        } catch (e: ChatGptAuthException) { throw e }
        catch (_: Exception) { throw ChatGptAuthException("无法读取 ChatGPT 模型列表，请重试。") }
        finally { mutableState.update { it.copy(isLoadingModels = false) } }
    }

    suspend fun signOut(context: Context): ChatGptSignOutResult {
        signingOut = true // Immediately prevents new requests, even while a refresh owns the lock.
        ChatGptInferenceSessions.invalidate()
        cancelLogin()
        return withContext(NonCancellable + Dispatchers.IO) {
            var cleared = false
            try {
                synchronized(lock) {
                    val current = load(context)
                    val account = current.active()
                    var confirmed = account?.tokens == null
                    val refresh = account?.pendingTokens?.refreshToken ?: account?.tokens?.refreshToken
                    if (refresh != null && account != null) {
                        val http = ChatGptHttp()
                        for (attempt in 0..1) {
                            try {
                                val endpoint = authEndpoint(http.json("${ChatGptProtocol.ISSUER}/.well-known/openid-configuration"), "revocation_endpoint")
                                http.json(endpoint, mapOf("token" to refresh, "token_type_hint" to "refresh_token", "client_id" to account.clientId))
                                confirmed = true
                                break
                            } catch (e: ChatGptAuthException) {
                                if (attempt == 1 || (e.status != null && e.status !in 500..599)) break
                                Thread.sleep(500)
                            }
                        }
                    }
                    val replacement = copyVault(current)
                    replacement.active()?.apply { tokens = null; pendingTokens = null; models = emptyList() }
                    commit(context, replacement)
                    cleared = true
                    ChatGptUsageStatus.clear()
                    ChatGptSignOutResult(confirmed, if (confirmed) "已退出 ChatGPT。" else "已清除本机 ChatGPT 登录；未能确认远端撤销，请在 ChatGPT 设置中断开此应用。")
                }
            } finally {
                // A failed local wipe is not a successful sign-out. Keep requests blocked;
                // another explicit sign-out can retry after storage becomes writable.
                signingOut = !cleared
            }
        }
    }

    private fun verifyIdentity(http: ChatGptHttp, idToken: String, clientId: String, nonce: String?): VerifiedIdentity {
        val discovery = http.json("${ChatGptProtocol.ISSUER}/.well-known/openid-configuration")
        val jwks = http.json(authEndpoint(discovery, "jwks_uri"))
        return ChatGptProtocol.verifyIdToken(idToken, jwks, clientId, nonce, now())
    }

    private fun authEndpoint(discovery: JSONObject, field: String): String {
        try {
            require(discovery.getString("issuer") == ChatGptProtocol.ISSUER)
            val endpoint = discovery.getString(field)
            val uri = URI(endpoint)
            require(uri.scheme == "https" && uri.host == "auth.openai.com" && uri.port in setOf(-1, 443) && uri.userInfo == null && uri.rawFragment == null)
            return endpoint
        } catch (_: Exception) { throw ChatGptAuthException("ChatGPT 身份服务配置校验失败。") }
    }

    private fun parseTokens(response: JSONObject, previousScopes: Set<String>?, previousId: String?, requireRefresh: Boolean): ChatGptTokens {
        try {
            require(response.getString("token_type").equals("Bearer", ignoreCase = true))
            val access = response.getString("access_token")
            require(access.isNotBlank() && access.none { it <= ' ' })
            val expiresIn = response.getLong("expires_in")
            require(expiresIn in 1..86_400)
            val refresh = response.nullableString("refresh_token")
            require(!requireRefresh || refresh != null)
            val scopes = if (response.has("scope")) response.getString("scope").split(Regex("\\s+")).filter(String::isNotBlank).toSet() else previousScopes.orEmpty()
            return ChatGptTokens(access, refresh, response.nullableString("id_token") ?: previousId, now() + expiresIn, scopes, response.optLong("earliest_refresh_at"))
        } catch (_: Exception) { throw ChatGptAuthException("ChatGPT 登录凭据不完整，请重新登录。") }
    }

    private fun load(context: Context): ChatGptVault {
        appContext = context.applicationContext
        return vault ?: ChatGptCredentialStore(context.applicationContext).read().also { vault = it }
    }
    private fun copyVault(current: ChatGptVault) = ChatGptCredentialStore.decode(ChatGptCredentialStore.encode(current))
    private fun commit(context: Context, replacement: ChatGptVault) {
        ChatGptCredentialStore(context.applicationContext).write(replacement)
        vault = replacement
        publish()
    }
    private fun publish() {
        val current = vault ?: return
        mutableState.update { it.copy(account = current.active()?.publicAccount(), accounts = current.registrations.map { account -> account.publicAccount() },
            models = current.active()?.takeIf { account -> account.tokens != null }?.models.orEmpty()) }
    }
    private fun requireContext() = appContext ?: throw ChatGptAuthException("ChatGPT 登录尚未初始化。")
    private fun now() = System.currentTimeMillis() / 1_000
    private val TERMINAL_REFRESH_ERRORS = setOf("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused")
}

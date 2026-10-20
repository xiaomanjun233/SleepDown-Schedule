package com.xiaomanjun.sleepdownschedule.core.remoteconfig

import com.xiaomanjun.sleepdownschedule.core.analytics.InstallationAnalytics
import android.content.Context
import androidx.core.content.edit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SleepDownRemoteConfig {
    private const val RefreshIntervalMillis = 5L * 60L * 1_000L
    private const val ExperiencePrefs = "sleepdown_remote_experience"
    private const val ShownNoticesKey = "shown_notice_ids_v1"
    private const val AcceptedPrivacyKey = "accepted_privacy_version"
    private const val AcceptedTermsKey = "accepted_terms_version"

    private val initialized = AtomicBoolean(false)
    private val refreshInProgress = AtomicBoolean(false)
    private val dialogNoticesShownThisProcess = ConcurrentHashMap.newKeySet<Long>()
    private val mutableState = MutableStateFlow(RemoteConfigState())
    val state = mutableState.asStateFlow()
    private val mutableExperience = MutableStateFlow(RemoteExperienceState())
    val experience = mutableExperience.asStateFlow()

    @Volatile private var store: RemoteConfigStore? = null
    @Volatile private var appContext: Context? = null

    private var onBootstrapLoaded: (Context) -> Unit = {}
    private var onBootstrapUpdated: () -> Unit = {}

    fun initialize(
        context: Context,
        scope: CoroutineScope,
        onBootstrapLoaded: (Context) -> Unit,
        onBootstrapUpdated: () -> Unit
    ) {
        val applicationContext = context.applicationContext
        if (!initialized.compareAndSet(false, true)) return
        this.onBootstrapLoaded = onBootstrapLoaded
        this.onBootstrapUpdated = onBootstrapUpdated
        appContext = applicationContext
        val configStore = RemoteConfigStore(applicationContext)
        store = configStore
        val cached = configStore.load()
        mutableState.value = RemoteConfigState(cached?.bootstrap, cached?.lastFetchTimeMillis ?: 0L)
        recomputeExperience(applicationContext)
        // The bootstrap is a persisted, encrypted transport configuration. Once it is available,
        // make the resolved provider active immediately so a process restart does not turn a valid
        // backend-issued key back into an unconfigured "none" provider.
        onBootstrapLoaded(applicationContext)
        scope.launch(Dispatchers.IO) {
            refreshIfNeeded(applicationContext, force = false)
            InstallationAnalytics(applicationContext).sync()
        }
    }

    fun refresh(scope: CoroutineScope, force: Boolean = true) {
        val context = appContext ?: return
        scope.launch(Dispatchers.IO) { refreshIfNeeded(context, force) }
    }

    private fun refreshIfNeeded(context: Context, force: Boolean) {
        val configStore = store ?: return
        val previous = configStore.load()
        if (!force && System.currentTimeMillis() - (previous?.lastFetchTimeMillis ?: 0L) < RefreshIntervalMillis) return
        if (!refreshInProgress.compareAndSet(false, true)) return
		mutableState.value = mutableState.value.copy(lastError = null, isRefreshing = true)
        try {
            when (val result = RemoteConfigClient().fetchBootstrap(previous?.etag)) {
                is BootstrapFetchResult.Updated -> {
                    configStore.save(result.bootstrap, result.etag)
                    mutableState.value = RemoteConfigState(result.bootstrap, System.currentTimeMillis())
                    recomputeExperience(context)
                    onBootstrapLoaded(context)
                    onBootstrapUpdated()
                }
                BootstrapFetchResult.NotModified -> {
                    configStore.markNotModified()
                    mutableState.value = mutableState.value.copy(lastFetchTimeMillis = System.currentTimeMillis(), lastError = null)
                }
            }
        } catch (error: Throwable) {
            mutableState.value = mutableState.value.copy(lastError = error.message ?: "远程配置刷新失败")
        } finally {
			mutableState.value = mutableState.value.copy(isRefreshing = false)
            refreshInProgress.set(false)
        }
    }

    fun markNoticeShown(context: Context, notice: RemoteNotice) {
        if (notice.displayMode == "dialog") {
            dialogNoticesShownThisProcess += notice.id
        } else {
            val prefs = context.getSharedPreferences(ExperiencePrefs, Context.MODE_PRIVATE)
            val shown = prefs.getStringSet(ShownNoticesKey, emptySet()).orEmpty().toMutableSet()
            shown += notice.id.toString()
            prefs.edit { putStringSet(ShownNoticesKey, shown) }
        }
        recomputeExperience(context.applicationContext)
    }

    fun acceptAgreement(context: Context, agreement: RemoteAgreementSummary) {
        val type = mutableState.value.bootstrap?.agreements?.let { set ->
            when (agreement) { set.privacy -> "privacy"; set.terms -> "terms"; else -> null }
        } ?: return
        context.getSharedPreferences(ExperiencePrefs, Context.MODE_PRIVATE).edit(commit = true) {
            putLong(if (type == "privacy") AcceptedPrivacyKey else AcceptedTermsKey, agreement.version)
        }
        recomputeExperience(context.applicationContext)
    }

    private fun recomputeExperience(context: Context) {
        val bootstrap = mutableState.value.bootstrap
        if (bootstrap == null) { mutableExperience.value = RemoteExperienceState(); return }
        val prefs = context.getSharedPreferences(ExperiencePrefs, Context.MODE_PRIVATE)
        val agreement = listOfNotNull(bootstrap.agreements.privacy, bootstrap.agreements.terms).firstOrNull { item ->
            val accepted = prefs.getLong(
                if (item === bootstrap.agreements.privacy) AcceptedPrivacyKey else AcceptedTermsKey,
                0L
            )
            item.required && item.version > accepted && (accepted == 0L || item.forceReaccept)
        }
        val shown = prefs.getStringSet(ShownNoticesKey, emptySet()).orEmpty()
        val now = estimatedServerTimeSeconds()
        val notice = bootstrap.notices.firstOrNull { item ->
            item.enabled && item.startAt <= now && (item.endAt == null || item.endAt > now) &&
                if (item.displayMode == "dialog") item.id !in dialogNoticesShownThisProcess
                else item.id.toString() !in shown
        }
        mutableExperience.value = RemoteExperienceState(agreement, notice)
    }

    internal fun estimatedServerTimeSeconds(): Long = store?.estimatedServerTimeSeconds() ?: System.currentTimeMillis() / 1_000L
}

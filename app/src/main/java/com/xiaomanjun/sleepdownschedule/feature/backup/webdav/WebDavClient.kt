package com.xiaomanjun.sleepdownschedule.feature.backup.webdav

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class WebDavFailure(val reason: Reason) : IOException(reason.message) {
    enum class Reason(val message: String) {
        ADDRESS("请输入有效的 WebDAV 文件夹地址，不要在地址中填写密码或查询参数"),
        AUTH("认证失败，请检查用户名、密码和服务端授权"),
        MISSING("远端文件或文件夹不存在，请检查目录地址并刷新列表"),
        COLLISION("远端已有同名文件，请确认覆盖或换一个名称"),
        CHANGED("远端文件已变化，请刷新列表后重新确认"),
        UNSAFE_OVERWRITE("服务器未提供可用于安全覆盖的文件版本，请更换名称上传备份"),
        TIMEOUT("连接或传输超时，请检查网络后重试"),
        NETWORK("无法连接 WebDAV，请检查网络、证书和服务地址"),
        UNSUPPORTED("服务器不支持所需 WebDAV 操作；原备份未通过直接写入覆盖"),
        RESPONSE("WebDAV 返回了无效或过大的响应"),
        FILE("备份文件过大或文件名无效"),
        STORAGE("远端空间不足或禁止写入"),
        HTTP("WebDAV 操作失败，请检查服务端权限和状态")
    }
}

internal data class WebDavEntry(val name: String, val size: Long?, val modified: String?, val etag: String?)

/** Credentials never appear in URLs, exception messages or toString(). */
internal class WebDavConnection(val address: String, val username: String, val password: String) {
    override fun toString() = "WebDavConnection(<private>)"
}

internal class WebDavClient(connection: WebDavConnection, client: OkHttpClient = defaultClient()) {
    private val base = folderUrl(connection.address)
    private val authorization = Credentials.basic(connection.username, connection.password)
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()

    suspend fun list(): List<WebDavEntry> = exchange(request(base).header("Depth", "1")
        .method("PROPFIND", """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:getetag/></d:prop></d:propfind>"""
            .toRequestBody("application/xml; charset=utf-8".toMediaType())).build()) { response ->
        expect(response, setOf(207))
        val bytes = response.body?.byteStream()?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() <= ListLimit) {
                val count = input.read(buffer, 0, minOf(buffer.size, ListLimit + 1 - output.size()))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
            ?: throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
        if (bytes.size > ListLimit) throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
        parseListing(base, bytes)
    }

    suspend fun upload(file: File, name: String, overwrite: Boolean = false, etag: String? = null) {
        val target = fileUrl(name)
        if (file.length() !in 1..ArchiveLimit) throw WebDavFailure(WebDavFailure.Reason.FILE)
        // The validator must identify the version the user confirmed, before any remote write.
        if (overwrite && (etag == null || !validEtag(etag)))
            throw WebDavFailure(WebDavFailure.Reason.UNSAFE_OVERWRITE)
        val temporary = base.newBuilder().addPathSegment(".sleepdown-${UUID.randomUUID()}.upload").build()
        try {
            exchange(request(temporary).header("If-None-Match", "*")
                .put(file.asRequestBody("application/octet-stream".toMediaType())).build()) {
                expect(it, setOf(200, 201, 204))
            }
            // RFC 4918 MOVE commits the already complete upload. Never PUT over an existing archive.
            val move = request(temporary).method("MOVE", null).header("Destination", target.toString())
                .header("Overwrite", if (overwrite) "T" else "F")
            if (overwrite) move.header("If", "<$target> ([$etag])")
            exchange(move.build()) { response ->
                if (response.code == 412) throw WebDavFailure(if (overwrite) WebDavFailure.Reason.CHANGED else WebDavFailure.Reason.COLLISION)
                expect(response, setOf(201, 204))
            }
        } finally {
            // Only our unpredictable staging name is eligible for cleanup, including cancellation.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                runCatching {
                    exchange(request(temporary).delete().build(), timeoutSeconds = 5) { it.close() }
                }
            }
        }
    }

    suspend fun download(entry: WebDavEntry, destination: File) {
        val get = request(fileUrl(entry.name))
        entry.etag?.takeIf(::validEtag)?.let { get.header("If-Match", it) }
        try {
            exchange(get.get().build()) { response ->
                if (response.code == 412) throw WebDavFailure(WebDavFailure.Reason.CHANGED)
                expect(response, setOf(200))
                val body = response.body ?: throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
                if (body.contentLength() > ArchiveLimit) throw WebDavFailure(WebDavFailure.Reason.FILE)
                body.byteStream().use { input -> destination.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > ArchiveLimit) throw WebDavFailure(WebDavFailure.Reason.FILE)
                        output.write(buffer, 0, count)
                    }
                } }
            }
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url).header("Authorization", authorization)
    private fun fileUrl(name: String): HttpUrl {
        if (!validName(name)) throw WebDavFailure(WebDavFailure.Reason.FILE)
        return base.newBuilder().addPathSegment(name).build()
    }

    private suspend fun <T> exchange(request: Request, timeoutSeconds: Long = 90, consume: (Response) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(safeFailure(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    if (!continuation.isActive) { response.close(); return }
                    try {
                        val result = response.use(consume)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(safeFailure(error))
                    }
                }
            })
        }

    companion object {
        const val ArchiveLimit = 128L * 1024 * 1024
        private const val ListLimit = 4 * 1024 * 1024
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).build()

        fun folderUrl(address: String): HttpUrl {
            val url = address.trim().toHttpUrlOrNull() ?: throw WebDavFailure(WebDavFailure.Reason.ADDRESS)
            if (url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
                throw WebDavFailure(WebDavFailure.Reason.ADDRESS)
            return if (url.encodedPath.endsWith('/')) url else url.newBuilder().addPathSegment("").build()
        }
        fun validName(name: String) = name.endsWith(".sleepdown", ignoreCase = true) && name.length <= 180 &&
            name.isNotBlank() && name.none { it == '/' || it == '\\' || it.code < 32 } && name !in setOf(".", "..")
        // Accept one strong, quoted opaque tag that is safe in an HTTP/WebDAV If header.
        // Spaces, embedded quotes, controls and non-ASCII header values are not usable validators.
        private fun validEtag(value: String) = value.length in 2..511 && value.first() == '"' && value.last() == '"' &&
            value.substring(1, value.lastIndex).all { it == '!' || it in '#'..'~' && it != ']' }
        private fun safeFailure(error: Exception): WebDavFailure = when (error) {
            is WebDavFailure -> error
            is java.io.InterruptedIOException -> WebDavFailure(WebDavFailure.Reason.TIMEOUT)
            else -> WebDavFailure(WebDavFailure.Reason.NETWORK)
        }
        private fun expect(response: Response, codes: Set<Int>) {
            if (response.code in codes) return
            throw WebDavFailure(when (response.code) {
                401, 403 -> WebDavFailure.Reason.AUTH
                404 -> WebDavFailure.Reason.MISSING
                405, 501 -> WebDavFailure.Reason.UNSUPPORTED
                412 -> WebDavFailure.Reason.COLLISION
                507 -> WebDavFailure.Reason.STORAGE
                else -> WebDavFailure.Reason.HTTP
            })
        }

        internal fun parseListing(base: HttpUrl, bytes: ByteArray): List<WebDavEntry> {
            try {
                // Reject DTDs before parsing as Android XML providers differ in supported features.
                val xml = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                if (xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true))
                    throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    isExpandEntityReferences = false
                }
                val builder = factory.newDocumentBuilder()
                builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
                builder.setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                    override fun error(e: org.xml.sax.SAXParseException) { throw e }
                    override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
                })
                val document = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xml.removePrefix("\uFEFF"))))
                if (document.documentElement.localName != "multistatus" || document.documentElement.namespaceURI != "DAV:")
                    throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
                val responses = document.getElementsByTagNameNS("DAV:", "response")
                if (responses.length > 4096) throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
                fun Element.text(name: String) = getElementsByTagNameNS("DAV:", name).item(0)?.textContent?.trim()
                return (0 until responses.length).mapNotNull { index ->
                    val element = responses.item(index) as Element
                    val href = element.text("href") ?: return@mapNotNull null
                    val url = base.resolve(href) ?: return@mapNotNull null
                    if (url.scheme != base.scheme || url.host != base.host || url.port != base.port ||
                        url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null ||
                        url.pathSegments.dropLast(1) != base.pathSegments.dropLast(1)) return@mapNotNull null
                    val name = url.pathSegments.last()
                    if (!validName(name)) return@mapNotNull null
                    val properties = element.getElementsByTagNameNS("DAV:", "propstat")
                    val good = (0 until properties.length).map { properties.item(it) as Element }
                        .filter { it.text("status")?.split(' ')?.getOrNull(1) == "200" }
                    if (good.isEmpty() || good.any { it.getElementsByTagNameNS("DAV:", "collection").length > 0 }) return@mapNotNull null
                    fun property(key: String) = good.firstNotNullOfOrNull { it.text(key) }
                    WebDavEntry(name, property("getcontentlength")?.toLongOrNull(), property("getlastmodified"), property("getetag"))
                }.distinctBy { it.name }.sortedByDescending { it.name }
            } catch (error: Exception) {
                throw WebDavFailure(WebDavFailure.Reason.RESPONSE)
            }
        }
    }
}

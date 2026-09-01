package com.xiaomanjun.sleepdownschedule.feature.importing.shiguang

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Collections

internal val ShiguangPostInterceptScript = """
(function() {
    var bridge = window.WebPostService;
    if (!bridge || window._postInterceptInjected) return;
    window._postInterceptInjected = true;

    var requestIdHeader = 'X-WebView-Post-Id';
    var requestIdParam = '_webview_post_id';

    function register(id, body, contentType) {
        try {
            if (window.WebPostService && window.WebPostService.register) {
                window.WebPostService.register(id, body, contentType || '');
            }
        } catch(e) {
            console.error("WebPostService register error:", e);
        }
    }

    var oldOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._method = method;
        this._url = url;
        this._headers = {};
        return oldOpen.apply(this, arguments);
    };

    var oldSetRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
    XMLHttpRequest.prototype.setRequestHeader = function(header, value) {
        try {
            if (this._headers && header) this._headers[header] = value;
            if (header && header.toLowerCase() === 'x-requested-with') return;
        } catch(e) {
            console.error("XHR setRequestHeader error:", e);
        }
        return oldSetRequestHeader.apply(this, arguments);
    };

    var oldSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
        try {
            if (this._method && this._method.toUpperCase() !== 'GET' && body) {
                var id = 'xhr_' + Date.now() + '_' + Math.random().toString(36).substr(2);
                var contentType = (this._headers && (this._headers['Content-Type'] || this._headers['content-type'])) || '';
                var bodyStr = '';
                if (typeof body === 'string') {
                    bodyStr = body;
                } else if (window.FormData && body instanceof FormData) {
                    if (window.URLSearchParams) {
                        var params = new URLSearchParams();
                        for (var pair of body.entries()) params.append(pair[0], pair[1]);
                        bodyStr = params.toString();
                    }
                    if (!contentType) contentType = 'application/x-www-form-urlencoded';
                } else if (window.URLSearchParams && body instanceof URLSearchParams) {
                    bodyStr = body.toString();
                    if (!contentType) contentType = 'application/x-www-form-urlencoded';
                }
                if (bodyStr) {
                    register(id, bodyStr, contentType);
                    this.setRequestHeader(requestIdHeader, id);
                }
            }
        } catch(e) {
            console.error("XHR send intercept error:", e);
        }
        return oldSend.apply(this, arguments);
    };

    if (window.fetch) {
        var oldFetch = window.fetch;
        window.fetch = function(input, init) {
            try {
                var options = init || {};
                var method = options.method;
                if (!method && input && typeof input === 'object' && input.method) method = input.method;
                if (!method) method = 'GET';
                var body = options.body;
                if (method.toUpperCase() !== 'GET' && body) {
                    var id = 'fetch_' + Date.now() + '_' + Math.random().toString(36).substr(2);
                    var contentType = '';
                    var headers = options.headers || (input && typeof input === 'object' ? input.headers : null);
                    if (headers) {
                        if (window.Headers && headers instanceof Headers) {
                            contentType = headers.get('Content-Type') || headers.get('content-type') || '';
                        } else if (Array.isArray(headers)) {
                            for (var i = 0; i < headers.length; i++) {
                                if (headers[i] && headers[i][0] && headers[i][0].toLowerCase() === 'content-type') {
                                    contentType = headers[i][1];
                                    break;
                                }
                            }
                        } else if (typeof headers === 'object') {
                            contentType = headers['Content-Type'] || headers['content-type'] || '';
                        }
                    }
                    var bodyStr = '';
                    if (typeof body === 'string') {
                        bodyStr = body;
                    } else if (window.URLSearchParams && body instanceof URLSearchParams) {
                        bodyStr = body.toString();
                        if (!contentType) contentType = 'application/x-www-form-urlencoded';
                    } else if (window.FormData && body instanceof FormData) {
                        if (window.URLSearchParams) {
                            var p = new URLSearchParams();
                            for (var pair of body.entries()) p.append(pair[0], pair[1]);
                            bodyStr = p.toString();
                        }
                        if (!contentType) contentType = 'application/x-www-form-urlencoded';
                    }
                    if (bodyStr) {
                        register(id, bodyStr, contentType);
                        if (!options.headers) options.headers = {};
                        if (window.Headers && options.headers instanceof Headers) {
                            options.headers.set(requestIdHeader, id);
                        } else if (Array.isArray(options.headers)) {
                            options.headers.push([requestIdHeader, id]);
                        } else if (typeof options.headers === 'object') {
                            options.headers[requestIdHeader] = id;
                        }
                    }
                }
                return oldFetch.call(this, input, options);
            } catch(e) {
                console.error("Fetch intercept error:", e);
                return oldFetch.apply(this, arguments);
            }
        };
    }

    document.addEventListener('submit', function(e) {
        try {
            var form = e.target;
            if (!form || !form.tagName || form.tagName.toLowerCase() !== 'form') return;
            if (!form.method || form.method.toLowerCase() !== 'post') return;
            if (form.querySelector && form.querySelector('input[type="file"]')) return;
            var id = 'form_' + Date.now() + '_' + Math.random().toString(36).substr(2);
            var formData = new FormData(form);
            var submitter = e.submitter || document.activeElement;
            if (submitter && submitter.form === form && submitter.name) {
                formData.append(submitter.name, submitter.value);
            }
            if (window.URLSearchParams) {
                var params = new URLSearchParams(formData);
                register(id, params.toString(), 'application/x-www-form-urlencoded');
                var action = form.getAttribute('action') || window.location.href;
                var separator = action.indexOf('?') !== -1 ? '&' : '?';
                form.setAttribute('action', action + separator + requestIdParam + '=' + id);
            }
        } catch(err) {
            console.error("Form submit intercept error:", err);
        }
    }, true);
})();
""".trimIndent()

internal class ShiguangWebRequestInterceptor {
    private data class RegisteredPostData(val body: String, val contentType: String)

    companion object {
        private val postBodyRegistry = Collections.synchronizedMap(mutableMapOf<String, RegisteredPostData>())

        private fun registerPostData(id: String, body: String, contentType: String) {
            postBodyRegistry[id] = RegisteredPostData(body, contentType)
        }
    }

    private val cookieManager = CookieManager.getInstance()

    fun intercept(request: WebResourceRequest, desktopMode: Boolean): WebResourceResponse? {
        val rawUrl = request.url.toString()
        if (!desktopMode || (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://"))) return null

        val requestIdHeader = request.requestHeaders.entries
            .firstOrNull { it.key.equals("X-WebView-Post-Id", ignoreCase = true) }
            ?.value
        val requestIdParam = request.url.getQueryParameter("_webview_post_id")
        val requestId = requestIdHeader ?: requestIdParam
        if (!request.isForMainFrame && requestId == null) return null

        val url = request.url.withoutInternalPostId(requestIdParam != null)
        val registeredData = requestId?.let(postBodyRegistry::remove)
        if (!request.method.equals("GET", ignoreCase = true) && registeredData == null) return null

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = !request.isForMainFrame
            requestMethod = request.method
            request.requestHeaders.forEach { (key, value) ->
                if (!key.equals("X-Requested-With", ignoreCase = true) &&
                    !key.equals("X-WebView-Post-Id", ignoreCase = true) &&
                    !key.equals("Accept-Encoding", ignoreCase = true)) {
                    setRequestProperty(key, value)
                }
            }
            setRequestProperty("Accept-Encoding", "identity")
            cookieManager.getCookie(url)?.takeIf(String::isNotBlank)?.let { setRequestProperty("Cookie", it) }
            registeredData?.let { data ->
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    data.contentType.ifBlank { "application/x-www-form-urlencoded" }
                )
                outputStream.use { it.write(data.body.toByteArray(Charsets.UTF_8)) }
            }
        }

        return try {
            val status = connection.responseCode
            connection.headerFields["Set-Cookie"]?.forEach { cookieManager.setCookie(url, it) }
            cookieManager.flush()
            if (status in 300..399 && request.isForMainFrame) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location == null) return null
                val destination = resolveAbsoluteUrl(url, location)
                val html = "<html><script>window.location.replace(${JSONObject.quote(destination)});</script></html>"
                return WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    200,
                    "OK",
                    mapOf("Cache-Control" to "no-cache"),
                    html.byteInputStream()
                )
            }

            val contentType = connection.contentType.orEmpty()
            val mimeType = contentType.substringBefore(';').ifBlank { "text/html" }
            val encoding = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                .find(contentType)?.groupValues?.get(1)?.trim()?.trim('"') ?: "UTF-8"
            val responseHeaders = connection.headerFields
                .filterKeys { it != null && !it.equals("Content-Encoding", ignoreCase = true) }
                .mapValues { (_, values) -> values.joinToString(", ") }
            val responseStream = connection.inputStreamOrError().disconnectWith(connection)
            WebResourceResponse(
                mimeType,
                encoding,
                status,
                connection.responseMessage.orEmpty().ifBlank { "OK" },
                responseHeaders,
                responseStream
            )
        } catch (error: Exception) {
            connection.disconnect()
            null
        }
    }

    private fun Uri.withoutInternalPostId(hasInternalId: Boolean): String {
        if (!hasInternalId) return toString()
        val builder = buildUpon().clearQuery()
        queryParameterNames.forEach { name ->
            if (name != "_webview_post_id") {
                getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
            }
        }
        return builder.build().toString()
    }

    private fun resolveAbsoluteUrl(baseUrl: String, location: String): String =
        try {
            URI(baseUrl).resolve(location).toString()
        } catch (_: Exception) {
            location
        }

    private fun HttpURLConnection.inputStreamOrError(): InputStream =
        if (responseCode >= 400) errorStream ?: inputStream else inputStream

    private fun InputStream.disconnectWith(connection: HttpURLConnection): InputStream =
        object : FilterInputStream(this) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    connection.disconnect()
                }
            }
        }

    internal class WebPostService {
        @JavascriptInterface
        fun register(id: String, body: String, contentType: String) {
            registerPostData(id, body, contentType)
        }
    }
}

internal fun WebView.installShiguangRuntime(host: ShiguangBridgeHost) {
    addJavascriptInterface(ShiguangNativeBridge(host), "_shiguangNativeBridge")
    addJavascriptInterface(ShiguangWebRequestInterceptor.WebPostService(), "WebPostService")
}

internal fun WebView.injectShiguangRuntime(desktopMode: Boolean) {
    evaluateJavascript(ShiguangBridgeInitScript, null)
    if (desktopMode) evaluateJavascript(ShiguangPostInterceptScript, null)
}

internal fun WebView.uninstallShiguangRuntime() {
    removeJavascriptInterface("_shiguangNativeBridge")
    removeJavascriptInterface("WebPostService")
}

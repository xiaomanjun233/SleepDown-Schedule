package com.xiaomanjun.sleepdownschedule.feature.importing.chatgpt

import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException

/** One-shot, bounded HTTP listener. No callback data appears in the browser response. */
internal class ChatGptLoopback : Closeable {
    private val server = ServerSocket().apply {
        reuseAddress = false
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 4)
        soTimeout = 1_000
    }
    val redirectUri = "http://127.0.0.1:${server.localPort}/auth/callback"
    @Volatile private var activeSocket: java.net.Socket? = null

    fun awaitCallback(attempt: AuthAttempt, timeoutMillis: Long = 120_000): AuthCallback {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        while (!server.isClosed && System.nanoTime() < deadline) {
            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
            socket.use {
                activeSocket = it
                it.soTimeout = 2_000
                try {
                    val input = it.getInputStream()
                    val line = boundedLine(input, 17_000)
                    val parts = line.split(' ')
                    val headers = linkedMapOf<String, String>()
                    var size = 0
                    while (true) {
                        val header = boundedLine(input, 4_096)
                        size += header.length
                        require(size <= 16_384)
                        if (header.isEmpty()) break
                        val separator = header.indexOf(':')
                        require(separator > 0)
                        val key = header.substring(0, separator).lowercase()
                        require(key !in headers)
                        headers[key] = header.substring(separator + 1).trim()
                    }
                    require(parts.size == 3 && parts[0] == "GET" && parts[2] == "HTTP/1.1")
                    require(headers["host"] == "127.0.0.1:${server.localPort}")
                    require(headers["transfer-encoding"] == null && (headers["content-length"] ?: "0") == "0")
                    val callback = ChatGptProtocol.callback(parts[1], attempt)
                    respond(it, true)
                    return callback
                } catch (e: ChatGptAuthException) {
                    runCatching { respond(it, false) }
                    // Invalid/spurious callbacks cannot cancel the real pending attempt.
                    if (e.message == "已取消 ChatGPT 授权。" || e.message == "ChatGPT 授权未完成，请重试。") throw e
                } catch (_: Exception) {
                    runCatching { respond(it, false) }
                } finally { activeSocket = null }
            }
        }
        throw ChatGptAuthException("ChatGPT 登录已超时，请重新登录。")
    }

    private fun boundedLine(input: InputStream, max: Int): String {
        val out = StringBuilder()
        while (out.length <= max) {
            val byte = input.read()
            require(byte >= 0)
            if (byte == 10) {
                require(out.isNotEmpty() && out.last() == '\r')
                return out.dropLast(1).toString()
            }
            require(byte in 32..126 || byte == 13)
            out.append(byte.toChar())
        }
        throw IllegalArgumentException()
    }

    private fun respond(socket: java.net.Socket, accepted: Boolean) {
        val body = if (accepted) "Authorization received. Return to SleepDown to finish sign-in."
            else "This sign-in callback was not accepted. Return to SleepDown."
        val status = if (accepted) "200 OK" else "400 Bad Request"
        val response = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\nCache-Control: no-store\r\n" +
            "Content-Security-Policy: default-src 'none'\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\n$body"
        socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
    }

    override fun close() {
        runCatching { activeSocket?.close() }
        runCatching { server.close() }
    }
}

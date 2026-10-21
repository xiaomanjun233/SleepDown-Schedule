package com.xiaomanjun.sleepdownschedule.core.web

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.xiaomanjun.sleepdownschedule.core.ui.interaction.findActivity

internal fun WebView.enableSleepDownDownloads() {
    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        handleSleepDownDownload(url, userAgent, contentDisposition, mimeType)
    }
}

private fun WebView.handleSleepDownDownload(
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
) {
    when {
        url.startsWith("blob:", ignoreCase = true) -> Toast.makeText(context, "请用浏览器下载此文件", Toast.LENGTH_SHORT).show()
        url.startsWith("data:", ignoreCase = true) -> {
            if (context.ensureLegacyDownloadPermission()) {
                context.handleSleepDownDataUrl(url)
            }
        }
        else -> {
            if (context.ensureLegacyDownloadPermission()) {
                context.handleSleepDownWebDownload(url, userAgent, contentDisposition, mimeType, this.url)
            }
        }
    }
}

internal fun shouldRequestLegacyDownloadPermission(
    sdkInt: Int,
    permissionGranted: Boolean
): Boolean = sdkInt < Build.VERSION_CODES.Q && !permissionGranted

private fun Context.ensureLegacyDownloadPermission(): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    if (!shouldRequestLegacyDownloadPermission(Build.VERSION.SDK_INT, granted)) {
        return true
    }
    val activity = findActivity()
    if (activity == null) {
        Toast.makeText(this, "无法申请存储权限，请使用系统浏览器下载", Toast.LENGTH_SHORT).show()
        return false
    }
    ActivityCompat.requestPermissions(
        activity,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        LegacyDownloadPermissionRequestCode
    )
    Toast.makeText(this, "授权存储权限后，请再次点击下载", Toast.LENGTH_SHORT).show()
    return false
}

private const val LegacyDownloadPermissionRequestCode = 60730

private fun Context.handleSleepDownWebDownload(
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
    referer: String? = null
) {
    val uri = runCatching { url.toUri() }.getOrNull()
    if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            Toast.makeText(this, "无法处理下载链接", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val appContext = applicationContext
    Toast.makeText(appContext, "已开始下载", Toast.LENGTH_SHORT).show()
    Thread {
        runCatching {
            appContext.downloadWebFile(
                url = url,
                fallbackContentDisposition = contentDisposition,
                userAgent = userAgent,
                referer = referer,
                mimeType = mimeType
            )
        }.onSuccess {
            appContext.showToastOnMain("已保存到下载目录")
        }.onFailure {
            appContext.showToastOnMain("下载失败：${it.message ?: "未知错误"}")
        }
    }.start()
}

private fun Context.downloadWebFile(
    url: String,
    fallbackContentDisposition: String?,
    userAgent: String?,
    referer: String?,
    mimeType: String?
) {
    val cookies = CookieManager.getInstance()
    val connection = openWebDownloadConnection(url, userAgent, referer, cookies::getCookie) { target, value ->
        cookies.setCookie(target, value)
    }
    try {
        val resolvedMimeType = connection.contentType?.substringBefore(';') ?: mimeType ?: "application/octet-stream"
        val resolvedFileName = resolveWebDownloadFileName(
            originalUrl = url,
            finalUrl = connection.url?.toString(),
            contentDisposition = connection.getHeaderField("Content-Disposition") ?: fallbackContentDisposition,
            mimeType = resolvedMimeType
        )
        connection.inputStream.use { input ->
            writeDownload(resolvedFileName, resolvedMimeType) { output -> input.copyTo(output) }
        }
    } finally {
        connection.disconnect()
    }
}

private fun resolveWebDownloadFileName(
    originalUrl: String,
    finalUrl: String?,
    contentDisposition: String?,
    mimeType: String?
): String {
    val fromDisposition = downloadDispositionFileName(contentDisposition)
    val guessed = URLUtil.guessFileName(finalUrl ?: originalUrl, contentDisposition, mimeType)
    val name = safeDownloadFileName(fromDisposition ?: guessed)
    if (!name.endsWith(".bin", ignoreCase = true) && '.' in name) return name
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType.orEmpty())
        ?.takeIf { it.isNotBlank() }
        ?: when {
            mimeType?.contains("android", ignoreCase = true) == true -> "apk"
            mimeType?.contains("zip", ignoreCase = true) == true -> "zip"
            mimeType?.contains("pdf", ignoreCase = true) == true -> "pdf"
            else -> null
        }
    val base = (if (name.endsWith(".bin", true)) name.dropLast(4) else name).ifBlank { "download" }
    return if (extension != null) "$base.$extension" else name
}

private fun Context.showToastOnMain(message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.handleSleepDownDataUrl(dataUrl: String) {
    val appContext = applicationContext
    Toast.makeText(appContext, "已开始下载", Toast.LENGTH_SHORT).show()
    Thread {
        runCatching {
            val download = parseDataUrlDownload(dataUrl)
            download.input.use { input ->
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(download.mimeType)
                val fileName = if (extension.isNullOrBlank()) "download" else "download.$extension"
                appContext.writeDownload(fileName, download.mimeType) { output -> input.copyTo(output) }
            }
        }.onSuccess {
            appContext.showToastOnMain("已保存到下载目录")
        }.onFailure {
            appContext.showToastOnMain("下载失败：${it.message ?: "未知错误"}")
        }
    }.start()
}

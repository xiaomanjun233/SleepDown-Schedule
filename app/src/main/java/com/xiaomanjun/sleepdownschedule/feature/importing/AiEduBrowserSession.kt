package com.xiaomanjun.sleepdownschedule.feature.importing

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.annotation.MainThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** The original browser keeps ownership even while its one WebView is lent to the AI window. */
internal class EduBrowserWebViewHost(context: Context, val browser: WebView) : FrameLayout(context) {
    init { addView(browser, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
}

internal data class AiEduBrowserBinding(val taskId: String, val session: AiEduBrowserSession)

internal object AiEduBrowserSessions {
    private val sessions = WeakHashMap<WebView, AiEduBrowserSession>()
    private val mutable = MutableStateFlow<AiEduBrowserBinding?>(null)
    val current = mutable.asStateFlow()

    @MainThread fun prepare(browser: WebView): AiEduBrowserSession? {
        sessions[browser]?.takeIf { it.available }?.let { return it }
        val home = browser.parent as? EduBrowserWebViewHost ?: return null
        return AiEduBrowserSession(browser, home).also { sessions[browser] = it }
    }

    // Task restarts may bind on a worker; this only publishes an already prepared capability.
    @Synchronized fun bind(taskId: String, session: AiEduBrowserSession?) {
        if (session != null && session.available) mutable.value = AiEduBrowserBinding(taskId, session)
    }

    @MainThread @Synchronized fun release(browser: WebView) {
        sessions.remove(browser)?.invalidate()
        val binding = mutable.value?.takeIf { it.session.owns(browser) }
        if (binding != null) mutable.compareAndSet(binding, null)
    }
}

/** No reload, cookie transfer or second renderer. A stopped/dismissed AI page returns the view. */
internal class AiEduBrowserSession @MainThread constructor(browser: WebView, home: EduBrowserWebViewHost) {
    private val target = WeakReference(browser)
    private val source = WeakReference(home)
    private var borrower = WeakReference<FrameLayout>(null)
    @Volatile var available = true
        private set
    val activity = MutableStateFlow("等待操作")
    val address = runCatching { java.net.URI(browser.url.orEmpty()).host }.getOrNull().orEmpty()

    fun owns(browser: WebView) = target.get() === browser

    @MainThread fun attach(host: FrameLayout): Boolean {
        val browser = target.get() ?: return false
        val home = source.get() ?: return false
        if (!available) return false
        val previous = borrower.get()
        if (previous != null && previous !== host) return false
        if (browser.parent !== home && browser.parent !== host) return false
        if (browser.parent !== host) {
            (browser.parent as? ViewGroup)?.removeView(browser)
            host.addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        borrower = WeakReference(host)
        return true
    }

    @MainThread fun restore(host: FrameLayout) {
        if (borrower.get() !== host) return
        borrower.clear()
        val browser = target.get() ?: return
        if (browser.parent !== host) return
        host.removeView(browser)
        if (available) source.get()?.addView(browser,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @MainThread internal fun invalidate() {
        available = false
        val browser = target.get()
        borrower.get()?.let { if (browser?.parent === it) it.removeView(browser) }
        borrower.clear()
    }
}

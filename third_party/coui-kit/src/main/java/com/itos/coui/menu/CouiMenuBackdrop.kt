package com.itos.coui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import android.view.View
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop

internal val LocalCouiMenuBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
private val LocalCouiMenuWindow = staticCompositionLocalOf<View?> { null }

/** 当前窗口已提供的采样源；不新建源或持有其消费引用，未提供时为 null。 */
@Composable
fun rememberCouiWindowBackdrop(): CouiBackdrop? = LocalCouiMenuBackdrop.current

/** 内容修饰符挂到所属窗口的内容根节点; Popup 菜单自身不进入采样树。 */
@Composable
fun ProvideCouiMenuBackdrop(content: @Composable (Modifier) -> Unit) {
    ProvideCouiMenuBackdrop(isolateContent = true, content = content)
}

/**
 * isolateContent 为 false 时, 窗口保留绘制指令而不强制整窗离屏。
 * 菜单及材质弹窗只在自己的采样区隔离绘制，并保留所有父窗口源。
 * 此模式要求内容中的 Clear、DstIn 等目标混合在其所属组件内隔离。
 * 优先用于已核对的不透明静态页面根; 透明内容、缩放或分数位移须独立验证栅格化结果。
 * 不确定内容的合成要求时, 使用保留隔离的单参数入口。
 * recordWhenIdle=false 仅用于独立弹窗的整窗源：没有消费者时直接绘制内容，
 * 不额外录制整页；retain 会唤醒源并保留父链，退出结束后恢复直绘。
 */
@Composable
fun ProvideCouiMenuBackdrop(
    isolateContent: Boolean,
    recordWhenIdle: Boolean = true,
    enabled: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    val parent = LocalCouiMenuBackdrop.current
    val parentWindow = LocalCouiMenuWindow.current
    val view = LocalView.current
    val reuse = parent != null && parentWindow?.rootView === view.rootView
    val backdrop = if (enabled && !reuse) {
        rememberCouiBackdrop(parent = parent, diagnosticName = "menu-window").also { source ->
            SideEffect {
                source.setIsolateContent(isolateContent)
                source.setRecordWhenIdle(recordWhenIdle)
            }
        }
    } else null
    CompositionLocalProvider(LocalCouiMenuBackdrop provides (backdrop ?: parent),
        LocalCouiMenuWindow provides (if (backdrop == null) parentWindow else view)) {
        content(if (backdrop == null) Modifier else Modifier.couiBackdrop(backdrop))
    }
}

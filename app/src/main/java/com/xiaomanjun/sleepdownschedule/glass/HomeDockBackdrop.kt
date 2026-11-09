package com.xiaomanjun.sleepdownschedule.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.backdrop.couiBackdrop
import com.itos.coui.backdrop.rememberCouiBackdrop

/**
 * COUI Dock 的整页采样入口。源挂在完整页面内容上，Dock 必须作为它的后绘制同级
 * 消费者；不要把这些节点放到同一条采样子树里，否则 Dock 会记录到自身。
 *
 * 源节点位于首页既有 `screenGraphicsLayer` 录制范围内，因此整页快照、冻结回放和
 * 跨 Activity 转场仍读取同一页面画面。未启用 COUI Dock 时不要挂载返回的 Modifier。
 */
@Composable
internal fun rememberDockPageBackdrop(): CouiBackdrop =
    // The dock isolates its own patches. Page transitions and the frozen Home host already
    // own their separate layers, so this sampling source can replay the same draw commands.
    rememberCouiBackdrop(diagnosticName = "home-dock-page", isolateContent = false)

internal fun Modifier.dockPageSource(backdrop: CouiBackdrop): Modifier =
    couiBackdrop(backdrop)

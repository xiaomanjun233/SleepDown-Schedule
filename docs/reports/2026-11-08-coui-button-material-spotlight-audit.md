# 按钮渐变、课程卡片与菜单跟手光核查

接续 `95ab3c00`。用户提供多课表管理截图，指出按钮的深色渐变，并追问课程卡片是否完成替换，追加菜单应有跟手光。本轮不安装、不推送、不发布，保留版本及既有尺寸。

没有再次同步远端；vendored 基线仍是已固定的 GitHub main `520478b9305780b078bed1096ce68f727c594f14`，不把它描述为当前最新上游。

## 截图对应的实际链路

- 顶部应用：`PickerHeaderButton → LiquidButton → CouiCapsuleButtonRenderer`。此前有背景源的所有主按钮都会移除 Kit 填色，改用暗色 alpha .50 的 FAB 扩展材质；这会把普通主按钮压成深色半透明表面，不能说是 Kit 默认按钮外观。
- 底部课表设置：传入旧个性化 `#4DFFFFFF → #1AFFFFFF` 纵向洗光，并打开 `nativeLightBackdrop`，从而跳过 Kit，继续旧原生按钮。截图中的显著纵向渐变有这项明确来源，不能全部解释为壁纸透色。
- 管理页 `managerBackdrop` 原先挂在空 Box 上，且黑底位于 producer 外层；按钮消费的默认 drawContent 没有录入黑底，后绘制的投光也不在该源中。
- 分享与新建：普通 Kit 兼容按钮，使用强调色半透明材质；它们不是带投光的旧按钮。
- 预览内课程卡片：当前帧来自已有课表快照；实时课程仍由 `ColorOsCourseCard` 或液态／高斯分支绘制。没有 Kit 课程卡片 renderer，不能声称迁移完成。

## 修正

1. 普通主按钮恢复 `CouiButtonDefaults.primaryColors` 的填色；兼容请求新增显式 `translucentAccent`，只有浮动有色操作选择半透明材质。管理页分享、新建及原 Dock 加号保持半透明模糊，中性按钮仍采样。
2. Kit 路线移除管理页课表设置的旧纵向洗光，让它使用 Kit 中性胶囊。旧系统原生回退保留原装配。
3. producer 移到黑底外侧，投光成为该 underlay 的子节点；预览、按钮和文字仍为后绘制的同级消费者，不录入自身。原生投光使用 TextureView，纳入层级不等于已证明真机硬件录制结果，需真机确认投光是否进入 Kit 采样。

没有修改 Kit 的轮廓、内晕、主题强调色或跟手光强度，不能据本次代码修正断言所有渐变或掉帧已经消失。

## 菜单跟手光

shortcut 和三点菜单都使用 Kit `Menu`。有效 backdrop 下，`couiMenuMaterial` 同时安装输入和光效绘制；输入在 Initial pass 观察，DOWN 点亮、MOVE 更新位置、UP/CANCEL 熄灭，不消费列表选择。光绘制在背景混色后、菜单正文前。当前保留外层离屏隔离，没有恢复重复纹理层。

与保存的系统 RoundFrameLayout 参考一致，使用 `TranslucentLarge1` 预设：浅色 .6、深色 .1、800物理像素半径、Overlay。此预设不是弹出时自动播放的闪光；菜单内部按压／滑动触发跟手光。本轮没有证据说明旧触点跨 Popup 窗口的交接已实现。

补充使用 Menu 现有 `onSpotlightDiagnostic` 的触摸检查，覆盖两种主题的点亮、绘制回调、滑移后 CANCEL 及点击抬起。Robolectric 可核对事件和绘制请求，不能证明 RuntimeShader 在真实 GPU 上的可见亮度。

## 验证与交付

`compileGithubReleaseKotlin :app:testGithubDebugUnitTest --tests '*GlowSystemMenusTest'` 通过，5m33s；6项、0失败／错误／跳过。包括 Menu 两主题跟手光触摸／绘制请求及现有宿主菜单立即提交、取消和布局检查。日志 `tmp/coui-button-spotlight-tests-20261108.log`。最初误用了未启用的 Release 测试任务名，已改用现有 Debug 单元测试变体，未改变构建配置。

签名打包结果待回填。用户暂时不能连接手机，本轮没有真机视觉或帧耗时验收。

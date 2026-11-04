# 原生卡片投光与桌面分页条

## 行为

多课表浮层和组件设置页使用系统 Wallpapers 的 `WallpaperShadowView`、COE 与 `shadow_card.coz`，不内嵌厂商 APK、SO 或素材。650ms 原生展开与 1000ms 透明度独立驱动；光覆盖卡片圆角，下方按钮保留颜色、原厂背景采样和独立投影。组件光区按实际宽度收束，深度取组件高度的 0.65，上限 112dp。

有限分页进度通过公共入口复用桌面 `OplusPageIndicator` 的绘制规格、原生触摸动画和 COUI 模糊/描边/跟手光，支持点击、连续拖动、吸附、取消恢复、RTL 和无障碍选页。整类依赖 Launcher 的业务环境，因此没有直接实例化整个桌面指示器。

桌面 17.3.11 的 `PageIndicatorTouchHelper` 只在跨页时触发 `VibrationUtils`：线性马达 WaveformEffect type 0，普通马达路径为 65ms。宿主保留相同时机及效果，遵循系统触感开关；同页移动、程序滚动和取消不额外震动。

## 快照与稳定性

课表仍沿用原 GraphicsLayer 截图、内存复用、v4 持久缓存及原缺图行为，不改数据库、缓存版本、快照方向或保存流程。软件 ARGB_8888 转换仅发生在 COE 纹理上传入口，Surface 首帧前不提交纹理，防止原生 `Material::setTexture` 崩溃；退出后释放引擎。

## 验证

独立分支基于最新 `origin/main`，没有包含其他 AI 导入、分享协议、首页顶栏和助手卡片任务。光效位图/Surface/生命周期与分页手势/震动去重定向回归 11 项通过，完整签名 GitHub Release 首轮 8m26s 成功；补齐原生震动调用失败的异常边界后，最终 Release 4m06s 成功。R8、资源压缩和 lintVital 均保留。设备未连接，系统实际 GPU 输出及触感手感未验收。

# 横屏凝光导航的反馈范围与亮度修订

本轮继续草稿 PR #78，运行时代码提交为 `78f1aa94`。用户在安装上一包后反馈柔性反馈、发光被限制在内框，并补充亮度过高。未合并 main 或发布新版本。

## 截图与本地实现证据

- 读取 PLJ110（Android 17）于 2026-10-19 13:09:58 保存的最新截图：横屏设置页顶部导航的白色光斑在内轨右侧圆角截断，设置文字被强光覆盖。截图只保留在本机忽略目录，没有提交或上传。
- 原导航由外层 48dp 玻璃和内缩的 40dp Segment 分开绘制。COUI 的 `mTouchAnimator` 只变形内轨，`drawSpotLight` 又按内轨的 `mBackgroundPath` 裁剪；原 Segment 跟手光还使用了公共 2 倍强度。
- 旧背景 delegate 返回透明 Paint，但本地完整 `drawButtonBackground` / `drawSmoothRoundRect` 实现仍调用 `canvas.clipPath`，裁剪会延续到同次 `onDraw` 的选中条绘制。隐藏颜色不能消除这层几何裁剪。

## 修订

- 增加 `HomeNavigation` 材质角色，将完整胶囊、标签和左侧按钮放入同一公共材质宿主，复用现有原生 deformation / rubber collector。裁剪只保留完整胶囊轮廓，明暗变化保留外壳 View 与动画绑定。
- 内轨关闭自身容器形变和跟手光，继续保留原生滑选、触觉、抬手提交及指示器弹簧。背景 delegate 返回含 null 的非空 Paint 数组，让 COUI 跳过背景绘制及其裁剪；空数组会触发默认背景，不能替代。
- 外壳采用与 Segment 对应的 `TYPE_CAPSULE_2` 光效，强度设为原生预设的 0.75 倍，并在标签及选中条下面绘制。左侧按钮使用共同光效，不额外叠加局部光；其他表面强度不变。
- 保留独立的左侧按钮点击区域、导航输入锁和普通分段控件的原有行为。没有数据或协议变更。

## 验证与交付

- `78f1aa94` 的完整 `assembleGithubRelease --console=plain --no-parallel --max-workers=2` 通过，9m13s，159 个任务中 21 个执行、138 个复用，保留 R8、资源压缩、lintVital 和正式签名。构建日志仅保留在忽略目录 `tmp/build-20261019-navigation-shell-feedback.log`。
- APK 副本：`tmp/review-apks/SleepDown-1.2.7-navigation-full-capsule-20261019.apk`，7,574,871 字节，SHA-256 `41f6768d737f9845bc5ce8e987e5f3768f7b139ccade6a795f7a5c4351d79eed`，与标准输出 APK 摘要一致。应用标识为 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.7` / 35。
- `apksigner verify --verbose --print-certs` 通过，v2 签名有效，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`，与既有正式签名一致。
- 经项目 ADB 5038 通道向 USB 设备 PLJ110（Android 17）执行 `install -r`，返回 Success；系统核对为 `1.2.7` / 35，更新时间 2026-10-19 13:24:58。保留应用数据，未自动启动或操作应用。
- 未追加单元、自动 UI 或性能测试。编译与安装成功不代表交互验收通过，亮度与拖动体验仍需用户实机验收。

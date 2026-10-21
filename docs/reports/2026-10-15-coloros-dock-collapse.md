# 凝光底栏重建后的加号收起

## 现象和范围

用户手机相册中 2026-10-15 15:13 的截图显示：凝光模式、并列导航、设置页中，蓝色加号仍覆盖底栏的设置项。设备为 OPPO PLJ110 / Android 17，安装版本名仍为 `1.2.7_beta5`。本次只修凝光底栏初始化，不改变液态玻璃、原生融合动画或主题配色规则。

## 原因

首页为底栏提供基于壁纸的 `HomeMaterialStyle`；设置页使用应用主题。如果两者明暗不同，`rememberNativeContext` 的 `dark` 参数变化会创建新 Context，并重新创建原生底栏和 FAB。`7ca6c90f` 为数值等控件补上明暗变化时重建，也改变了原生底栏的生命周期；底栏本来已有 `updateTheme` 和 `refreshAppearance` 原地刷新能力。重建还会丢掉绑定的融合动画状态。

`ColorOsParallelHomeDock` 创建原生控件时只为首页调用 `showExpansionView(false)`；如果当前在设置页，只将 Kotlin 的 `expanded` 初始化为 false，没有调用原生 `hideExpansionView(false)`。下一次更新看到目标状态与缓存相同便直接返回，原生 FAB 仍可见。

单纯更改强调色不会更换原生控件的 remember key，继续走 `updateAccent`。因此触发重建的关键是明暗主题差异，而不是任意颜色数值改变。

## 修改和验证

`rememberNativeContext` 增加显式的 `retainOnThemeChange` 策略，默认继续重建控件。普通底栏与并列底栏使用保留策略，并沿用已有的 `updateTheme` 和 `refreshAppearance`；开关、滑条、分段控件等保持原来的明暗重建修复。真实配置变化或宿主变化仍可创建新控件。

创建并列原生底栏时按当前页面同时覆盖展开和收起两条初始化路径，均不播放初始动画。这样也覆盖直接恢复设置页，或者停留设置页时启用凝光的情况。后续切页继续使用原来的原生动画及状态判断。

完整 `assembleGithubRelease` 构建成功，包含 Kotlin、R8、资源压缩、lintVital 和签名。APK 为 7,525,159 字节，SHA-256 为 `ED75F9CC565975B871DF644BABC0E6BE716F1603EFFF0017D4F2DAC67F15EC24`；包名 `com.xiaomanjun.sleepdownschedule`，版本仍为 `1.2.7_beta5` / `34`。签名校验通过，证书 SHA-256 为 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。

通过项目 SDK ADB 的 5038 通道覆盖安装到上述 OPPO 手机，返回 `Success`，回读更新时间为 2026-10-15 15:33:20。没有启动或操作应用；尚未对修复后的原生显示进行实机验收，不以静态路径分析或编译代替显示验证。由用户检查首页 → 设置 → 首页、停留设置时明暗切换，以及设置页重建后的加号状态。没有发布 beta6。

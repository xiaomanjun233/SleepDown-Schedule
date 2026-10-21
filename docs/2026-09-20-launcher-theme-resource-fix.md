# Beta7 桌面图标深浅跟随修复

## 根因与复现

Beta7 的资源清理把 `ic_launcher` 和 `ic_launcher_kanban` 从图片资源改为 values / values-night 中的资源引用。虽然 APK 的原图哈希和资源表均正确，Android 在解析 Manifest 的 `android:icon` 时会通过 `TypedArray.getResourceId()` 取得已经解析到目标图片的 ID。因此 PackageManager 记录的是固定浅色图标，桌面随后加载该 ID 时无法再选择夜间版本。

这属于本轮清理引入的回归，单纯核对 APK 资源表和图片哈希没有覆盖安装解析这一步。

在只读运行的 Pixel_10_Pro 模拟器（Android 16，API 36）中，以 `PackageManager.getPackageArchiveInfo()` 解析已发布 APK，再用其实际登记的图标 ID 分别按白天和夜间配置绘制：

| APK | 简约跟随 | 看板娘跟随 | 两套固定深浅图标 |
| --- | --- | --- | --- |
| Beta6 | 正常切换 | 正常切换 | 正常 |
| 原版 Beta7 | 固定浅色 | 固定浅色 | 正常 |
| 修复包 | 正常切换 | 正常切换 | 正常 |

原版 Beta7 的解析结果为 `LauncherFollow → mipmap/ic_launcher_light`、`LauncherKanbanFollow → mipmap/ic_launcher_kanban_light`。修复后保留 `mipmap/ic_launcher` 和 `mipmap/ic_launcher_kanban` 的动态入口，昼夜像素分别与已有固定深浅图标一致；应用级图标同样恢复跟随。

## 修复

- 使用 `mipmap/` 与 `mipmap-night/` 下的轻量 bitmap XML，分别引用原有固定深浅图。XML 自身具有独立资源 ID，解析 Manifest 时不会继续展开其内部图片引用。
- 四套原图与五档密度继续共用，实时活动的固定 drawable 别名保持原方式。未恢复重复图片。
- 图标生成脚本说明更新为当前引用方式，应用内 Beta7 日志与发布说明同步修正。
- [LauncherIconProbe.java](../scripts/android/LauncherIconProbe.java) 保留为 Android 端回归检查：读取真实 APK 包信息，绘制并比较两套图标的固定浅色、固定深色、跟随模式与应用级图标。原版 Beta7 必须失败，Beta6 和修复包必须通过。

检查通过 `javac` 和 Android SDK 的 `d8` 编译该 Java 文件，再将 dex 与待测 APK 推送到专用模拟器的 `/data/local/tmp/` 后运行：

```sh
CLASSPATH=/data/local/tmp/sleepdown-icon-probe.dex app_process /system/bin LauncherIconProbe /data/local/tmp/sleepdown-beta7-icon-fixed.apk
```

测试解析 APK，不安装或启动目标应用。它验证 Android 包解析与图像绘制，不能代替各厂商桌面缓存刷新行为的实机验收。

## 验证

- 首个修复包 `assembleGithubRelease` 成功，耗时 2 分 41 秒，保留 R8、资源压缩、lintVital 与正式签名；APK v2 签名验证通过。
- Android 端同一回归检查：Beta6 通过，原版 Beta7 在“跟随模式未使用深色图”断言失败，修复包通过。
- 首个修复包 6,328,542 字节，比原版 Beta7 增加 1,136 字节，重复资源仍为 0；仍保留此前的包体精简效果。

## 最终重发产物

- 源码提交：`ac86c024684a271758a10618e9ab16ebe73b939e`。
- 更新应用内日志后的最终 `assembleGithubRelease` 成功，耗时 3 分 25 秒，保留 R8、资源压缩、lintVital 与正式签名；APK v2 签名验证通过。
- 最终 APK 再次通过上述 Android 16 包解析与绘制检查，两套固定深浅图标、两套跟随图标及应用级图标均符合预期。
- 包名 `com.xiaomanjun.sleepdownschedule`，版本 `1.2.6_beta7`，versionCode `33`。
- APK 6,328,542 字节；较首次发布增加 1,136 字节，重复资源仍为 0。
- SHA-256：`38cc085b28a2751f7bb44afee4b8440c5f3d4c66b1134725749399ede40ba9cb`。
- 首次发布产物保留在本地临时目录，历史发布报告保持原记录。本次沿用 Beta7 版本，已安装首次发布包的用户需重新下载覆盖安装。

## 两端发布校验

- [GitHub Beta7](https://github.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.6_beta7) 与 [Gitee Beta7](https://gitee.com/xiaomanjun233/SleepDown-Schedule/releases/tag/v1.2.6_beta7) 的 APK 附件已替换为上述最终产物，发布说明与应用内日志同步。
- 两端均保持预发布状态，发布目标与 Beta7 标签均指向源码提交 `ac86c024684a271758a10618e9ab16ebe73b939e`；未合并主分支。GitHub 的最新正式版仍为 `v1.2.5`。
- GitHub 附件 ID 为 `576930919`，服务端 digest、附件大小和公开下载校验一致；Gitee 公开下载的 SHA-256 与大小同样符合上述产物。
- Gitee 首次说明更新因缺少其要求的完整字段返回 HTTP 400，补齐字段后更新成功。附件上传后下载校验发生一次连接中断；先只读确认远端状态，再单独下载校验通过，未重复删除或上传附件。
- 只读模拟器已关闭。未安装到用户实机，也未启动或读取用户应用数据。

# 首页编辑与组件后续调整（2026-09-10）

本次是 [PR #31](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/31) 的用户追加范围，同时处理 [Issue #30](https://github.com/xiaomanjun233/SleepDown-Schedule/issues/30)。以下记录是本次实现与验证证据，不作为新的全局限制。

## 改动与原因

- 长按菜单按课程卡宽度加少量余量布局，缩小卡片起伏与菜单回弹；外层加大圆角，内部两项改为同轴胶囊。保留左、中、右卡片各自的展开方向、长按直接拖动与编辑模式交接。
- 复制后仍须重选时间。没有原卡片返回锚点的复制编辑器改用公共居中弹窗：中心缩放、非线性进退场与背景模糊，退出结束后才卸载；内部时间选择器采样编辑器自己的下层内容。
- 个性化表单增加逐行错开的透明度、缩放和上移动画，保留原有外层转场与滑条保存流程。
- 周视图编辑控件的上下绘制余量覆盖角标阴影与弹入超调；右下角缩放角标使用与左上删除按钮相同的玻璃胶囊材质，保留 44dp 操作区域。
- 完全位于节次表之外或课间的精确时间课程原先映射为零高度，现在放入周视图的“其他时间”区域，显示起止时间并复用现有编辑、复制与删除入口。原始课程时间和数据库不变。
- 提醒原先以同一课程所有节次的首尾作为整个时段；现在按连续节次分组，并为每段分配独立闹钟标识。通知边界到达时从当前课表重新选择正在上课的课程，避免旧课程的结束或重试事件覆盖中间课程。精确时间变化也会更新调度签名。
- 组件设置增加“添加到桌面”，按当前选中的五种组件之一调用系统固定组件请求；实际添加仍由桌面确认。不支持该 API 的桌面显示手动添加提示。
- 课程玻璃参照 Nexio 的共享背景与消费缓冲同为 0.48 的路径：共享模糊 → 按采样坐标直接取背景 → 单卡折射 → SleepDown 着色、轮廓光与阴影。不在基础模糊前叠加 vibrancy，单卡 lens 使用 `depthEffect=false`。保留变换、固定几何和导出场景原有通用路径。
- 精简 `AGENTS.md`、增加文档索引，区分长期边界和历史实验记录。`.gitattributes` 将拾光资源和 `third-party/` 标记为 vendored；保留文件、许可和导入能力。语言统计更新由 GitHub 重算，项目自有 JavaScript 仍可能计入。

主要实现位于 `feature/home/overlay/`、`feature/home/week/WeekScheduleUi.kt`、`app/ui/ScheduleAppUi.kt`、`domain/schedule/ScheduleCalendar.kt`、`feature/reminder/`、`CourseAlarmReceiver.kt`、`feature/widget/WidgetCustomizationUi.kt` 与 `glass/ui/GlassUi.kt`。第三方改动同步记录到 `patches/kyant-backdrop-2.0.0-sleepdown.patch` 和模块 README。

没有修改数据库 schema、导入协议、备份格式、稳定组件名称或私有后端。共享采样实现也被其他玻璃消费者使用，但新增直接采样分支仅在共享层及消费层比例匹配且无额外变换、固定几何或导出时生效。

## 验证

- 定向 JVM 测试：34 项通过，0 失败、0 跳过。覆盖长按与复制草稿、编辑器分组和时间选择、精确时间定位、非连续节次提醒及 Live Update 内容。
- Kyant Android host 测试：5 项通过，0 失败、0 跳过。覆盖录制缓存生命周期、采样缩放和几何恢复。
- 场景输入：课程 A 选择第 1、2、5、6 节，课程 B 选择中间第 3、4 节。结果：A 拆为两个提醒时段；B 上课期间取得通知；两段之间无活动课程时不延续 A；下午 A 以第二段时间重新计算剩余时长。
- 精确时间用例覆盖教学时间前、教学时间后、完整课间、跨边界、单分钟和空节次配置。
- `git diff --check` 通过。补丁逆向检查 `git apply --reverse --check -p2 --directory=third-party/kyant-backdrop patches/kyant-backdrop-2.0.0-sleepdown.patch` 通过。
- `assembleGithubRelease` 成功（6 分 22 秒），包含 Kotlin 编译、R8、资源压缩、lintVital、打包和正式签名。`apksigner verify --verbose` 通过，v2 签名有效。
- 已通过无线 ADB 覆盖安装 `1.2.5_beta3`（versionCode 31），`adb install -r` 返回 `Success`；设备更新时间为 16:36:29，安装包哈希与本地产物一致，没有自动启动应用。APK SHA-256：`8c7b852870997bb46f5d7badc3e622252517e3408cc532bfefae982a73306dbe`。
- 实机目标：OPPO Find X9（PLJ110），Android 17；开始修改前已建立无线 ADB 连接。
- 动画逐帧、明暗壁纸、不同字体比例、桌面确认交互和 GPU 性能验证未执行。本轮自动测试不能替代视觉验收，也不能证明添加 SleepDown 装饰后与 Nexio 像素一致或帧时间相同。

工程仅有 Debug 单元测试变体；部分旧测试仍引用迁移前的符号，本次不声明全套测试通过。定向测试使用下列临时 init 脚本筛选源码，未将临时文件纳入提交：

```groovy
gradle.beforeProject { project ->
    project.pluginManager.withPlugin('com.android.application') {
        def testSources = project.android.sourceSets.getByName('test')
        def includes = [
            '**/feature/home/overlay/CourseShortcutTest.kt',
            '**/feature/course/editor/CourseEditor*Test.kt',
            '**/domain/schedule/CustomCourseTimeTest.kt',
            '**/feature/reminder/CourseSessionReminderTest.kt',
            '**/feature/reminder/LiveUpdatePayloadTest.kt'
        ]
        [testSources.java, testSources.kotlin].each {
            it.setSrcDirs([project.file('src/test/java')])
            it.setIncludes(includes)
        }
    }
}
```

```powershell
$env:JAVA_HOME='D:\Android studio\JDK'
$env:GRADLE_USER_HOME='C:\Users\23085\.gradle'
.\gradlew.bat -I tmp/home-followup-tests.init.gradle testGithubDebugUnitTest :kyant-backdrop:testAndroidHostTest --console=plain --no-parallel --max-workers=1
.\gradlew.bat assembleGithubRelease --console=plain --no-parallel --max-workers=1
```

Nexio 参考固定于提交 `2971759ed3bb7b16ef13e639fba5dbf2a6a9cb2d`；具体源码和许可说明见 [第三方模块记录](../third-party/kyant-backdrop/README.md)。组件固定接口参考 [Android 官方文档](https://developer.android.com/develop/ui/views/appwidgets/discoverability)。

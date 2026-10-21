# 课表状态与共享转场边界续作

用户在[上一轮](2026-10-20-architecture-completion-and-ai-fixes.md)交付后要求“继续重构”。本轮开始时核对 main 仍为 `9b6acbb2`，[PR #79](https://github.com/xiaomanjun233/SleepDown-Schedule/pull/79) 开放、无评审线程，远端与本地均为 `d2aeddd7`。继续使用独立工作树 `tmp/architecture-refactor/` 和 `codex/architecture-boundaries`，不合并、不改版本、不发布。主目录已有独立学校适配任务及未提交交接，未切换或覆盖其内容。

## 功能内课表状态

`636d07fc` 解除课表管理、调休 Activity 和自动刷新登录 Activity 对 `app/state/ScheduleViewModel` 的依赖。此前三处仅使用课表查询和少量命令，却同时创建首页个性化队列、Agent 设置执行器和提示状态。

现在 `feature/schedule/ScheduleScreenViewModel` 只持有 Activity 所需的当前/全部课表流，保持原 `WhileSubscribed(5000)`、初始状态、默认数据准备和首次刷新。三个入口仍通过原 Activity 的 ViewModelStore 获取实例；旋转继续保留相同生命周期，未将写入任务改成 Composable 的协程。

原新建、切换、改名、删除和调休保存集中到 `ScheduleActions`，首页总 ViewModel 委托同一实现。命令接受所属作用域和刷新回调，没有新增作用域、接口框架或全局实例。保留：创建后可选激活、激活后才回调、删除后的壁纸清理、调休的原值比较与错误回调，以及取消传播。通知/Widget 刷新的共同实现放进现有 `ScheduleRefreshCoordinator.kt`；保存退出的立即刷新仍使用注入的 Application 作用域，避免 Activity 结束截断刷新。

新回归使用真实内存 Room，覆盖创建/激活与回调顺序、不激活的新课表、删除当前课表后的上下文、只修改调休字段、过期草稿不覆盖新值、不再存活的所属作用域不能写入。未改变 Activity FQCN、Intent extras、Repository 事务或数据模型。

## 转场与首页的依赖方向

跨 Activity 的 Legacy renderer 原来为了轨迹、内容交接和圆角读取整个首页几何文件与自适应布局。此次将已有共享计算归入 `transition/legacy/LegacySharedObjectGeometry.kt` 与 `LegacyMenuDestinationGeometry.kt`，首页和跨 Activity renderer 共用同一计算。首页专属的 LiquidMorphSpec 适配位于 `feature/home/overlay/HomeLiquidMorphSpecs.kt`，只负责把首页几何接入既有通用材质协议。

31 项迁移声明对照原源码，除必要的可见性调整外计算内容一致；两个首页文件中留下的 45 项声明也已对照一致。没有复制轨迹实现，没有调整缓动、时长、阶段阈值、透明度、圆角、光照、Modifier 或缓存交接。旧名称继续保留，未为目录整理批量改名。

跨 Activity renderer 实际只需自适应弧高。`core/ui/interaction/DeviceScreenGeometry.kt` 提供窗口尺寸规则：首页与转场共同使用原手机 48dp、大屏竖屏 44dp、大屏横屏 40dp。保留原 600dp 边界、尺寸取整与最小值，不引入设备特例；安全区和字体比例不参与原弧高计算。窗口边界回归同时核对历史预期和首页计算结果。

## 检查防退化

`fbb8a6e3` 在原扫描基础上解析根包 FQN `typealias` 的目标与别名链，并识别显式导入、全限定使用和根包通配导入中的实际别名使用。新增四项检查器回归，防止通过根兼容别名藏起 feature 依赖；注释、字符串与未使用的通配别名不会产生边，循环别名不会无限递归。

这仍是源码声明检查，不代替编译器：未解析根转发函数、反射、字符串模板中的表达式、一般未限定调用或外部模块内部引用。通配功能包引用仍保守建边，不能把统计称为完整类型调用图。

本轮五项已登记反向文件依赖全部移除，当前 **0 项**；声明循环边 **65→44**。扫描 561 个生产 Kotlin/Java 文件，基线只删除已解决条目，没有新增豁免，也没有通过改变检查口径排除功能文件。剩余循环主要在 feature 间的真实交接/设置装配及 core/glass，后续应围绕具体职责调整，不机械拆分所有长文件。

## 验证与边界

- `636d07fc`：Release Kotlin 编译及 7 类 92 项定向回归通过，0 失败/错误/跳过，4m56s；包含新增命令、调休、公共作息仓库、AI 设置、自动刷新时间保留和通知调度。
- `d0b3598f` 转场迁移：Release Kotlin 编译及 12 类 131 项通过，0 失败/错误/跳过，5m06s；覆盖窗口适配、首页轨迹与个性化、玻璃协议/绘制、Morph 模糊、转场路线/载荷/状态机。本轮最终运行时代码为该提交；后续文档更新不改变运行时。
- 13 项架构检查器回归通过；共享及保留几何声明已对照。

构建沿用 `miuix-local`、单 worker 和临时 4 GiB JVM 堆。转场初次编译发现两项数学导入和一处 remember 依赖键遗漏，补齐后重新验证，失败运行不计为通过。最终 head 的完整云端检查链接与结果保存在 #79；不使用上一轮 `d2aeddd7` 的通过结果代替新提交。

本轮没有 Manifest、资源、依赖、Room schema/迁移、备份协议、通知 wire 值或版本变更。#78 的玻璃/动效没有重写。未构建签名发布包、安装、启动或实机验收；单测不能代表真实设备的旋转、返回动画和 OEM 后台行为已经验收。

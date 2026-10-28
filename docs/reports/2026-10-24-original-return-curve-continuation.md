# 保留原关闭曲线的中途续接

接续 `codex/sharedviewkit-ai-history` / `f0d0f8ba` 和全部已有未提交改动。用户验证上一包 `ee638f74…` 后明确：打开曲线已正确，关闭仍不对；保持原曲线，只在打断时从当前节点继续原来的返回动画。沿用只传 APK 到本人私有 Drive 测试目录的授权，不安装、提交或推送。

## 根因与实现

- 上一候选只恢复原始时钟时长，课程 `legacyCourseEditorMorphSpec` 和个性化 `legacyPersonalizationMorphSpec` 在预载表单时仍强制使用打开几何，加号菜单也固定 `closing=false`。因此完整关闭等同倒放打开，原独立返回的位移、提前收缩和压缩/恢复均被替代。本轮按实际方向恢复上述三条路线原有纯帧计算；打开函数和参数未变。
- 中途不能直接切换到新方向的同进度帧。新增 `MorphCurveContinuity`，每个存续会话各自绑定纯帧采样器；阶段从 Opening/Closing 反向时，在当前进度求旧已衔接帧及切线，与新方向原帧作差，再将残差接入该原曲线的剩余段。端点残差及导数归零，快速再次反向捕获当前残差，不积累另一条动画。原位热区与多会话所有权沿用上包。
- 正常从端点打开或关闭不产生残差，使用原始每帧结果。只有方向改变时采样有限差分；正常帧无需额外数组，活动残差逐通道求值。不添加表单、位图、模糊采样、Offscreen 或后台计时器。位置、尺寸、圆角、叠化、模糊和 taper 一起衔接。
- 返回原始时钟只在打断节点局部处理速度：最多 60ms 制动，最多 60ms 接回原路线的恒定原始速率，剩余返回按原速率前进，不再用整段 Hermite 代替回程。已确认打开和有 easing 的背景续接不改。系统动画倍率、独立时钟和资源释放沿用现有实现。

## 验证与交付

最终源码 8 类 103 项定向回归通过（0 失败、错误和跳过）：`InterruptibleMorphTest` 9、`MorphCurveContinuityTest` 5、`RetainedMorphLayerTest` 2、`CourseEditorSessionsTest` 5、`HomePersonalizationGeometryTest` 5、`HomeAnchoredMorphGeometryTest` 45、`HomeMorphPreloadTest` 4、`GlassFrameworkTest` 28。

新增验证覆盖三路线完整开合保持原帧、返回区别于倒放打开、重复反向的当前帧与切线衔接、近源站位反向不外推失控、独立卡片互不影响、返回局部衔接后恢复原时钟速度及边界限制。真实 Compose 宿主连续反向 6 次，表单仅挂载一次；保留之前原站位连续点 8 次的真实触摸回归。Preparing 零进度取消直接 Idle，待下一次 Composition 完成卸载，放行旧 gate 不能重开。计划采样的 Float 终点比较放在减去制动时间前，避免浮点抵消多拖一帧。

上述 103 项回归与完整 `assembleGithubRelease -Psleepdown.sharedViewKitTest=true` 同次调用 6m51s 成功，192 项：24 执行、168 复用；包含 R8、资源压缩、lintVital 和正式签名。66 个变动生产文件的构建前后哈希一致。两个生成开关均为 true；实际 APK 的 `b3.p` 金标能力查询路径及本地门控、此次新增帧残差实现 `nb3` 均已反汇编核对，正式 v2 证书匹配既有发布身份。

新包为主目录 `tmp/review-apks/SleepDown-original-return-curve-gold-20261024.apk`，7,624,231 字节，SHA-256 `798ccdb27834d464758bbbe0054c4d59be2f6996f2299ba263ceb4e5fc9b5972`。身份仍为 `com.xiaomanjun.sleepdownschedule`、`1.2.7.1` / 35，证书 SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。已作为新文件上传原本人私有“SleepDown 测试包”，metadata 与目录回读确认文件名、字节数、父目录、私有和可下载，没有覆盖旧包。准确链接见主目录 `tmp/review-apks/remote-delivery-20261023.json`。

没有安装、提交、推送、PR、合并或发布。用户只确认过上一候选的打开曲线；本包关闭观感与手机 GPU 帧率尚未实测，数学/Compose 宿主验证不能替代手机验收。

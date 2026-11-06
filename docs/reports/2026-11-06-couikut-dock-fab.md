# COUI 底栏 FAB 迁移与深色跟手光

日期：2026-11-06  
分支：`codex/couikit-dock-fab`  
父提交：`5ad124a9`（COUI Dock 迁移与默认凝光）

## 1. 范围

- 把首页底栏的加号从反射加载的 `COUIFloatingButton` / Compose 后备实现迁到 `third_party/coui-kit` 的 COUI FAB 材质、触摸和跟手光。
- 胶囊与 FAB 使用 `CouiFloatingRenderer.pair()` 的双主体链路，恢复重叠时的融合连接。
- 保留现有菜单、锚点、返回位置和设置页收放；加号仍由宿主控制，不在库内提交导航。
- 深色模式跟手光改为浅/深分别配置，降低菜单和 FAB 的深色强度。

## 2. 系统 FAB 参数

来源：

- `E:\COUI\work\src7\sources\com\coui\appcompat\floatingactionbutton\COUIFloatingButton.java`
- `E:\COUI\work\src7\sources\com\coui\appcompat\floatingactionbutton\FloatingButtonWrapper.java`
- `E:\COUI\work\src1\sources\com\coui\appcompat\spotlight\COUISpotLightEffect.java`

| 项目 | 系统值 |
| --- | --- |
| 默认 FAB 尺寸 | 56dp（`DEFAULT_COUI_FLOATING_BUTTON_SIZE`） |
| FAB 跟手光类型 | `TYPE_OPAQUE_SMALL_1` |
| 浅色跟随光终点强度 | 0.6 |
| 深色跟随光终点强度 | 0.5 |
| 旧应用覆盖 | 跟手光范围乘 2；FAB 颜色使用墙纸强调色 |
| 旧应用实际浅色/深色范围 | 1.2 / 1.0 |

旧 `createNativeFab` 的 `bindFollowLightIntensity(root)` 默认乘 2；新实现保留浅色 2×（1.2），深色不再强制 2×，回到 COUI 预设 0.5。

## 3. Kit 实现

新增 `third_party/coui-kit/src/main/java/com/itos/coui/navigation/CouiDockBar.kt`：

- `CouiDockPlusStyle`：COUI FAB 的材质、投影、圆形形状、图标、图标描述、浅/深跟随光强度和跟手光颜色。
- `CouiDockBar`：菜单胶囊 + FAB 的双主体入口。
  - 要求 `CouiFloatingRenderer.pair()` 和 API 34。
  - 两个表面为菜单 `material.index=1`、FAB `material.index=2`，扩展 4dp。
  - 菜单和 FAB 分别拥有内容层、触摸/按压和 spotlight；FAB 材质由 `CouiFabDefaults.material/shadow` 生成，使用墙纸强调色。
  - `plusSlot` 由宿主动画控制：主页 68dp（12dp 间距 + 56dp FAB），设置页 0dp；FAB 随 slot 左移进入胶囊，pair shader 在重叠时融合。
  - `plusAlpha` 控制菜单打开/设置页淡出；FAB 节点和直接跟手光一起淡出。
  - 加号点击回调携带根坐标 bounds，宿主继续做菜单锚点、返回快照和返回交接。
- `CouiDockCapsule.kt` 抽取 `CouiDockMenuContent`，供独立胶囊与双主体底栏复用；独立胶囊行为不变。
- `CouiDockStyle` 的单一 `followLightIntensity` 拆为 `lightFollowLightIntensity` / `darkFollowLightIntensity`，null 表示沿用 COUI 预设。

跟随光映射：

| 部件 | COUI 预设 | 浅色强度 | 深色强度 |
| --- | --- | --- | --- |
| 菜单胶囊 | `OpaqueSmall2`（1.0 / 0.15） | 2.0（2×） | 0.3（2×） |
| FAB | `OpaqueSmall1`（0.6 / 0.5） | 1.2（2×） | 0.5（预设，不再 2×） |

此前实现把 `intensity = 2f` 同时覆盖到深色，导致菜单深色实际为 2.0（预设 0.15 的 13 倍）；本次按预设分别计算。深色菜单/FAB 均明显减弱，浅色保留原 2×。

## 4. 宿主接入

`app/src/main/java/com/xiaomanjun/sleepdownschedule/feature/home/CouiHomeDockChrome.kt`：

- renderer 改为 `CouiFloatingRenderer.pair()`；创建失败继续记录原因并回到既有原生/液态 Dock。
- 加号使用 `CouiDockBar` 的 COUI FAB，不再创建原生 `NativeFab` 或 Compose `SleepDownFloatingAddButton`。
- 保留 `plusSlot`/`plusAlpha` 的收放时机：
  - 设置页收为 0，胶囊居中，FAB 左移并淡出。
  - 菜单打开时槽位保留、FAB 淡出并禁用点击，避免菜单锚点漂移。
- 点击 FAB 仍通过 PixelCopy 获取实际合成像素，生成圆形返回层并交给现有菜单 host；加号 bounds 继续作为菜单锚点。
- 深浅壁纸、无壁纸、明暗主题、底部导航区、IME 的处理沿用上一步。

## 5. 验证

已执行：

- `compileGithubReleaseKotlin`：通过。
- `:sleepdown-coui:testDebugUnitTest`：通过（既有手势用例）。
- `testGithubDebugUnitTest` 定向：
  - `ColorOsDockCompositionTest`：4 项通过，包含旧系统回退和既有 Dock 组合边界。
  - `VisualEffectsCapabilityTest`：通过。
- `assembleGithubRelease`：通过（1m56s，R8、资源压缩、lintVital、打包与签名）。APK `tmp/review-apks/SleepDown-1.2.8-beta2-couikut-dock-fab-20261106-c7c6a30a.apk`，7,676,200 字节，SHA-256 `C7C6A30A26C61FB165AB11FEA1ED2509D85CC5E1CA3581395342684A1ACC3AB6`；V2 签名通过，证书 SHA-256 仍为 `0f2b50df…`。精确 mapping 同目录同名 `-mapping.txt`，155,917,837 字节，SHA-256 `0DB170EC2499E9E4461D5A58242E2F644C91F0911FE320664F7783D62248D3B7`。`com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / versionCode 36`，minSdk 26，targetSdk 36。构建内嵌 revision 为 `03a5496d`（FAB 代码提交）。

未执行 / 未证实：

- 未在真机验证双主体 shader、融合边界、FAB 阴影和深色跟随光最终像素。
- Robolectric 不覆盖 COUI pair renderer 的实际 GPU 绘制。
- 菜单开合、返回位置和设置页收放仍需实机核对。

## 6. 回滚

本步在 `codex/couikit-dock-fab` 上单独提交。回退该提交即可恢复上一步的原生 FAB/Compose 后备和单一胶囊，不影响 `codex/couikit-dock` 的导航胶囊迁移与默认凝光提交。

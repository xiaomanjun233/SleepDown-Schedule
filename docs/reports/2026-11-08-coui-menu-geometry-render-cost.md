# 动作菜单字体、锚点与采样开销修正

接续 `ac81aed0`，用户反馈字体、外观、弹出锚点不对，Dialog和动画严重卡顿。此次继续使用COUIKit Menu；不恢复独立菜单外壳，不增加分割线。版本与仅本地交付边界不变。

## 原生依据与修正

- 重新沿相册APK的 `DefaultAdapter.setTitle → couiTextBodyL` 核对实际资源：普通动作是15sp、sans-serif、normal；固定字体分支才强制16sp。此前报告的16sp并非普通默认分支。两个菜单显式覆盖Kit默认14sp medium，使用自然字体高度与零字距，不覆盖其他菜单。
- `PopupMenuLocateHelper` 使用真实锚点及可见窗口。工具栏按锚点居中后避让，课程对齐逻辑起始边；上、下翻都保留8dp间隔。相册实际资源顶部安全留白16dp、底部32dp；长菜单限制可见高度并滚动，避免伸入系统栏和IME。
- 白光轮廓通过Kit的样式参数接入已有CouiEdge，沿用已核对的 `EDGE_STYLE_2`、28dp／2.5轮廓及材质混色。不是恢复第二套菜单渲染后端。菜单宽196dp、顶栏42dp／20dp图标保持；原生前景胶囊遮罩、无分割线和退场交接保持。

## 已确认的开销路径

- 完整窗口源在消费者出现时由Auto强制变成Offscreen，开窗会多出包含已有Dock、顶栏及正文源的整屏纹理。显式 `isolateContent=false` 的窗口现在持续使用Auto指令源，材质只隔离自身采样区，保留源录制、父链与退出生命周期。默认隔离入口未改。
- 源和浮动材质原来用 `neverEqualPolicy` 接收每次布局回调；不变的位置也能使所有依赖失效，浮动 `drawWithCache` 重新配置整条Shader链。现在比较实际大小和屏幕仿射几何，只在几何发生变化时通知；动画中的主体仍按实际帧更新。
- Dialog除显示／退出动画外，还可能因IME和正文布局重组。原窗口策略每次重组分配背景Drawable并重复调用多个Window setter。现在背景随Window建立，实际属性变化才合并提交；保留Compose更新参数之后的策略校准。
- Menu内两层相同尺寸Offscreen合并为外层唯一隔离，跟手光仍在背景混色后、文字前绘制。未改变Kawase等级、混色、融球、运动时序或阴影算法。

这些是源码和生命周期层面的开销修正，不是GPU耗时测量结论。用户感知卡顿是否完全消除仍需同场景真机比较。

## 验证与交付

菜单修正提交 `350f6715`，采样／窗口修正提交 `a8642f23`。最终定向运行通过（4m20s）：Kit12项、应用17项，共29项，0失败／错误／跳过。覆盖锚点避让与RTL、菜单取消／退出交接、分段提交、Dialog反向重开／嵌套、日期边界与草稿保存。窗口诊断确认被消费时完整源仍为Auto。日志 `tmp/coui-menu-geometry-final-tests-20261108.log`。

首次定向运行17项应用测试仅新增诊断断言失败：Compose的策略字符串是 `CompositingStrategy(value=0)`，不是 `Auto`；实际策略正确，断言已改为与公开Auto值比较。最终截图只用于布局／状态核对；不能以29项通过证明流畅度或GPU观感。

完整签名 `assembleGithubRelease --console=plain --no-parallel --max-workers=1 --no-daemon` 通过（5m50s，195任务，31执行／164复用），保留R8、资源压缩、lintVital和V2签名。日志 `tmp/coui-menu-geometry-release-20261108.log`。

| 归档文件 | 字节 | SHA-256 |
| --- | ---: | --- |
| `SleepDown-1.2.8-beta2-coui-menu-geometry-20261108-d5cf727f.apk` | 7,891,084 | `D5CF727F636FF2212639B65BA190DB2765E70D7B922D5A7F53E59AE08345AC44` |
| 同名 `-mapping.txt` | 163,103,350 | `65000215C70C719B46E51B820D4E3A7E538238F1F8909914DBCE810E873DAE87` |

文件位于 `tmp/review-apks`，副本与原构建输出哈希一致。APK内嵌源码 `b9a34058a798c393f80d817a36d856277b8fd609`；包名／版本 `com.xiaomanjun.sleepdownschedule / 1.2.8_beta2 / 36` 保持。V2证书SHA-256 `0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。交付元数据 `tmp/coui-menu-geometry-delivery-20261108.json`。

新包校验后，按用户已有清理授权删除上一份 `b0c89f55` 归档mapping（163,130,863字节），归档目录仅保留本包一份精确mapping。旧APK及其他目录保留。

设备调查：SDK37.0.1客户端；5038及5037均为同一SDK来源，USB设备列表和两通道mDNS均为空，没有其他可见ADB服务。因此未采集新设备帧耗时、GPU trace或原生对比截图，也未安装、启动或发布应用。Robolectric截图只用于排版／状态检查。

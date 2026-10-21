# 自动刷新课表适配审计与 Beta9 验证

日期：2026-09-22。普通版基线：main；本次不更新 exp。

## 上游来源与筛选

使用 [拾光官方仓库 fa7cbc2](https://github.com/ShiGuangSchedule/shiguang_warehouse/tree/fa7cbc2ea116e5ffd082a9fe8cb7bdf4407a8713) 的根索引、学校 adapters.yaml 和实际 JS 调用链，纳入 **130 所学校、142 个入口**。

判断依据是课程数据的来源：允许从 HTML、DOM、页面变量中读取学号、学期、校区、接口参数和作息；允许 response.text() 后解析 JSON。实际课程走结构化接口的适配器纳入，包括正方新教务、金智、部分 URP 等；校园网、WebVPN、VPN 要求不作为排除条件。西南大学使用上游 swu.js，与其他学校共用登录界面及文案。

只有课程来自 HTML 表格或页面内嵌 TaskActivity 等的入口排除，例如北京邮电大学、福建农林大学、重庆科技大学、湖南信息学院。武汉商学院的上游脚本优先调用接口，附带页面兜底，按接口能力纳入；成都中医药大学从页面提取任务标识和辅助信息，通过排课详情接口获取具体排课，纳入。河南师范大学提供整学期 HNSF_01；HNSF_02 仅返回所选月份，不用于覆盖整学期，旧连接需要重新选择该校整学期入口。

目录记录在 app/src/main/assets/auto_refresh/catalog.tsv，每项携带官方元数据及审阅源码 SHA-256。执行固定的已审阅内置源码，防止远端自动换成另一种取课方式。复用现有拾光 bridge 和资源目录，仅同步本清单涉及且变更的 65 份 JS；上游版权注释和已有 LICENSE 保留。哈希统一 CRLF/LF 和文件末尾换行；脚本内容中的原有空白保留。

## 会话与交互

- 所有学校使用教务导入页的浏览器、底栏、网页适配与选择器；初次操作就在已登录页面验证，复用已安装的原生 bridge。
- 首次连接正常展示上游学期、校区、日期等对话框，保存确认结果。后台以选项文本定位，选项顺序变化不会切换学期；原选项消失或新增必要输入时停止并要求重新登录。
- Cookie、学校入口、桌面模式、必要的 Web Storage 和选择结果随原配置一起由 Android Keystore 加密保存。仅采集上游脚本实际使用的存储字段，在同源文档启动时恢复；不采集整个站点存储，不自动提交账号密码。
- 后台仍使用官方 adapter → bridge → ImportDraft 校验 → 指定课表存储的链路。退出与刷新共用互斥锁，取消自动任务后清除配置，保留已导入课表。
- 从旧短间隔迁移至每天，保持是否启用不变；可选从不、每天、每7天。由 WorkManager 在网络可用时调度。

## 界面

个人信息卡片置顶，液态玻璃编辑头像按钮打开圆形预览，复用壁纸裁切手势和裁切数学；完成后才写入新头像。退出按钮复用通知设置的底部悬浮布局。周课表含调课标签时增加 24dp 底部溢出空间，编辑状态保留 40dp。

## 验证范围

已通过 10 项定向测试：清单完整性与源码哈希、HTML 元信息入口纳入、纯页面取课排除、旧频率迁移、选择结果复用与选项变化，以及既有统一认证路由。

测试命令：`testGithubDebugUnitTest --tests '*ShiguangApiAdapterCatalogTest' --tests '*AutoRefreshPreferencesTest' --tests '*SwuAuthRoutesTest'`，结果 BUILD SUCCESSFUL，10 项测试、0 失败、0 错误。

完整构建：`assembleGithubRelease --console=plain --no-parallel --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8'`，结果 BUILD SUCCESSFUL（4 分 58 秒）。保留资源压缩、R8、lintVital 与仓库外正式签名。初次尝试同时构建测试及 Release 时 2GiB 堆不足，之后使用单 worker 分开完成；构建参数未修改项目默认配置。

产物：`app/build/outputs/apk/github/release/app-github-release.apk`，6,662,984 字节；包名 `com.xiaomanjun.sleepdownschedule`，versionName `1.2.6_beta9`，versionCode `33`，minSdk 26 / targetSdk 36。apksigner 验证通过，证书 SHA-256：`0f2b50dfb7e10c6f5981ab966f1f4ebd76af81a7ad9ba31663284eaa88c0181a`。APK SHA-256：`c910dfd7fc7a0565e539a7b4151a38f4ec0edb87bdb9e9f91b2ed53b0e23aa34`。

自有 Kotlin、测试、构建配置及日志的 diff 空白检查通过。官方 JS 保留了上游部分行尾空白，不为通过空白检查修改第三方源码。

未持有各校账号，源码审计不等于 130 所学校逐校实测；需要校园网/VPN的入口仍取决于设备网络与学校会话有效期。本次未执行设备安装或真机 UI 验证。

## 适配入口

| 学校 | 入口 ID | 上游脚本 |
| --- | --- | --- |
| 安徽电气工程职业技术学院 | AEPU_01 | AEPU/aepu.js |
| 安徽财经大学 | AUFE_01 | AUFE/aufe_01.js |
| 安徽财经大学 | AUFE_02 | AUFE/aufe_01.js |
| 北部湾大学 | BBGU_01 | BBGU/bbgu.js |
| 常州机电职业技术学院 | CZIMT_01 | CZIMT/czimt.js |
| 成都中医药大学 | CDUTCM_01 | CDUTCM/cdutcm_01.js |
| 成都信息工程大学 | CUIT_01 | CUIT/cuit_bk_new.js |
| 成都医学院 | CMC_01 | CMC/cmc_01.js |
| 成都航空职业技术大学 | CAPU | CAPU/capadap.js |
| 成都银杏酒店管理学院 | YXHMC | YXHMC/yxhmc.js |
| 重庆化工职业学院 | CQCIVC | CQCIVC/cqcivc.js |
| 重庆大学 | CQU | CQU/cqu.js |
| 重庆工程学院 | CQIE_01 | CQIE/cqie_01.js |
| 重庆理工大学 | CQUT | CQUT/cqut_01.js |
| 重庆航天职业技术学院 | CQEPC_01 | CQEPC/cqepc.js |
| 重庆财经学院 | CFEC_01 | CFEC/CFEC.js |
| 长春理工大学 | CUST_01 | CUST/cust.js |
| 东北农业大学 | NEAU_01 | NEAU/NEAU_01.js |
| 东北大学 | NEU_1 | NEU/neu.js |
| 东北大学 | NEU_2 | NEU/neuyjs.js |
| 东北师范大学 | NENU_01 | NENU/NENU_01.js |
| 大连大学 | DLU_01 | DLU/dlu.js |
| 大连工程学院 | DLUTCI_01 | DLUTCI/dlutci.js |
| 大连理工大学 | DLUT_01 | DLUT/dlut_01.js |
| 德州学院 | DZU_01 | DZU/dzu_deep.js |
| 福州理工学院 | FIT | FIT/fit.js |
| 福建船政交通职业学院 | FJCPC | FJCPC/fjcpc.js |
| 广东工业大学 | GDUT_01 | GDUT/gdut.js |
| 广东技术师范大学 | GPNU_01 | GPNU/gpnu_01.js |
| 广东海洋大学 | GDOUYJ_01 | GDOU/gdouyj.js |
| 广东海洋大学 | GDOUYJ_02 | GDOU/gdouyj2.js |
| 广东海洋大学 | GDOU_01 | GDOU/gdou.js |
| 广东科学技术职业学院 | GDIT_01 | GDIT/gdit.js |
| 广东科技学院 | GDUST | GDUST/gdust.js |
| 广东药科大学 | GDPU_01 | GDPU/gdpu_01.js |
| 广州华立学院 | HUALIXY_01 | HUALIXY/hualixy.js |
| 广州航海学院 | GZMTU_01 | GZMTU/gzmtu.js |
| 广西医科大学 | GXMU_01 | GXMU/gxmu.js |
| 广西机电职业技术学院 | GXCME | GXCME/gxcme.js |
| 桂林信息科技学院 | GUIT_01 | GUIT/guit_01.js |
| 桂林医科大学 | GLMU_01 | GLMU/glmu.js |
| 甘肃医学院 | GSMC_01 | GSMC/gsmc_01.js |
| 甘肃财贸职业学院 | GSCMXY_01 | GSCMXY/gscmxy.js |
| 贵州大学 | GZU | GZU/gzu.js |
| 华中科技大学 | HUST_01 | HUST/hust.js |
| 华侨大学 | HQU | HQU/hquadap.js |
| 华南师范大学 | SCNU_01 | SCNU/scnu.js |
| 华南理工大学 | SCUT_01 | SCUT/SCUT_01.js |
| 华南理工大学 | SCUT_02 | SCUT/SCUT_01.js |
| 哈尔滨工业大学 | HIT | HIT/hit.js |
| 哈尔滨工程大学 | HRBEU | HRBEU/hrbeu.js |
| 哈尔滨工程大学 | HRBEU_YJS | HRBEU/hrbeu_yjs.js |
| 河北经贸大学 | HUEB | HUEB/hueb.js |
| 河南城建学院 | HUUC_01 | HUUC/huuc_01.js |
| 河南工业大学 | HAUT_01 | HAUT/haut_01.js |
| 河南师范大学 | HNSF_01 | HNSF/hnsf_01.js |
| 河南职业技术学院 | HNZY_01 | HNZY/hnzy.js |
| 河南财经政法大学 | HUEL_01 | HUEL/huel_01.js |
| 淮南师范学院 | HNNU_01 | HNNU/hnnu_01.js |
| 湖北医药学院 | HBMU_01 | HBMU/hbmu.js |
| 湖北文理学院 | HBUAS_01 | HBUAS/hbuas_01.js |
| 湖北汽车工业学院 | HUAT | HUAT/HUAT.js |
| 黑龙江大学 | HLJU | HLJU/hlju.js |
| 黑龙江大学 | HLJU_BACHELOR | HLJU/hlju_bachelor.js |
| 吉林大学 | JLU_01 | JLU/JLU_01.js |
| 吉林大学 | JLU_02 | JLU/JLU_01.js |
| 暨南大学 | JNU_01 | JNU/jnu_01.js |
| 江苏大学 | UJS_01 | UJS/ujs_zhengfang_v9.0.js |
| 江苏旅游职业学院 | JSTC_01 | JSTC/jstc_01.js |
| 江苏电子信息职业学院 | JSEI_01 | JSEI/jsei_01.js |
| 江苏科技大学 | JUST | JUST/just.js |
| 江西航空职业技术学院 | JHZYEDU_01 | JHZYEDU/zhengfang.js |
| 喀什大学 | KSU_01 | KSU/ksu_01.js |
| 临沂大学 | LYU | LYU/lyu.js |
| 兰州理工大学 | LUT | LUT/LUT.js |
| 洛阳理工学院 | LIT_01 | LIT/lit_01.js |
| 聊城大学东昌学院 | LCUDCC | LCUDCC/lcudcc.js |
| 辽宁大学 | LNU_01 | LNU/LNU_01.js |
| 辽宁科技大学 | USTL | USTL/ustl.js |
| 茂名职业技术学院 | MMPT | MMPT/mmpt.js |
| 内蒙古大学 | IMU_01 | IMU/imu_01.js |
| 南京信息工程大学 | NUIST_01 | NUIST/nuist.js |
| 南京工业大学 | NJTECH | NJTECH/njtech.js |
| 南京工业职业技术大学 | NIIT | NIIT/niit.js |
| 南京师范大学 | NJNU | NJNU/njnu.js |
| 南京邮电大学 | NJUPT_01 | NJUPT/njupt_01.js |
| 南方科技大学 | SUSTECH | SUSTECH/sustech.js |
| 南昌科技职业大学 | THDM_01 | THDM/THDM_01.js |
| 南通大学 | NTU | NTU/ntu.js |
| 宁波工程学院 | NBUT_01 | NBUT/nbut.js |
| 青岛理工大学 | QUT | QUT/qut.js |
| 青岛黄海学院 | QDHHC | QDHHC/qdhhc.js |
| 青海师范大学 | QHNU_01 | QHNU/qhnu_01.js |
| 齐齐哈尔大学 | QQHRU | QQHRU/qqhru.js |
| 齐齐哈尔工程学院 | QQHRIT | QQHRIT/qqhrit.js |
| 三峡大学 | CTGU | CTGU/ctgu.js |
| 三明学院 | SMXY | SMXY/smxy.js |
| 上海中侨职业技术大学 | SHZQ_01 | SHZQ/shzq.js |
| 上海财经大学浙江学院 | SHUFEZJ | SHUFEZJ/shufezj.js |
| 四川师范大学 | SICNU_01 | SICNU/school.js |
| 山东华宇工学院 | HUAYU_01 | HUAYU/huayu.js |
| 山东师范大学 | SDNU | SDNU/sdnu.js |
| 山东石油化工学院 | SDIPCT | SDIPCT/sdipct.js |
| 山东药品食品职业学院 | SDDFVC | SDDFVC/sddfvc.js |
| 山东轻工职业学院 | SDLIVC | SDLIVC/sdlivc.js |
| 山西工程职业学院 | SXGCXY_01 | SXGCXY/sxgcxy_01.js |
| 沈阳科技学院 | SYIST_01 | SYIST/syist_01.js |
| 绥化学院 | SHXY | SHXY/shxy.js |
| 同济大学 | TONGJI_01 | TONGJI/tongji_01.js |
| 塔里木大学 | TARU_01 | TARU/taru_01.js |
| 天津城建大学 | TCU_01 | TCU/tcu_01.js |
| 天津科技大学 | TUST_01 | TUST/tust_01.js |
| 铜仁学院 | GZTRC | GZTRC/gztrc.js |
| 铜仁学院 | GZTRC_OLD | GZTRC/gztrc_old.js |
| 文华学院 | WENHUA_01 | WENHUA/wenhua_01.js |
| 无锡学院 | CWXU_01 | CWXU/cwxu_01.js |
| 武汉商学院 | WBU_02 | WBU/wbu_02.js |
| 武汉商学院 | WBU_03 | WBU/wbu_02.js |
| 武汉理工大学 | WHUT_01 | WHUT/whut_01.js |
| 新疆政法学院 | XJZFU_01 | XJZFU/xjzfu.js |
| 西北工业大学 | NWPU_01 | NWPU/nwpu_01.js |
| 西南交通大学 | SWJTU_01 | SWJTU/swjtu_yhxt.js |
| 西南大学 | SWU_01 | SWU/swu.js |
| 西安交通大学 | XJTU_01 | XJTU/xjtu.js |
| 西安建筑科技大学 | XAUAT_01 | XAUAT/xauat.js |
| 西安文理学院 | XAWL_01 | XAWL/xawl_01.js |
| 西安科技大学 | XUST_01 | XUST/xust_01.js |
| 宜宾学院 | YIBINU_01 | YIBINU/yibinu_01.js |
| 扬州大学 | YZU_01 | YZU/yzu.js |
| 中国地质大学(武汉) | CUG_01 | CUG/cug.js |
| 中国民航大学 | CAUC | CAUC/cauc.js |
| 中国石油大学(北京) | CUP_01 | CUP/cup_01.js |
| 中国石油大学(北京) | CUP_02 | CUP/cup_02.js |
| 中国石油大学（北京）克拉玛依校区 | CUPK_01 | CUPK/cupk_01.js |
| 中国科学技术大学 | USTC_01 | USTC/ustc_01.js |
| 中国计量大学 | CJLU | CJLU/cjlu.js |
| 中山大学 | SYSU | SYSU/sysu.js |
| 中山大学 | SYSU_01 | SYSU/sysu_01.js |
| 枣庄学院 | UZZ_01 | UZZ/uzz_01.js |
| 浙江中医药大学 | ZCMU | ZCMU/zcmu.js |
| 浙江工业大学 | ZJUT_01 | ZJUT/zjut_01.js |
| 郑州大学 | ZZU_01 | ZZU/zzu.js |

# PLJ110 / Android 17 的真实 Release 栈：PeriodSchemeEditor 的顶部 Dp 加法中，
# R8 9.2.14 将 Modifier 引用复用到 float 寄存器，ART 以 VerifyError 拒绝整个类。
# 只保留该 Compose 方法的代码形态，其他方法仍参与优化、裁剪及名称混淆。
-keepclassmembers,allowshrinking,allowobfuscation class com.xiaomanjun.sleepdownschedule.feature.settings.PeriodSchemeEditorUiKt {
    void PeriodSchemeEditor-*(...);
}

# Xiaomi focus notifications cross the OEM notification / privileged Binder boundary.
# Keep the island builder, bridge callbacks and manifest restore receiver intact in R8 builds.
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiShizukuBridge { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiShizukuBridge$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiRootBridge { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiRootBridge$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiRootFirewallCommand { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiFirewallService { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.IXiaomiFirewallService$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiNetworkRestoreReceiver { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiNetworkRestoreReceiver$* { *; }

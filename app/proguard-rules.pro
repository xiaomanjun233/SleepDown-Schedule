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

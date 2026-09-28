# The common transition framework loads this adapter only after the ColorOS runtime class exists.
# Keep the boundary intact so R8 cannot inline the compileOnly vendor superclass into common code.
-keep class com.xiaomanjun.sleepdownschedule.transition.OplusVendorCallbackFactory { *; }
-keep class com.xiaomanjun.sleepdownschedule.transition.OplusVendorAnimationCallback { *; }

# Retaining the callback is part of the per-session protocol, not an otherwise observable read.
# Without this member rule R8 correctly sees the field as dead and removes the strong reference.
-keepclassmembers class com.xiaomanjun.sleepdownschedule.transition.NativeSessionResource {
    java.lang.Object callback;
}

# Xiaomi focus notifications cross the OEM notification / privileged Binder boundary.
# Keep the island builder, bridge callbacks and manifest restore receiver intact in R8 builds.
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiSuperIsland$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiShizukuBridge { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiShizukuBridge$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiRootBridge { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiRootBridge$* { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiNetworkRestoreReceiver { *; }
-keep class com.xiaomanjun.sleepdownschedule.feature.experimental.XiaomiNetworkRestoreReceiver$* { *; }

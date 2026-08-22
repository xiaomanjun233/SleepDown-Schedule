# The common transition framework loads this adapter only after the ColorOS runtime class exists.
# Keep the boundary intact so R8 cannot inline the compileOnly vendor superclass into common code.
-keep class com.xiaomanjun.sleepdownschedule.transition.OplusVendorCallbackFactory { *; }
-keep class com.xiaomanjun.sleepdownschedule.transition.OplusVendorAnimationCallback { *; }

# Retaining the callback is part of the per-session protocol, not an otherwise observable read.
# Without this member rule R8 correctly sees the field as dead and removes the strong reference.
-keepclassmembers class com.xiaomanjun.sleepdownschedule.transition.NativeSessionResource {
    java.lang.Object callback;
}

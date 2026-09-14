# SleepDown Kyant Backdrop source dependency

Upstream: https://github.com/Kyant0/AndroidLiquidGlass

Baseline: tag `2.0.0`, commit `bebb11a91bd97bf1dabde479f3b332ad9898731f`.
License: Apache-2.0; see LICENSE. Original `com.kyant.backdrop` packages are retained.
The 34 commonMain/androidMain source files were copied from that tag. This module builds only
the Android target; its Compose dependencies resolve against the application's existing 1.11.2
version. No new platform target or upstream catalog implementation is bundled.

## SleepDown changes (2026-09-08)

- `internal/ShapeProvider`: element equality follows shape callback and render options; equivalent
  element updates keep the node-owned outline cache. Fixed-host geometry uses translated outlines.
- `DrawBackdropModifier`: separate draw invalidation from effect invalidation; optional sample-free
  decoration and paused rendering retain the original isolation/clip/blend order.
- `BackdropRenderOptions`: per-consumer enabled predicate, logical bounds and bounded allocation
  padding. Defaults preserve upstream rendering. Fixed geometry cannot be used with export yet.
- `BackdropEffectScope`, `effects/Lens`: logical effect size and origin are independent of host
  allocation. Each consumer still owns its own mutable RuntimeShader and effect chain.
- `highlight/HighlightModifier`, `shadow/*Modifier`: pause before material work; inset highlighting
  uses the original local coordinate system. Native material-layer lifecycle/recording counters.
- `backdrops/LayerBackdropModifier`: producer recording and size counters.
- `layerBackdrop(recordKey)`: opt-in completed-recording fingerprint. Null keeps upstream live
  recording. The node invalidates on source, size, density, font-scale, direction and lifecycle
  changes; callers own content/animation invalidation. Home enables this only for settled wallpaper.
- Course cards opt into decoration recording reuse. Size, density, font scale, layout direction,
  outline and immutable material values form the key; translation alone retains the existing
  highlight/shadow recording. Custom highlight shaders keep recording every draw. Sampling,
  refraction and blur remain live, with the original layer ownership and blend order.
- `BackdropDiagnostics`: optional sink. `RecordedPixelArea` counts recorded pixels; it is not GPU
  allocation, resident-memory, frame-submission or first-visible-frame evidence.

`patches/kyant-backdrop-2.0.0-sleepdown.patch` records changes to upstream sources. The Android-only
Gradle adapter and new extension files live here. Compare/rebase against the exact tag above;
do not substitute a newer binary without repeating equivalence and lifecycle validation.

## Candidate limitations

Fixed Morph and retained-node occlusion are diagnostics-only until phone/tablet visual and GPU
validation. Release does not enable either candidate through Gradle experiment properties.
Fixed geometry callers supply a finite host envelope and enough padding for the complete effect
curve, preserve the local shape, and release the envelope at Open. Mutable shader instances must
never be shared across consumers. Unsupported custom export is rejected, not silently shifted.

## Host regression tests

On 2026-09-14, `BackdropRenderOptions.coordinatesFrozen` adds an opt-in position-notification
gate for retained underlays. It keeps the existing sample, effects and decoration nodes alive;
new coordinate nodes, model/effect changes and size changes still refresh normally. Reading the
flag in draw also invalidates once on resume. Foreground consumers keep the default live behavior.

The follow-up adds `sampleRecordKey`, an opt-in completed sample-recording identity. Reuse requires
both `coordinatesFrozen()` and a matching non-null key, size, density, font scale and layout
direction. Live draws still record; modifier/effect geometry updates and node replacement clear
the cached identity. This keeps the existing sampling layer and RenderEffect ownership, with no
new bitmap or GraphicsLayer. `Sample.FrozenReuse` counts avoided recordings, not GPU frame time.
Only a host with a complete frozen scene identity may supply this key.

`ShapeProvider` retains outlines through a derived state so dynamic shapes at fixed host sizes
still update their clipping when animation state changes. Static shapes keep cached outlines.
`DynamicOutlineCacheTest` covers both behaviors without requiring a GPU.

Pager/scroll consumer coordinates now remain node-local and call `invalidateDraw()` directly.
Moving glass still samples the current wallpaper position every frame, while avoiding one
Snapshot-state write and its notification fan-out per consumer. No sampling resolution, blur,
lens or decoration quality is reduced. Exported backdrop coordinates keep their published state.

`SharedBlurBackdrop` shares the wallpaper prefix across course cards. The 2026-09-10 alignment
uses NexioSchedule commit `2971759ed3bb7b16ef13e639fba5dbf2a6a9cb2d` as its reference:
[DrawBackdropModifier](https://github.com/HaoZai000/NexioSchedule/blob/2971759ed3bb7b16ef13e639fba5dbf2a6a9cb2d/app/src/main/java/com/kyant/backdrop/DrawBackdropModifier.kt),
[SharedBlurBackdrop](https://github.com/HaoZai000/NexioSchedule/blob/2971759ed3bb7b16ef13e639fba5dbf2a6a9cb2d/app/src/main/java/com/kyant/backdrop/backdrops/SharedBlurBackdrop.kt).
The matching course path uses a 0.48 shared source and consumer buffer, proportionally scaled
blur, direct sampled-pixel translation into the card, and a per-card lens with depthEffect=false.
Course cards do not apply vibrancy before blur. SleepDown's tint, preset edge highlight, outline
light and shadows remain additional decorations at the original layout resolution.

The direct sampling branch is adapted from that Nexio implementation, retaining the upstream
Apache-2.0 component packages and license. Consumers with inverse transforms, fixed geometry,
exports or mismatched scales retain the generic coordinate-correct path. The recorder owns one
layer and releases it on detach; SleepDown's completed-recording cache remains an invalidation
optimization and does not change the effect sequence. `Sample.SharedDirect` identifies direct
sampling in diagnostic builds. Matching the base path does not imply identical appearance or
measured frame time once SleepDown decorations are enabled.

The shared layer keeps its RenderEffect attached: recording drawLayer is a display-list reference,
not a pixel bake. Sampling applies inverse consumer transform, full-resolution source offset, then
texture upscaling in the generic path. The matching direct path translates by the source offset
times 0.48 in sampled pixels, then expands only the final card buffer.

Course cards now use node-internal sampling buffers, following Nexio's full-size layout approach.
`BackdropRenderOptions.sampleScale` scales only the sampling buffer and effect density/geometry;
source coordinates, clipping and decorations stay at full resolution. Fixed Morph allocations and
exported backdrops retain scale 1. No scroll-driven material culling is introduced. The host geometry
test checks proportional dp conversion, stable updates and restoration to full resolution.

`testAndroidHostTest` checks completed recording reuse, unkeyed dynamic frames, content/geometry
changes and lifecycle reset. These tests do not measure GPU execution or prove pixel equivalence.

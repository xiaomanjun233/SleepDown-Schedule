# SleepDown UI Performance Benchmark Handoff

Date: 2026-08-13

## Status: PLJ110 PAUSED; XIAOMI TABLET EXPLORATORY

Paused on 2026-08-14 by user decision. Do not continue physical-device benchmark,
JankStats, direct Perfetto, gfxinfo, APK installation, or UI automation on the
current `PLJ110` device until the user explicitly resumes this work.

The current device is participating in a Beta OS program. ColorOS rejected both
the `githubBenchmarkRelease` and `githubBenchmark` diagnostic APKs with vendor
status `-99`; system logs identified the path as `PC install attack detected` /
`OPLUS_ADB_INSTALL_CANCEL`. This is an environment limitation, not evidence of an
app or benchmark-code failure.

Keep all existing Macrobenchmark journeys, test tags, PerformanceMetricsState
states, trace labels, emulator smoke artifacts, and the benchmark-only JankStats
summary implementation. Emulator results remain `EMULATOR SMOKE TEST ONLY`.

Preferred resume condition: a stable-release physical device that permits the
isolated `com.xiaomanjun.sleepdownschedule.benchmark` package to be installed, or
an explicit user decision to retry after the device leaves the Beta program.

The PLJ110/ColorOS route remains paused. On 2026-08-14 the user explicitly
resumed an exploratory route on a Xiaomi tablet. This report separates emulator
smoke, PLJ110 failures, and tablet dry-run results. No number below is a formal
five-iteration physical-device baseline.

## XIAOMI TABLET DRY-RUN

Device: `25053RP5CC`, Android 16/API 36, serial `0A84E43049A40540`, 3200×2136
landscape at 400 dpi and 120 Hz peak refresh rate.

The isolated target and runner installed successfully. AndroidX completed one
fullscreen `PersonalizationBenchmark#openWithoutCompilation` dry-run and emitted
a Perfetto trace. The one-iteration result was 30 frames:

| Metric | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| CPU frame duration | 17.8 ms | 43.3 ms | 46.8 ms | 49.7 ms |
| Frame overrun | 9.9 ms | 48.8 ms | 53.4 ms | 63.3 ms |

These values indicate visible missed deadlines in this single run, but the
sample is too small to establish a baseline or compare implementations.

### Fullscreen add destinations and page transition

Four `CompilationMode.None()` dry-runs completed 4/4 on the tablet. Each test
starts after the add menu is fully open (where applicable) and captures the
same 800 ms interval after the click. Each row is still only one iteration.

| Scenario | Frames | CPU P50 | CPU P90 | CPU P95 | CPU P99 | Overrun P50 | Overrun P90 | Overrun P95 | Overrun P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Add single course | 42 | 20.3 | 54.4 | 56.7 | 72.1 | 14.7 | 59.5 | 79.1 | 84.9 |
| Manual import | 40 | 24.2 | 53.8 | 55.1 | 61.1 | 20.3 | 58.7 | 71.6 | 74.7 |
| Education-system import | 26 | 20.3 | 112.4 | 129.3 | 141.0 | 15.2 | 149.5 | 183.3 | 192.4 |
| Home to Settings | 93 | 6.1 | 9.8 | 25.1 | 58.5 | -1.2 | 4.1 | 34.9 | 56.0 |

All timings are milliseconds. The three add destinations miss deadlines even
at the median in this run. Education-system import has the worst tail, while
Home-to-Settings is substantially smoother through P90. Four corresponding
Perfetto traces are stored under
`benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/25053RP5CC - 16/`.

ADB accepted three exact freeform bounds: 900×1600 px (360×640dp), 1300×1800 px
(520×720dp), and 2200×1500 px (880×600dp). The compact AndroidX dry-run was
attempted twice. Both attempts failed before the measured click because the
managed run lost/reset the freeform task and UiAutomator could no longer find
`benchmark_personalize_button`. Manual freeform launch and hierarchy capture did
expose the node correctly. Therefore no freeform frame result is valid yet, and
the failure trace must not be interpreted as application performance.

## Scope

The intended first physical-device scenario is exactly one journey:

- Class: `PersonalizationBenchmark`
- Test: `openWithoutCompilation`
- Journey: Home screen -> tap the Personalization button -> wait for the Personalization panel (`首页壁纸`) to be visible
- Metric: `FrameTimingMetric`
- Compilation mode: `CompilationMode.None()`
- Normal iteration count in source: 5
- Initial requested run: AndroidX dry-run (one iteration), before the five-iteration run and before `CompilationMode.Partial`

No physical measurement was started for Close, Slider, Import History, or any other formal scenario after this limitation was identified.

## Code And Packaging

### Packages

The benchmark target is deliberately isolated from the formal application:

| Role | Package |
| --- | --- |
| Formal application | `com.xiaomanjun.sleepdownschedule` |
| Benchmark target | `com.xiaomanjun.sleepdownschedule.benchmark` |
| Instrumentation APK | `com.xiaomanjun.sleepdownschedule.benchmark.test` |

The app Gradle benchmark variant is `githubBenchmarkRelease`; its generated application ID was verified as `com.xiaomanjun.sleepdownschedule.benchmark`. Installation and direct launch in this session used only this isolated target and runner. The formal package was not launched or overwritten by the benchmark work.

### Journey implementation

`BenchmarkHomeJourney.kt` launches the target's `LauncherFollow` launcher alias using `MacrobenchmarkScope.startActivityAndWait()` after `pressHome()`.

`PersonalizationBenchmark.kt` uses `MacrobenchmarkRule.measureRepeated` with `FrameTimingMetric`, warm startup, `CompilationMode.None()`, and five configured iterations. The UI interaction originally attempted UiAutomator lookup by a Compose semantic description/test tag. On the ColorOS device, that tag was not visible to UiAutomator, so the test stayed on the home screen.

Direct ADB validation established that this physical device opens Personalization when the isolated target is started and receives:

```text
adb shell input tap 552 256
```

On the 1256 x 2760 device this is approximately 44% of display width and 9.3% of display height. The benchmark was changed to use the corresponding proportional `device.click(...)` coordinates. During direct instrumentation, the `首页壁纸` panel was observed in the device UI, proving the click path itself works.

`ScheduleUi.kt` also contains an attempted `testTagsAsResourceId = true` mapping at the Home root. After rebuilding and installing the isolated target, UiAutomator still did not expose `benchmark_personalize_button` as a resource ID on this device. Therefore `By.res(...)` was not used for the final direct-instrumentation attempt.

### Trace instrumentation

`Performance.kt` maps the Personalization motion phases to trace labels including `SleepDown.Personalize.Open`. This is intended to make the app transition identifiable in Perfetto alongside `FrameTimingMetric`.

## EMULATOR SMOKE TEST

Environment: Pixel 10 Pro AVD, Android 16/API 36, x86_64, 60 Hz. AndroidX emulator validation was supplied temporarily through instrumentation arguments only; the project was not permanently changed to suppress the `EMULATOR` error.

These results prove the automation mechanics on the AVD only. They must not be interpreted as user-device performance values.

| Scenario | Compilation | Iterations | P50 overrun | P95 overrun | P99 overrun |
| --- | --- | ---: | ---: | ---: | ---: |
| Personalization Open | None | 5 | 89.8 ms | 279.9 ms | 297.5 ms |
| Personalization Open | Partial | 5 | 92.9 ms | 298.0 ms | 350.0 ms |
| Personalization Close | None | 5 | 97.7 ms | 204.2 ms | 220.8 ms |
| Blur slider continuous drag | None | 5 | 210.8 ms | 363.3 ms | 403.6 ms |

An emulator dry-run also generated a valid single-iteration Perfetto trace. This establishes that Macrobenchmark code and the general journey were executable on the AVD before the later device-specific automation changes.

## PHYSICAL DEVICE BASELINE

### Device conditions

| Property | Observed value |
| --- | --- |
| ADB serial | `3B15AE023YL00000` |
| Model | `PLJ110` |
| OS/API | ColorOS / Android 16, API 36 |
| Display resolution | 1256 x 2760 |
| Peak refresh-rate setting | 120.00001 Hz |
| Device state required by test | Unlocked (`isKeyguardShowing=false`) |

### Result

**No valid physical-device baseline exists.** No FrameTimingMetric JSON and no completed Perfetto trace were recovered.

### Observed execution evidence

1. The Gradle task used was `:benchmark:connectedBenchmarkReleaseAndroidTest` with one selected test and AndroidX dry-run argument.
2. Passing `--serial` causes an AGP 9.2.1 failure before test execution:

```text
DeviceProviderInstrumentTestTask.getFilteredDevices
java.lang.UnsupportedOperationException
at com.google.common.collect.ImmutableCollection.remove
```

With only one USB device attached, the task was run without `--serial` to avoid that AGP bug.
3. A previous Gradle run did execute instrumentation and synchronized host test artifacts under:

```text
benchmark/build/outputs/androidTest-results/connected/benchmarkRelease/PLJ110 - 16/
```

Its synced log records AndroidX's actual device output location as:

```text
/sdcard/Android/media/com.xiaomanjun.sleepdownschedule.benchmark.test/additional_test_output
```

The run was invalid: ColorOS reported `keyguardOn=true`, intercepted the injected Home action, and the setup never reached the intended UI click. The result XML reports `Process crashed`; that is not evidence that the target app crashed during its UI transition.
4. Direct ADB invocation after the coordinate-click change did reach the Personalization panel. At that point the device had live target, runner, and Perfetto processes, and AndroidX wrote:

```text
/sdcard/Android/media/com.xiaomanjun.sleepdownschedule.benchmark.test/trace_config.pb
```

It did **not** create an `additional_test_output` directory, benchmark JSON, or `.perfetto-trace` before the run was stopped. The direct instrumentation did not finish within several minutes.

### Host artifact audit

The expected Gradle host root is:

```text
benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/
```

At handoff it contains no completed physical-device JSON or Perfetto trace. The available host evidence is the Gradle connected-test result directory and its saved logcat/test-result files:

```text
benchmark/build/outputs/androidTest-results/connected/benchmarkRelease/PLJ110 - 16/
```

The saved log is important because it proves the actual AndroidX `additionalTestOutputDir` rather than guessing a path. The target/test APKs may be removed during Gradle cleanup, so the device-side directory can disappear after failure.

## Current Blocking Problem

The click mechanism is now independently proven on the physical device. The unresolved issue is AndroidX Macrobenchmark completion and artifact emission on ColorOS/Android 16:

- Gradle `--serial` is broken by AGP 9.2.1 device filtering.
- Gradle without `--serial` can hang in device test setup/execution; it synchronizes logs but has not emitted completed additional output.
- Direct `am instrument` reaches the target panel and starts Perfetto, but does not finish or emit JSON/trace before timeout.
- Do not claim the existing emulator numbers as a physical baseline.

## Recommended Next Investigation

1. Capture a full direct-instrumentation logcat focused on `Benchmark`, `TestRunner`, `perfetto`, and ActivityManager from start through termination; determine the exact blocking call after `trace_config.pb` is written.
2. Verify AndroidX Benchmark 1.4.1 compatibility with ColorOS Android 16, especially Perfetto stop/trace processor shell server behavior. Test a minimal upstream Macrobenchmark sample on the same physical device to separate device/vendor behavior from SleepDown code.
3. Keep the physical device unlocked and disable/avoid overlays or permission dialogs before the run. Do not use the failed global `waitForIdle()` pattern on this device.
4. Once a dry-run completes, immediately inspect the exact device `additional_test_output` path and copy it to the host. Only then run the configured five iterations for None, followed by Partial.
5. Consider using the directly verified proportional click only as a short-term physical-device fallback. It depends on current portrait layout and should be validated across window sizes before becoming the long-term benchmark selector.

## Commands Used

Gradle, no serial because of the AGP filtering exception:

```powershell
$selector='com.xiaomanjun.sleepdownschedule.benchmark.PersonalizationBenchmark#openWithoutCompilation'
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest --rerun `
  "-Pandroid.testInstrumentationRunnerArguments.class=$selector" `
  "-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true" `
  --console=plain
```

Direct instrumentation used to verify the final target path:

```powershell
adb -s 3B15AE023YL00000 shell "am instrument -w -r `
  -e class 'com.xiaomanjun.sleepdownschedule.benchmark.PersonalizationBenchmark#openWithoutCompilation' `
  -e androidx.benchmark.dryRunMode.enable true `
  com.xiaomanjun.sleepdownschedule.benchmark.test/androidx.test.runner.AndroidJUnitRunner"
```

The direct invocation remains incomplete and is not a source of benchmark values.

## XIAOMI TABLET ADD-DESTINATION EXPERIMENTS (2026-08-15)

### Valid device and journey

- Device: `25053RP5CC`, serial `0A84E43049A40540`, Android 16/API 36.
- Full-screen landscape: 3200 x 2136, 400 dpi, peak 120 Hz.
- Variant/package: isolated `githubBenchmarkRelease`, target
  `com.xiaomanjun.sleepdownschedule.benchmark`; the formal app was not replaced.
- Measurement: `CompilationMode.None()`, warm start, unchanged 800 ms capture window.
- Test: `HomePopupAndPageTransitionBenchmark`; all four scenarios completed one dry-run and five
  formal iterations.

### Trace diagnosis

The three add destinations shared the same slow-frame chain: main-thread
`Choreographer#doFrame -> traversal -> draw -> syncAndDrawFrame -> postAndWait` waited for
RenderThread. During the original morph, `flush layers`, Vulkan image allocation, and texture
uploads dominated; animated `.offset().size()` and an animated `HomeAddMenuMorphPanel.targetSize`
caused differently sized offscreen/blur/glass layers to be reallocated across frames.

Edu import's additional 100 ms+ tail was also on RenderThread: full-screen 3200 x 2136 layer
allocation/upload, direct reclaim, and Vulkan pipeline/shader work. The trace did not show a
corresponding 100 ms main-thread `ShiguangWarehouse.loadAdapters()` block, so adapter I/O was not
changed merely on suspicion.

### Rejected fixed-shell optimization

- The experiment laid the morph shell out once at final target size and animated layer translation,
  scale, clip, and shape instead of changing Compose width/height each frame.
- The expanded source menu is recorded once at its true source size and replayed as a cached
  `GraphicsLayer` through source handoff.
- The moving destination glass surface is frozen during Opening/Closing and returns to live
  `LiquidPanel` recording in the stable Open phase.
- Destination/source blur keeps its visual endpoints and uses stable sharp and fixed-blur cached
  layers with crossfade, rather than a changing `RenderEffect` radius.
- Existing 330/350 ms timing, background zoom, handoffs, target geometry, full-screen endpoint,
  and reverse-close state machine were not changed in code.

Device visual review subsequently found that the non-uniform shell scaling distorted circular
corners and laid content out at final-page size before compressing it into the moving popup. That
changes the visible corner and layout even though the numerical endpoints are unchanged. The
fixed-shell/source-scaling/surface-freezing part is therefore rejected and has been reverted.

### Before and after

The original values below are one diagnostic dry-run per scenario; the retained result is the
aggregate from five formal iterations. They establish direction, but the original row is not a
five-iteration statistical baseline.

| Scenario | Sample | CPU P50 | CPU P90 | Overrun P50 | Overrun P90 | Overrun P95 | Overrun P99 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Add course | Original dry-run | 20.3 ms | 54.4 ms | 14.7 ms | 59.5 ms | 79.1 ms | 84.9 ms |
| Add course | Rejected fixed shell, 5 iterations | 25.8 ms | 41.0 ms | 23.0 ms | 45.1 ms | 51.0 ms | 75.7 ms |
| Manual import | Original dry-run | 24.2 ms | 53.8 ms | 20.3 ms | 58.7 ms | 71.6 ms | 74.7 ms |
| Manual import | Rejected fixed shell, 5 iterations | 26.4 ms | 41.3 ms | 23.6 ms | 49.9 ms | 64.0 ms | 92.2 ms |
| Edu import | Original dry-run | 20.3 ms | 112.4 ms | 15.2 ms | 149.5 ms | 183.3 ms | 192.4 ms |
| Edu import | Rejected fixed shell, 5 iterations | 29.3 ms | 51.7 ms | 26.4 ms | 64.9 ms | 98.3 ms | 148.4 ms |
| Home to Settings | Original dry-run | 6.1 ms | 9.8 ms | -1.2 ms | 4.1 ms | 34.9 ms | 56.0 ms |
| Home to Settings | Rejected fixed shell, 5 iterations | 7.8 ms | 13.2 ms | -0.4 ms | 7.9 ms | 9.9 ms | 66.2 ms |

Patch 2 dry-run also reduced representative `flush layers` averages from 7.09 to 3.61 ms for
add-course and from 7.24 to 3.74 ms for manual import. Edu representative total flush time fell
from about 345 ms to 175 ms. P90 and especially the edu tail improved substantially, while P50
remains above the first-stage target.

An attempted follow-up moved alpha/effect directly onto recorded `GraphicsLayer` objects. Perfetto
still showed two full target texture uploads per frame and the single edu sample regressed, so that
extra complexity was reverted. These five-iteration values are diagnostic evidence only because
the visual result was later rejected.

### Additional edu layer experiments

One further evidence-driven round tested three ways to reduce edu's full-screen handoff cost. None
was retained:

- Applying inverse transforms to the destination child kept its pixels visually fixed inside the
  parent morph, but did not reduce the full-screen RenderTargets. Its formal five-iteration edu
  result regressed to CPU P50/P90 `30.3/54.6 ms` and overrun P50/P90/P95/P99
  `29.4/74.9/101.0/148.2 ms`.
- Removing the extra full-screen Material background did not remove the 3200 x 2136 texture
  uploads; the compositor had already merged that simple fill, and the dry-run worsened.
- Replacing the sharp/fixed-blur pair with one destination layer and a changing `RenderEffect`
  reduced allocations in one dry-run, but the formal five-iteration tail regressed to CPU
  P50/P90/P95/P99 `26.6/54.5/69.6/120.0 ms` and overrun
  `24.8/76.9/103.9/160.1 ms`, consistent with per-frame effect invalidation.

All three experiments were reverted before the later visual-correctness rollback.

### Visual-correctness rollback and short check

The moving shell now again uses the original animated `offset + size`, the actual per-frame corner,
the live `LiquidPanel`, and the original per-frame source-menu layout. Only the destination-content
recording and sharp/fixed-blur crossfade remain from the performance work. The benchmark variant
compiled, and all four scenarios passed one isolated dry-run. Its CPU P50/P90 values were add
`22.6/54.6`, manual `25.8/58.3`, edu `16.3/121.6`, and settings `5.7/9.5 ms`; overrun P50/P90 was
`17.1/69.3`, `21.8/59.7`, `11.5/197.1`, and `-1.4/2.6 ms`. This single run shows that the fixed-shell
performance gain does not survive the geometry rollback, especially for edu, and is not a formal
baseline. The output directory currently contains these four dry-run traces; the earlier 20 trace
files were replaced by the rerun, while their aggregate metrics remain in the table above.

A final attempt replaced only the outer `graphicsLayer` clip with an equivalent Canvas path clip,
leaving real offset, size, corner, and content layout unchanged. It did not improve add/manual and
regressed edu: CPU P50/P90 was add `25.7/55.4`, manual `25.7/57.0`, edu `15.0/137.4`, settings
`6.0/9.2 ms`; overrun P50/P90 was `23.0/69.3`, `23.5/65.0`, `7.5/203.6`, and `-1.3/2.6 ms`.
The Canvas clip experiment was reverted without a formal run. The source is back at the visually
confirmed dynamic-geometry state described above; the output directory contains the four Canvas
experiment traces, not an accepted baseline.

### Remaining hotspot

The remaining median cost is still RenderThread/GPU work rather than Compose recomposition. Edu's
worst inspected frame spent about 76 ms in `flush layers`, with repeated Vulkan image allocations
and several 3200 x 2136 uploads; main-thread recompose/measure work in that frame was below 0.1 ms.
The tested Compose `graphicsLayer` arrangements only reshuffled the same three to four full-screen
RenderTargets. A future round therefore needs a structural handoff that rasterizes/masks the edu
destination into one compositor layer, or a lower-level RenderNode/custom-draw prototype, while
preserving the original geometry and image under device visual review. Adapter async/cache work
should only be done if a future trace shows asset parsing on the click-to-display critical path.

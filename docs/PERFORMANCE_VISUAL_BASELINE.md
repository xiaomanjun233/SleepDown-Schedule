# Performance visual baseline

This document is the hard visual-regression contract for performance work.

## Invariants

Performance changes must not alter:

- glass token values or Backdrop effect order;
- blur, lens, tint, highlight, shadow, inner-shadow, or chromatic-aberration values;
- wallpaper source resolution, crop focus, brightness, or crossfade behavior;
- animation duration, delay, easing, spring, overshoot, origin, or target geometry;
- layout dimensions, typography, colors, content order, or clipping;
- the source Backdrop sampled by an existing visual element.

An optimization that cannot preserve these invariants must be reverted or split into a separately approved visual change.

## Reference scenarios

Capture screenshots and screen recordings for each scenario before and after a performance phase:

1. Home week and day modes with the default light and dark wallpapers.
2. Home week and day modes with a high-resolution custom wallpaper.
3. Top controls, mode switch, week header, and bottom dock over light and dark regions.
4. Course editor open, immediate back, close, save, and rotate while open.
5. Week edit move, resize, invalid drop, edge scrolling, save, and delete confirmation.
6. Add menu and personalization panel opening and closing.
7. Settings root, schedule settings scrolling, and multi-schedule carousel switching.
8. Manual import, education import, AI progress, and confirmation preview.

## Performance gates

- Measure benchmark/profileable builds, not debug builds.
- Compare frame timing, frame overrun, startup timing, allocations, and garbage collections.
- A phase must improve a measured hotspot and preserve all reference visuals.
- A phase with a visual mismatch or an unexplained regression is not accepted.

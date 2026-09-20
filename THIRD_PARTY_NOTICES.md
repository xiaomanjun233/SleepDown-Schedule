# Third Party Notices

## HaoZai000/NexioSchedule

The progressive top-bar blur shaders in `glass/ui/NexioProgressiveBlur.kt` are
adapted from `ProgressiveBlurTopBar.kt` provided by
[@HaoZai000](https://github.com/HaoZai000), author of
[NexioSchedule](https://github.com/HaoZai000/NexioSchedule).

License: GNU Affero General Public License, Version 3 (AGPL-3.0), retained in
[`third-party/notices/NexioSchedule-AGPL-3.0.txt`](third-party/notices/NexioSchedule-AGPL-3.0.txt).
The reference repository was reviewed at commit
`618f29808f1d313ed8b4a437c66967cd38618cb5`. SleepDown's adaptation retains the
radial sampling and denoise passes, and uses its Backdrop 2 per-node shader cache,
full-resolution coordinates and shared glass surface entry point.

## Kyant0/AndroidLiquidGlass

Portions of the liquid glass catalog component code are based on
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

License: Apache License, Version 2.0.

The code has been modified for SleepDown-Schedule to preserve the app's
glass quality controls, fallback behavior, restrained tint values, and local
interaction styling.

## compose-miuix-ui/miuix

The settings and education-import page structure uses components from
[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix), version 0.9.3.

License: Apache License, Version 2.0.

SleepDown-Schedule combines Miuix layout and interaction components with its
existing Kyant liquid-glass surfaces and controls. The Miuix blur module is not
included. The tracked Miuix patches expose surface modifiers, centered dialogs,
content clipping and popup lifecycle hooks used by those combined surfaces.
`patches/miuix-scaffold-underlay.patch` separates the Scaffold page underlay from
its popup host so backdrop consumers do not sample a producer that contains
themselves. The patch base, application order and scope are documented in
`patches/README.md`; the original package names and license headers are retained.

## xingheyuzhuan/shiguang_warehouse

Education-system adapter indexes, YAML configuration and JavaScript resources
under `app/src/main/assets/shiguang_warehouse-main/` are based on
[xingheyuzhuan/shiguang_warehouse](https://github.com/xingheyuzhuan/shiguang_warehouse).

License: MIT License.

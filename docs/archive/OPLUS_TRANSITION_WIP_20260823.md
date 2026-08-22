# Oplus Transition WIP archive — 2026-08-23

This checkpoint preserves the deferred cross-Activity transition work before the
liquid-glass framework and performance work begins.

## Baseline

- Source branch at archive start: `main`
- Source commit: `b0b5c5219fbaef7486c5012f009a16bbceb4007f`
- Remote comparison commit: `6ace41f50d36fe7b0c1cadfdb93f0dfa4c0e809a`
- The local and remote commits have the same tree; the divergence is merge history only.
- Verified unit-test baseline before archiving: 378 tests, 0 failures, 0 errors, 0 skipped.

## Preserved work

- Unified cross-Activity transition route/session/payload/coordinator framework.
- Reflection-isolated ColorOS ViewSeamless backend and Debug-only harness.
- Opaque native-host themes, manifest aliases, capability policy and R8 rules.
- Legacy fallback integration for course management, education import and AI history routes.
- Glass foreground/contrast corrections that were developed in the same working tree.
- Transition, capability, payload cleanup and IME compensation tests.

## Deferred device findings

The checkpoint is evidence, not a completed ColorOS fix. Production native route
allowlists remain empty/default-disabled.

- Home destination CLOSE still ended with a center fade instead of the complete
  Legacy morph returning to the real three-dot button.
- Course detail CLOSE still exposed an empty frame.
- AI history OPEN/CLOSE still exposed empty frames.
- Registration-view, overlay and callback timing attempts did not resolve those results.

Investigation markers are retained as `TODO(OPLUS_DEFERRED_20260823)` in:

- `AiEduImportProgressActivity.kt`
- `ImportUi.kt`
- `transition/OplusSeamlessBackend.kt`
- `transition/TransitionPayload.kt`

Do not resume or expand that investigation unless explicitly requested.

## Archive boundary

The checkpoint intentionally excludes build caches, temporary worktrees, screenshots,
`ui.xml`, promotional assets, the untracked `miuix-local` checkout and all
`SleepDown-Server` content. The pre-existing Git stash is left untouched.

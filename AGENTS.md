# M120 Launcher Project Rules

## Project
This is a lightweight custom Android launcher for a Chinoe AT-M120.

Package:
com.tnyemii.m120launcher

Target device:
- Android 12
- ARMv7 / armeabi-v7a
- 480 x 800 portrait display
- approximately 2 GB RAM
- low-end MediaTek MT6761 hardware

## Development priorities

1. Keep the launcher lightweight and responsive.
2. Preserve existing behavior unless explicitly asked to change it.
3. Prefer small, targeted changes over large rewrites.
4. Avoid unnecessary dependencies.
5. Avoid WebViews unless explicitly requested.
6. Avoid unnecessary animations, background work, polling, and allocations.
7. Do not add features unless explicitly requested.
8. Do not replace working Compose architecture just for stylistic reasons.
9. Do not redesign the UI unless explicitly requested.

## UI

The launcher uses:
- Jetpack Compose
- pastel pink and lavender styling
- kawaii visual elements
- 480 x 800 portrait layout

Keep the existing visual style intact.

## Important existing functionality

The launcher currently supports:
- HOME launcher behavior
- app drawer
- favorites
- favorite reordering
- wallpaper selection
- wallpaper reset
- automatic KAWAIIICON mapping
- manual KAWAIIICON icon selection
- icon search
- custom app icons
- reset custom icons
- app info
- swipe gestures
- physical Home button returning to the launcher home state
- cached launcher icons
- sampled wallpaper loading
- battery/Wi-Fi/time status card
- StickerExplosion decorations

Do not remove or break these features.

## Performance

The target device is low-RAM.

Current launcher memory usage is approximately:
- ~162 MB PSS
- ~99 MB RSS

Graphics/icon memory is more significant than the Kotlin/Java heap.

Do not add:
- System.gc()
- RAM cleaners
- aggressive cache clearing
- unnecessary bitmap duplication
- unnecessary WebViews
- unnecessary background services

Do not optimize based solely on theoretical concerns. Prefer measurable improvements.

## Coding style

Keep code understandable to a beginner Android developer.

When proposing a significant change:
1. Explain what it changes.
2. Explain why it helps.
3. Show the relevant diff or small edit.
4. Do not silently rewrite unrelated code.

Before making a large refactor, ask for permission.

## Build safety

Before changing build configuration:
- inspect the existing Gradle setup
- explain why the change is necessary
- avoid unnecessary dependency upgrades

After code changes, build the project and report any errors.

## Git / changes

Keep changes small and reviewable.

Do not delete files, remove features, or perform broad refactors without explicit permission.

If unsure whether a change is wanted, ask first.
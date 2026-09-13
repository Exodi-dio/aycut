# Aycut Mobile Agent Guide

## Scope and current phase

`mobile/` is aycut's Kotlin Multiplatform mobile application. It is **Phase 1:
an Android video editing engine + shell**. Work only on Android unless a task
explicitly authorizes iOS or desktop work.

Read `README.md` in this directory before changing mobile code. From the
repository root, use `./mobile/gradlew`; from this directory use `./gradlew`.

**Everything builds and tests on GitHub Actions (`mobile-ci.yml`). Never run
Gradle on the local machine** — it is too slow. Push to `main` and read the
workflow run. All lint/build/test verification happens there.

## License policy (read before any dependency or any code)

Distribution is closed-source. The license policy in `README.md` is a hard
constraint, not advice. In short:

- Allowed to import: Apache-2.0, MIT, BSD, ISC, zlib, LGPL (with relinking
  caveats; FFmpeg only `--disable-gpl`).
- Banned: GPL-2.0/3.0, AGPL, and any code or derived design from Shotcut/MLT,
  Kdenlive, OpenShot, and co. See README for the full list.
- The engine is clean-room: never open a GPL editor's source for a design
  answer; design from first principles and docs of allowed libraries.
- If a dependency's license is uncertain, stop and flag it to the user.

## Architecture

```text
androidApp (Android Compose UI, MediaCodec/MediaMuxer, GLES compositor,
            export, MediaStore) -> editor-core (timeline engine)
```

- `editor-core/src/commonMain` contains the timeline model (Sequence, Track,
  Clip), operations (split/trim/move) + undo, time mapping, effects data, and
  the portable project codec. It must not import Android, GLES, or UI APIs.
- `androidApp` owns every Android/GL API and the Compose UI, including
  MediaCodec decode/encode, MediaMuxer MP4 output, the GLES compositor, audio
  mixing, and MediaStore import.
- Keep the dependency direction inward: `androidApp` depends on `editor-core`;
  `editor-core` depends only on its own contracts and multiplatform libraries.
- Playback/export runs in a foreground service; no always-on background work.

## Conventions

- Use the Gradle version catalog in `gradle/libs.versions.toml`. Never hardcode
  versions in module build files.
- SDK policy: `minSdk 26`, `compileSdk 36`, `targetSdk 36`. This is fixed unless
  a task explicitly changes it.
- Package: `com.exodi.aycut` (dev flavor application id `com.exodi.aycut.dev`).
- Strings, icons, themes, accessibility semantics live in Android resources or
  Android UI code, mirroring the airmedy conventions.
- All fixes/features add a test. Host unit tests in the relevant module.
- Never run tests on a physical device or emulator; CI host tests only.

## Workflow

1. Inspect this guide and the module before editing.
2. Add/update tests with the change (`kotlin.test` for editor-core).
3. Push to `main`; `mobile-ci.yml` runs: editor-core host tests,
   androidApp host unit tests, and `assembleDevDebug`. Iterate from CI logs.

## Out of scope until explicitly requested

- iOS implementation and iOS targets.
- Desktop app code.
- Monetization/IAP/ads.
- FFmpeg and other native code (Phase 1 preview/export uses MediaCodec +
  MediaMuxer only).
- Anything GPL-licensed.
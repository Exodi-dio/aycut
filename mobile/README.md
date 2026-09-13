# Aycut — clean-room video editor

a consumer video editor for mobile (and desktop later). **Phase 1: Android
editing engine + shell.**

## License policy — non-negotiable

This project will be distributed closed-source. Every dependency and every line
of code must stay compatible with that.

**Allowed (can be pulled in, can be statically linked, can ship closed):**
- Apache-2.0 (Compose, Material3, Kotlin, coroutines, serialization, Room,
  Media3, Android SDK MediaCodec/MediaMuxer/MediaStore, openh264)
- MIT / BSD-3 / BSD-2 / ISC / zlib
- LGPL-2.1/later (FFmpeg built with `--enable-lgpl`, GStreamer) — LGPL pieces
  are allowed only with the shipping caveats below.

**Banned (never pull in, never port logic from):**
- GPL-2.0 / GPL-3.0 / AGPL, including any code or *derived design* from:
  Shotcut / MLT, Kdenlive, OpenShot, Flowblade, Olive, Pitivi, Cinelerra,
  LiVES, mpv, x264, x265, FFmpeg's GPL builds, ImageMagick (GPL), Blender.
- Any project whose license is uncertain. When in doubt: ask the maintainer,
  ask the human, or don't include it.

### LGPL handling when it ships
- LGPL libraries may be linked only if the app keeps relinking/upgrading
  obligations practical, and the LGPL component must be clearly separated.
- For FFmpeg: build LGPL-only (`--disable-gpl`), follow its LGPL obligations.
- If a license question is not obvious, flag it to the user before writing code.

### Clean-room rule for the engine
- The editing engine (timeline model, time mapping, compositor, mixer, project
  file format) is written from scratch against our own design, not by
  consulting GPL sources. No copying structure/logic from banned projects,
  even paraphrased.

## Current status

- M1 skeleton: repo builds on GitHub Actions (`mobile-ci.yml`), editor-core
  shared module + androidApp shell, package `com.exodi.aycut`.

## Architecture

```text
androidApp (Android Compose UI, MediaCodec pipeline, GLES compositor,
            export service)
    -> editor-core (timeline model, time mapping, operations, project codec)
```

- `editor-core` is pure business logic, platform-neutral, tested on the host.
- `androidApp` owns every Android API: MediaCodec, MediaMuxer, GLES, MediaStore.

## Verify (run on GitHub Actions — do not build on the local machine)

```bash
./gradlew :editor-core:testAndroidHostTest
./gradlew :androidApp:testDevDebugUnitTest
./gradlew :androidApp:assembleDevDebug
```

## Commits

Conventional Commits. All commits co-authored with the project owner identity.

## Milestones

- [x] M1 skeleton + CI
- [ ] M2 timeline model (clips, tracks, time mapping, undo)
- [ ] M3 project persistence (Room + portable snapshot)
- [ ] M4 media I/O (import, decode, thumbnails)
- [ ] M5 GLES compositor + preview
- [ ] M6 timeline UI (Compose)
- [ ] M7 export (H.264/AAC MP4)
- [ ] M8 polish + release
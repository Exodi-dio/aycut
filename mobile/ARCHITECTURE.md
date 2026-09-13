# aycut mobile architecture

North-star design for the editing stack. This file is a durable contract for
every session: read it before changing structure or adding platform work.

## Principle: one engine, many renderers

Aycut is **one editing engine** (`editor-core`) that drives every device. There
is never a separate "mobile engine" and a "pc engine" — that forks features,
doubles bugs, and makes projects drift apart. What differs across devices is
only the rendering/recode surface, not the edit brain.

```text
                    ┌─────────────────────────────────────────────┐
                    │           editor-core (the brain)          │
                    │  timeline model · editing ops · undo        │
                    │  timebase/projection · effects · keyframes  │
                    │  transitions · audio mixdown · color        │
                    │  snap/selection · proxy & render budget     │
                    │  project codec · validator                  │
                    └───────────────┬─────────────────────────────┘
                                    │ RenderResolver / MasterManifest
       ┌────────────────────────────┼──────────────────────────────┐
       ▼                            ▼                              ▼
 Android renderer            iOS renderer                   Desktop renderer
 GLES + MediaCodec           KMP Native + Metal              Compose Desktop + Metal/Vulkan
 Compose UI                  SwiftUI shell                   JVM UI, full power tier
```

Platform lanes are swappable; they consume engine contracts
(`CompositeFrame`, `AudioMixSpec`, `MasterManifest`) and never own editing
logic. The same project file opens on phone, tablet, and workstation.

## Why one engine handles phones and workstations

Phones are weaker but the power problem is I/O and pixels, not logic. We use
**tiered playback, not a reduced engine**:

- **Proxies** — import keeps the original for export; preview uses a small
  lightweight copy. 4K edits smoothly on a phone; export conforms to full
  quality. (`MediaProxy`, `ConformPlanner`)
- **Render budgets** — the engine receives a pure contract
  (`RenderBudget`, computed by `PlaybackProfile` from device class) stating
  "max simultaneous decodes, preview scale, feature flags." It resolves the
  edit within that budget — a trade-off, never a handicap.
- **Feature tiers** — one project everywhere. A device flags "no 8K realtime"
  and preview downscales; the edit data is identical.
- **Cache planning** — `CachePlanner` decides which source windows to warm
  (proxy vs original, TTL) from the budget and the playhead.

## Rules

1. `editor-core` is **platform-free**: no Android/iOS/GL/UI imports, integer
   microseconds only, all math pure and test-first. It serves Android today
   and iOS/desktop later with zero forking.
2. Editing logic lives in the engine; rendering, decode/encode, and UI live in
   the app lanes. Dependency direction is inward (app → core).
3. **No GPL/AGPL, no derived design from Shotcut/MLT/Kdenlive/OpenShot.**
   Clean-room: design from first principles and published standards (SMPTE,
   ITU-R BT specs, EBU R128, common compositing math).
4. Each engine milestone lands as its own CI-green push with tests before app
   changes. New model fields default to current behavior so app code compiles
   unchanged through engine evolution.

## Milestone map

Progress: **E1 ✅** (timebase kernel) · **E2 ✅** (source projection) · E3–E12 pending · M6–M8 pending.

Engine stack (in `editor-core`, test-first):
- E1 timebase kernel · E2 source projection · E3 track/audio · E4 effects +
  keyframes + transitions + `RenderResolver` · E5 pro ops + snap + selection ·
  E6 integrity + migration · E7 profile + proxy · E8 color + compositing ·
  E9 nested sequences/ramps/links · E10 pro editing (markers, 3/4-pt, r/s/s,
  captions, ducking) · E11 performance gates + cache/conform · E12 SDK surface
  + `MasterManifest`.

App lanes (after the engine stack):
- M6 Compose timeline UI · M7 MP4 export (MediaMuxer consuming resolver
  frames + mix) · M8 polish, then GL shaders for E8 color/blend/LUT ·
  then iOS and desktop renderers on the same core.
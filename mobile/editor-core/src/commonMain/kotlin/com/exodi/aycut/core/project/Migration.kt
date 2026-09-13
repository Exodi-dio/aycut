package com.exodi.aycut.core.project

/**
 * One-step upgrade of a decoded snapshot to the current [ProjectCodec.CURRENT_VERSION].
 *
 * The v1 format lacked master gain, playhead, lane types, marker, and most
 * per-clip fields. v2 adds them with defaults that preserve v1 behavior:
 * unity gain, playhead at 0, VIDEO lanes, empty marker list, playRate 1.0,
 * no fades/envelope/effects/transition/link/speed ramp.
 */
object Migration {

    fun upgradeToCurrent(snapshot: ProjectSnapshot): ProjectSnapshot {
        if (snapshot.version == ProjectCodec.CURRENT_VERSION) return snapshot
        if (snapshot.version > ProjectCodec.CURRENT_VERSION) {
            throw ProjectFormatException("cannot downgrade snapshot v${snapshot.version}")
        }
        return snapshot.copy(
            version = ProjectCodec.CURRENT_VERSION,
            // v1 defaults (already applied by the v2 model defaults).
        )
    }
}
package com.exodi.aycut.core.export

import com.exodi.aycut.core.model.Sequence
import com.exodi.aycut.core.profile.Profile

/**
 * Stateless description of one export: what to render and where it should land.
 * Pure data — [assemble] produces the concrete [MasterManifest] from a
 * sequence and a profile, so the exporter never needs the editor UI.
 */
data class ExportRequest(
    val profileName: String,
    val sourceSequence: Sequence,
    val outputFileName: String,
)

/** The concrete export plan the renderer consumes. */
data class MasterManifest(
    val sequence: Sequence,
    val profile: Profile,
    val outputFileName: String,
    val frameCount: Long,
) {
    val videoBitrate: Int get() = profile.videoBitrate
    val audioBitrate: Int get() = profile.audioBitrate
    val width: Int get() = profile.width
    val height: Int get() = profile.height
    val frameRate: Double get() = profile.frameRate
}

/** Assembles an export plan from a sequence and a profile. */
object ExportPlanner {

    fun assemble(sequence: Sequence, profile: Profile, outputFileName: String): MasterManifest =
        MasterManifest(
            sequence = sequence,
            profile = profile,
            outputFileName = outputFileName,
            frameCount = sequence.frameCount,
        )

    fun assemble(request: ExportRequest): MasterManifest = assemble(
        sequence = request.sourceSequence,
        profile = Profile.forSequence(request.sourceSequence, request.sourceSequence.frameRate),
        outputFileName = request.outputFileName,
    )
}
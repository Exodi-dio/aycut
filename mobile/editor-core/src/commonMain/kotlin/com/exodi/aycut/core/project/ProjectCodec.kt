package com.exodi.aycut.core.project

import com.exodi.aycut.core.model.Sequence
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Thrown when a snapshot is malformed, unsupported, or structurally invalid. */
class ProjectFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/**
 * Encodes and decodes [ProjectSnapshot]s.
 *
 * Encoding is strict (current version). Decoding accepts unknown JSON keys
 * for forward compatibility and validates structural invariants before the
 * snapshot is allowed to become a [Sequence] (see [decodeToSequence]).
 */
object ProjectCodec {

    const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(snapshot: ProjectSnapshot): String = json.encodeToString(snapshot)

    fun encode(sequence: Sequence, name: String): String = encode(sequence.toSnapshot(name))

    fun decodeSnapshot(jsonText: String): ProjectSnapshot = try {
        json.decodeFromString<ProjectSnapshot>(jsonText)
    } catch (e: SerializationException) {
        throw ProjectFormatException("malformed project snapshot", e)
    }

    /** Decode and fully validate a snapshot into an editable [Sequence]. */
    fun decodeToSequence(jsonText: String): Sequence {
        val snapshot = decodeSnapshot(jsonText)
        if (snapshot.version > CURRENT_VERSION) {
            throw ProjectFormatException(
                "snapshot version ${snapshot.version} is newer than supported $CURRENT_VERSION",
            )
        }
        return try {
            snapshot.toSequence()
        } catch (e: RuntimeException) {
            // Structural validation rejects via require (IAE) and check (ISE).
            throw ProjectFormatException("structurally invalid snapshot", e)
        }
    }
}
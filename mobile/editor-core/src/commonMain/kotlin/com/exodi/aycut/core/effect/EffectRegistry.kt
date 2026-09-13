package com.exodi.aycut.core.effect

/**
 * Describes one built-in or third-party effect: its ID, human label, and
 * the parameter schema the renderer and the timeline UI need.
 *
 * All in-memory; the "registry" is a static map owned by the engine core
 * so the UI can enumerate effects and the renderer can validate param names
 * before they reach the GLES pipeline.
 */
enum class ParamSpec { NUM, BOOL, COLOR, POINT }

data class EffectDescriptor(
    val id: EffectId,
    val label: String,
    val params: Map<String, ParamSpec>,
) {
    init {
        require(label.isNotBlank()) { "effect label must not be blank" }
    }
}

/**
 * The engine-internal registry. Initially contains the two E4 core effects
 * plus a few planned placeholders that the compose/UI layer can discover
 * without hard-coding effect IDs.
 */
object EffectRegistry {

    private val entries = mutableMapOf<EffectId, EffectDescriptor>()

    init {
        register(
            EffectDescriptor(
                id = CoreEffectIds.OPACITY_ID,
                label = "Opacity",
                params = mapOf("opacity" to ParamSpec.NUM),
            ),
        )
        register(
            EffectDescriptor(
                id = CoreEffectIds.TRANSFORM_ID,
                label = "Transform",
                params = mapOf(
                    "rotation" to ParamSpec.NUM,
                    "scaleX" to ParamSpec.NUM,
                    "scaleY" to ParamSpec.NUM,
                    "pivotX" to ParamSpec.NUM,
                    "pivotY" to ParamSpec.NUM,
                ),
            ),
        )
    }

    fun register(descriptor: EffectDescriptor) {
        entries[descriptor.id] = descriptor
    }

    fun descriptor(id: EffectId): EffectDescriptor? = entries[id]
    fun all(): List<EffectDescriptor> = entries.values.toList()
}
package com.exodi.aycut.core.api

import com.exodi.aycut.core.edit.EditCommand
import com.exodi.aycut.core.edit.SequenceEditor
import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence

/**
 * The engine's public facade. Keeps the timeline model and edit machinery
 * behind one surface so the Compose UI (and tests) always talk to the same
 * entry point. The facade is stateful: it wraps a [SequenceEditor] plus the
 * playhead and exposes only intent-level operations.
 */
interface EditorApi {

    val sequence: Sequence
    var playhead: Micros

    fun perform(command: EditCommand)
    fun undo(): Boolean
    fun redo(): Boolean

    fun insertClip(clipId: String, mediaId: String, atMicros: Micros)
    fun rippleDeleteAt(micros: Micros)
}

/** In-memory implementation of the facade. */
class DefaultEditorApi(initial: Sequence = Sequence.createEmpty()) : EditorApi {

    private val editor = SequenceEditor(initial)

    override val sequence: Sequence get() = editor.sequence

    override var playhead: Micros
        get() = editor.playhead
        set(value) = editor.seek(value)

    override fun perform(command: EditCommand) = editor.perform(command)
    override fun undo() = editor.undo()
    override fun redo() = editor.redo()

    override fun insertClip(clipId: String, mediaId: String, atMicros: Micros) {
        // Placeholder wiring: full insert ops land with E10 Select/Replaces.
        // Kept out of imports so this remains a thin pure delegation layer.
        seek(atMicros)
    }

    override fun rippleDeleteAt(micros: Micros) {
        // Placeholder wiring: full ripple ops land with E5 pro ops.
        seek(micros)
    }

    private fun seek(micros: Micros) = editor.seek(micros)
}
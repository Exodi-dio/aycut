package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.Micros
import com.exodi.aycut.core.model.Sequence

/**
 * Stateful holder for an editable sequence: current state plus undo/redo
 * stacks and the playhead position.
 *
 * `perform` applies a command and pushes it to the undo stack; the redo
 * stack is cleared by any new edit. `undo`/`redo` walk the stacks by
 * applying inverse / original commands, keeping commands immutable on the
 * stacks.
 */
class SequenceEditor(
    initial: Sequence = Sequence.createEmpty(),
    /** Bounded undo: older commands are dropped once the stack exceeds this. */
    val undoLimit: Int = DEFAULT_UNDO_LIMIT,
) {
    var sequence: Sequence = initial
        private set

    var playhead: Micros = 0L
        private set

    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()

    init {
        require(undoLimit >= 1) { "undoLimit must be at least 1, was $undoLimit" }
    }

    fun perform(command: EditCommand) {
        sequence = command.apply(sequence)
        undoStack.addLast(command)
        while (undoStack.size > undoLimit) undoStack.removeFirst()
        redoStack.clear()
        clampPlayhead()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        sequence = command.invert().apply(sequence)
        redoStack.addLast(command)
        clampPlayhead()
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        sequence = command.apply(sequence)
        undoStack.addLast(command)
        clampPlayhead()
        return true
    }

    fun seek(to: Micros) {
        playhead = to.coerceIn(0L, sequence.durationMicros)
    }

    private fun clampPlayhead() {
        playhead = playhead.coerceIn(0L, sequence.durationMicros)
    }

    companion object {
        const val DEFAULT_UNDO_LIMIT = 100
    }
}
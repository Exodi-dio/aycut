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
) {
    var sequence: Sequence = initial
        private set

    var playhead: Micros = 0L
        private set

    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()

    fun perform(command: EditCommand) {
        sequence = command.apply(sequence)
        undoStack.addLast(command)
        redoStack.clear()
        clampPlayhead()
    }

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
}
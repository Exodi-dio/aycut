package com.exodi.aycut.core.edit

import com.exodi.aycut.core.model.Sequence

/**
 * An undoable edit on a [Sequence].
 *
 * `apply` is absolute (given a sequence it returns the edited one), and
 * `invert` yields the inverse command, so `command.invert().apply(command.apply(s))`
 * restores `s` exactly. Commands may capture clip state on first apply so the
 * inverse can rebuild the pre-edit state.
 */
interface EditCommand {
    fun apply(sequence: Sequence): Sequence

    fun invert(): EditCommand
}
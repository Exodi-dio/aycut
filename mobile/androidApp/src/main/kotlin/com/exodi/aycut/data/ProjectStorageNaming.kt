package com.exodi.aycut.data

import java.util.UUID

/**
 * Contract between raw string ids and on-disk file names.
 *
 * Kept dependency-free (no Android types) so it is host-unit-testable: a
 * project id is a UUID slug and must never contain path separators or dots
 * that could escape the project directory.
 */
internal object ProjectStorageNaming {

    internal val ID_REGEX = Regex("[A-Za-z0-9_\\-]+")

    fun newId(): String = UUID.randomUUID().toString()

    fun requireValidId(id: String) {
        require(id.isNotEmpty()) { "project id must not be empty" }
        require(ID_REGEX.matches(id)) { "project id contains unsafe characters: $id" }
    }

    fun fileName(id: String): String {
        requireValidId(id)
        return "$id.json"
    }

    fun idFromFileName(fileName: String): String? {
        if (!fileName.endsWith(".json")) return null
        val id = fileName.removeSuffix(".json")
        if (!ID_REGEX.matches(id)) return null
        return id
    }
}
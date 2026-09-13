package com.exodi.aycut.data

import android.content.Context
import com.exodi.aycut.core.project.ProjectCodec
import com.exodi.aycut.core.project.ProjectSnapshot
import java.io.File

/** Lightweight metadata for the project picker. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAtMillis: Long,
)

/**
 * Document-style project persistence: one JSON snapshot per project, stored
 * under app-private storage. No database; queries are file listings.
 *
 * All parsing goes through [ProjectCodec] so on-disk data is always validated
 * before it becomes a [com.exodi.aycut.core.model.Sequence].
 */
class ProjectStorage(context: Context) {

    private val root: File = File(context.filesDir, "aycut_projects").apply { mkdirs() }

    fun save(snapshot: ProjectSnapshot, id: String = ProjectStorageNaming.newId()): String {
        val file = File(root, ProjectStorageNaming.fileName(id))
        file.writeText(ProjectCodec.encode(snapshot))
        return id
    }

    fun loadSnapshot(id: String): ProjectSnapshot? {
        val file = File(root, ProjectStorageNaming.fileName(id))
        if (!file.exists()) return null
        return ProjectCodec.decodeSnapshot(file.readText())
    }

    fun delete(id: String): Boolean {
        val file = File(root, ProjectStorageNaming.fileName(id))
        return file.exists() && file.delete()
    }

    fun list(): List<ProjectSummary> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isFile }
            .mapNotNull { file ->
                val id = ProjectStorageNaming.idFromFileName(file.name) ?: return@mapNotNull null
                val snapshot = runCatching { ProjectCodec.decodeSnapshot(file.readText()) }
                    .getOrNull() ?: return@mapNotNull null
                ProjectSummary(id, snapshot.name, file.lastModified())
            }
            .sortedByDescending { it.updatedAtMillis }
}
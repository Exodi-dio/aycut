package com.exodi.aycut

import com.exodi.aycut.data.ProjectStorageNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectStorageNamingTest {

    @Test
    fun `new id is a valid filename slug`() {
        assertTrue(ProjectStorageNaming.ID_REGEX.matches(ProjectStorageNaming.newId()))
    }

    @Test
    fun `file name round trips through idFromFileName`() {
        val id = ProjectStorageNaming.newId()
        assertEquals(id, ProjectStorageNaming.idFromFileName(ProjectStorageNaming.fileName(id)))
    }

    @Test
    fun `unsafe ids are rejected`() {
        val unsafeIds = listOf("", "../escape", "a/b", "x.y", "a b", "..")
        for (unsafe in unsafeIds) {
            try {
                ProjectStorageNaming.requireValidId(unsafe)
                throw AssertionError("expected rejection for '$unsafe'")
            } catch (expected: IllegalArgumentException) {
                // pass
            }
        }
    }

    @Test
    fun `non-index files are ignored`() {
        assertNull(ProjectStorageNaming.idFromFileName("readme.txt"))
        assertNull(ProjectStorageNaming.idFromFileName(".json"))
        assertNull(ProjectStorageNaming.idFromFileName("a/b.json"))
    }
}
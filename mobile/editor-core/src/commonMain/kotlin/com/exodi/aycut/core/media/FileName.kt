package com.exodi.aycut.core.media

/**
 * Reduce an arbitrary media display name to something safe to store in
 * app-private storage: only letters, digits, dash, underscore and dot, no
 * path separators or leading/trailing junk.
 */
fun sanitizeFileName(name: String): String {
    val sanitized = buildString(name.length) {
        for (c in name) {
            append(if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.') c else '_')
        }
    }.trim('_', ' ', '.')

    return if (sanitized.isBlank()) {
        "media"
    } else {
        sanitized
    }
}
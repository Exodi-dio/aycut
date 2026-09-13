package com.exodi.aycut.core.model

/** A named position marker on the sequence timeline (always sorted). */
data class Marker(
    val label: String,
    val at: Micros,
) {
    init {
        require(label.isNotBlank()) { "marker label must not be blank" }
        require(at >= 0L) { "marker time must be non-negative" }
    }
}

/** Sequence markers, kept sorted by time (stable: equal times keep order). */
fun Sequence.withMarker(marker: Marker): Sequence =
    copy(markers = (markers + marker).sortedBy { it.at })

fun Sequence.withoutMarker(at: Micros): Sequence =
    copy(markers = markers.filterNot { it.at == at })
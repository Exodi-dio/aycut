package com.exodi.aycut.core.media

import com.exodi.aycut.core.model.MediaId

/**
 * The set of assets available to place on the timeline.
 *
 * Immutable snapshots only (a register returns a new registry), so concurrent
 * UI reads are always safe.
 */
data class MediaAssetRegistry(
    internal val assets: Map<MediaId, MediaAsset> = emptyMap(),
) {

    fun asset(id: MediaId): MediaAsset? = assets[id]

    fun register(asset: MediaAsset): MediaAssetRegistry {
        require(asset.durationMicros > 0L) { "cannot register a zero-length asset" }
        return copy(assets = assets + (asset.id to asset))
    }

    fun remove(id: MediaId): MediaAssetRegistry = copy(assets = assets - id)

    fun all(): List<MediaAsset> = assets.values.sortedBy { it.displayName.lowercase() }

    val size: Int
        get() = assets.size

    companion object {
        val EMPTY = MediaAssetRegistry()
    }
}
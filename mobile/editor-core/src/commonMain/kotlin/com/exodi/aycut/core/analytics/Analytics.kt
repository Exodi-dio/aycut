package com.exodi.aycut.core.analytics

/**
 * Analytics events emitted by the engine. Emitted onto the sink (a no-op
 * at the core boundary) so the platform layer can decide what to log.
 */
sealed interface AnalyticsEvent {
    val timestampMs: Long

    data class AppStart(override val timestampMs: Long) : AnalyticsEvent
    data class ProjectOpen(override val timestampMs: Long, val clipCount: Int) : AnalyticsEvent
    data class ProjectSave(override val timestampMs: Long, val durationMicros: Long) : AnalyticsEvent
    data class ExportStart(override val timestampMs: Long, val width: Int, val height: Int) : AnalyticsEvent
    data class ExportComplete(override val timestampMs: Long, val durationMs: Long) : AnalyticsEvent
    data class Action(override val timestampMs: Long, val name: String) : AnalyticsEvent
}

/** Analytics sink. The platform layer implements this; the engine core never calls it directly. */
fun interface AnalyticsSink {
    fun emit(event: AnalyticsEvent)
}

/** No-op sink used in tests and at the core boundary. */
object NoOpAnalyticsSink : AnalyticsSink {
    override fun emit(event: AnalyticsEvent) { /* no-op */ }
}
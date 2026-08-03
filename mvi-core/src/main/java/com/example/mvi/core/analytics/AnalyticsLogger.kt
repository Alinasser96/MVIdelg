package com.example.mvi.core.analytics

/** One tracked interaction, always paired with the screen it happened on. */
sealed interface AnalyticsEvent {
    val eventId: String
    val screenId: String

    data class Button(override val eventId: String, override val screenId: String) : AnalyticsEvent
    data class Field(override val eventId: String, override val screenId: String) : AnalyticsEvent
}

/**
 * Where analytics go. `LoggingPluginImpl` calls this off the main thread.
 */
fun interface AnalyticsLogger {
    suspend fun log(event: AnalyticsEvent)
}

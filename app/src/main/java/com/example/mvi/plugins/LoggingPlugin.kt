package com.example.mvi.plugins

import androidx.lifecycle.ViewModel
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.platform.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One tracked interaction, always paired with the screen it happened on. */
sealed interface AnalyticsEvent {
    val eventId: String
    val screenId: String

    data class Button(override val eventId: String, override val screenId: String) : AnalyticsEvent
    data class Field(override val eventId: String, override val screenId: String) : AnalyticsEvent
}

/** Where analytics go. [LoggingPlugin] calls this off the main thread. */
fun interface AnalyticsLogger {
    suspend fun log(event: AnalyticsEvent)
}

/**
 * Example plugin: screen analytics.
 *
 * The one that shows why constructor dependencies beat a shared bag. Everything it needs —
 * the logger, the dispatchers, and the screen id every event is attributed to — arrives
 * up front, so there is no configure-me-first step and no way to log an event with a
 * blank screen id.
 *
 * It also uses [onCreate], because it needs the ViewModel's scope to log without blocking
 * the intent that triggered it.
 */
class LoggingPlugin(
    private val analyticsLogger: AnalyticsLogger,
    private val dispatchers: DispatcherProvider,
    private val screenId: String,
) : MVIPlugin {

    private lateinit var scope: CoroutineScope

    override fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope) {
        scope = viewModelScope
    }

    fun logButtonEvent(event: String) {
        logEvent(AnalyticsEvent.Button(event, screenId))
    }

    fun logFieldEvent(event: String) {
        logEvent(AnalyticsEvent.Field(event, screenId))
    }

    private fun logEvent(event: AnalyticsEvent) {
        scope.launch(dispatchers.io) {
            analyticsLogger.log(event)
        }
    }
}

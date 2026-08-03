package com.example.mvi.core.plugins

import androidx.lifecycle.ViewModel
import com.example.mvi.core.analytics.AnalyticsEvent
import com.example.mvi.core.analytics.AnalyticsLogger
import com.example.mvi.core.helpers.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plugin for screen analytics.
 *
 * Requires a one-time [configure] call naming the screen, so every event is attributed
 * without each call site repeating the screen id. The [check] turns "we shipped events
 * with a blank screen id" into a crash in development.
 */
class LoggingPluginImpl : MVIPlugin {

    private lateinit var scope: CoroutineScope
    private lateinit var analyticsLogger: AnalyticsLogger
    private lateinit var dispatcher: DispatcherProvider

    private var screenId: String = ""
    private var configured: Boolean = false

    override fun onCreate(
        viewModel: ViewModel,
        viewModelScope: CoroutineScope,
        dependencies: MviPluginDependencies,
    ) {
        scope = viewModelScope
        analyticsLogger = dependencies.analyticsLogger
        dispatcher = dependencies.dispatcherProvider
    }

    fun configure(screenId: String) {
        this.screenId = screenId
        configured = true
    }

    fun logButtonEvent(event: String) {
        check(configured) { "Call configureLogging(screenId) before logging events" }
        logEvent(AnalyticsEvent.Button(event, screenId))
    }

    fun logFieldEvent(event: String) {
        check(configured) { "Call configureLogging(screenId) before logging events" }
        logEvent(AnalyticsEvent.Field(event, screenId))
    }

    private fun logEvent(event: AnalyticsEvent) {
        scope.launch(dispatcher.io) {
            analyticsLogger.log(event)
        }
    }
}

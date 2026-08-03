package com.example.mvi.core

import com.example.mvi.core.analytics.AnalyticsEvent
import com.example.mvi.core.analytics.AnalyticsLogger
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.navigation.Destination
import com.example.mvi.core.navigation.Navigator
import com.example.mvi.core.plugins.MviPluginDependencies
import kotlinx.coroutines.CoroutineDispatcher

/** Every layer takes its dispatchers from here, so one test dispatcher drives everything. */
class TestDispatcherProvider(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

class RecordingAnalyticsLogger : AnalyticsLogger {
    val events = mutableListOf<AnalyticsEvent>()
    override suspend fun log(event: AnalyticsEvent) {
        events += event
    }
}

fun testPluginDependencies(
    dispatcher: CoroutineDispatcher,
    navigator: Navigator = ChannelNavigator(),
    analyticsLogger: AnalyticsLogger = RecordingAnalyticsLogger(),
) = MviPluginDependencies(
    navigator = navigator,
    analyticsLogger = analyticsLogger,
    dispatcherProvider = TestDispatcherProvider(dispatcher),
)

data class TestDestination(override val route: String) : Destination

package com.example.mvi.di

import android.util.Log
import com.example.mvi.core.analytics.AnalyticsEvent
import com.example.mvi.core.analytics.AnalyticsLogger
import com.example.mvi.core.helpers.DefaultDispatcherProvider
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.navigation.Navigator
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.data.FakeUserRepository
import com.example.mvi.data.UserRepository

/**
 * Deliberately the dumbest possible dependency injection.
 *
 * In production this is Hilt: `MviPluginDependencies` is `@Inject`-constructed and
 * field-injected into the base ViewModel. The blueprint keeps it manual so the plugin
 * architecture stays visible instead of being buried under generated components — the
 * shape of what gets injected is identical either way.
 */
object ServiceLocator {

    val userRepository: UserRepository by lazy { FakeUserRepository() }

    /** App-wide, so the NavHost and every ViewModel share one command stream. */
    val navigator: Navigator by lazy { ChannelNavigator() }

    val dispatcherProvider: DispatcherProvider by lazy { DefaultDispatcherProvider() }

    private val analyticsLogger: AnalyticsLogger = AnalyticsLogger { event ->
        val kind = when (event) {
            is AnalyticsEvent.Button -> "button"
            is AnalyticsEvent.Field -> "field"
        }
        Log.d("Analytics", "${event.screenId}/$kind/${event.eventId}")
    }

    /** The one bag every installed plugin receives in `onCreate`. */
    val pluginDependencies: MviPluginDependencies by lazy {
        MviPluginDependencies(
            navigator = navigator,
            analyticsLogger = analyticsLogger,
            dispatcherProvider = dispatcherProvider,
        )
    }
}

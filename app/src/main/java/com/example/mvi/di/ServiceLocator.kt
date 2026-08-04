package com.example.mvi.di

import android.util.Log
import com.example.mvi.data.FakeUserRepository
import com.example.mvi.data.UserRepository
import com.example.mvi.platform.DefaultDispatcherProvider
import com.example.mvi.platform.DispatcherProvider
import com.example.mvi.plugins.AnalyticsEvent
import com.example.mvi.plugins.AnalyticsLogger
import com.example.mvi.plugins.ChannelNavigator
import com.example.mvi.plugins.Navigator

/**
 * Deliberately the dumbest possible dependency injection.
 *
 * Plugins take their dependencies in their own constructors, so this is where those
 * dependencies come from. Swapping it for Hilt or Koin changes this file and the
 * ViewModel factories — `:mvi-core` neither knows nor cares.
 */
object ServiceLocator {

    val userRepository: UserRepository by lazy { FakeUserRepository() }

    /** App-wide, so the NavHost and every NavigationPlugin share one command stream. */
    val navigator: Navigator by lazy { ChannelNavigator() }

    val dispatcherProvider: DispatcherProvider by lazy { DefaultDispatcherProvider() }

    val analyticsLogger: AnalyticsLogger = AnalyticsLogger { event ->
        val kind = when (event) {
            is AnalyticsEvent.Button -> "button"
            is AnalyticsEvent.Field -> "field"
        }
        Log.d("Analytics", "${event.screenId}/$kind/${event.eventId}")
    }
}

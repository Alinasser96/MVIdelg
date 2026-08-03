package com.example.mvi.core.plugins

import com.example.mvi.core.analytics.AnalyticsLogger
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.navigation.Navigator

/**
 * Everything any plugin might need, in one bag handed to [MVIPlugin.onCreate].
 *
 * One bag rather than per-plugin constructor arguments is what keeps installation
 * automatic: the base class can build any plugin without knowing what it depends on, so
 * adding a plugin never changes a ViewModel's constructor.
 *
 * In a Hilt project this is `@Inject`-constructed and field-injected into the base class.
 * The blueprint has no DI framework, so it is a constructor parameter instead — the only
 * deliberate difference from the production shape.
 */
class MviPluginDependencies(
    val navigator: Navigator,
    val analyticsLogger: AnalyticsLogger,
    val dispatcherProvider: DispatcherProvider,
)

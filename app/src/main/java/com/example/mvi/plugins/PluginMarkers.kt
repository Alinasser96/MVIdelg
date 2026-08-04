package com.example.mvi.plugins

import com.example.mvi.core.plugins.PluggableMviViewModel
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.core.plugins.requirePlugin
import com.example.mvi.di.ServiceLocator

/**
 * Markers and accessors for this app's plugins.
 *
 * Each pair is the pattern from `:mvi-core`'s `requirePlugin()` docs, applied four times.
 * A marker is an empty interface; the accessor extends it, so `loading` only resolves
 * inside a class that declared [HasLoadingPlugin]. A screen that never asked for loading
 * cannot name it — a compile error, not a null.
 *
 * None of this lives in the library. Your app writes its own set.
 */

interface HasLoadingPlugin

interface HasErrorPlugin

interface HasNavigationPlugin

interface HasLoggingPlugin

val HasLoadingPlugin.loading: LoadingPlugin
    get() = (this as PluggableMviViewModel<*, *, *>).requirePlugin()

val HasErrorPlugin.errors: ErrorPlugin
    get() = (this as PluggableMviViewModel<*, *, *>).requirePlugin()

val HasNavigationPlugin.navigation: NavigationPlugin
    get() = (this as PluggableMviViewModel<*, *, *>).requirePlugin()

val HasLoggingPlugin.logging: LoggingPlugin
    get() = (this as PluggableMviViewModel<*, *, *>).requirePlugin()

/**
 * The set most screens in this app install.
 *
 * A convenience, not a framework feature — it just saves repeating four constructors per
 * ViewModel. **Keep it in step with the markers you declare:** the compiler stops you
 * using an accessor without its marker, but nothing checks that a marker you declared has
 * its plugin in the list. That mismatch throws from `requirePlugin()` with the plugin's
 * name, which is what `CustomPluginTest` pins down.
 */
fun standardPlugins(screenId: String): List<MVIPlugin> = listOf(
    LoadingPlugin(),
    ErrorPlugin(),
    NavigationPlugin(ServiceLocator.navigator),
    LoggingPlugin(
        analyticsLogger = ServiceLocator.analyticsLogger,
        dispatchers = ServiceLocator.dispatcherProvider,
        screenId = screenId,
    ),
)

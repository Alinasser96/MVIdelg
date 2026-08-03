package com.example.mvi.core.plugins

import com.example.mvi.core.MviViewModel

/**
 * The accessors that make an installed plugin reachable.
 *
 * Each is an extension property on a *marker*, so `loading` only resolves inside a class
 * that declared [HasLoadingPlugin]. Forgetting the marker is a compile error, not a null
 * at runtime — the `error(...)` below is unreachable in practice and exists only to
 * document the invariant the base class upholds.
 *
 * This pairing of marker + accessor is the trick worth copying: it gives the ergonomics
 * of a method on the base class (`loading.show()`) with none of the cost, because a
 * screen that never asked for loading never sees it.
 */
val HasLoadingPlugin.loading: LoadingPluginImpl
    get() = (this as MviViewModel<*, *, *>)._loadingPlugin
        ?: error("LoadingPlugin should be installed when HasLoadingPlugin is implemented")

val HasErrorPlugin.errors: ErrorPluginImpl
    get() = (this as MviViewModel<*, *, *>)._errorPlugin
        ?: error("ErrorPlugin should be installed when HasErrorPlugin is implemented")

val HasNavigationPlugin.navigation: NavigationPluginImpl
    get() = (this as MviViewModel<*, *, *>)._navigationPlugin
        ?: error("NavigationPlugin should be installed when HasNavigationPlugin is implemented")

val HasLoggingPlugin.logging: LoggingPluginImpl
    get() = (this as MviViewModel<*, *, *>)._loggingPlugin
        ?: error("LoggingPlugin should be installed when HasLoggingPlugin is implemented")

fun HasLoggingPlugin.configureLogging(screenId: String) {
    logging.configure(screenId)
}

/**
 * Looks up a plugin you supplied through `additionalPlugins`.
 *
 * Write the same marker + accessor pair the built-in four use, from any module:
 *
 * ```
 * interface HasPaginationPlugin
 *
 * val HasPaginationPlugin.pagination: PaginationPlugin
 *     get() = (this as MviViewModel<*, *, *>).requirePlugin()
 * ```
 *
 * The only difference from a built-in is that the base cannot construct your plugin for
 * you, so the ViewModel also passes it to `additionalPlugins`.
 *
 * @throws IllegalStateException if the plugin was never installed — which for a
 *   marker-backed accessor means the marker and the `additionalPlugins` entry disagree.
 */
inline fun <reified P : MVIPlugin> MviViewModel<*, *, *>.requirePlugin(): P =
    pluginOrNull(P::class)
        ?: error("${P::class.simpleName} is not installed. Pass it in additionalPlugins.")

/** Non-throwing variant, for a plugin that is genuinely optional. */
inline fun <reified P : MVIPlugin> MviViewModel<*, *, *>.pluginOrNull(): P? =
    pluginOrNull(P::class)

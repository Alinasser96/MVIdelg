package com.example.mvi.core.plugins

/**
 * Looks up an installed plugin so you can give it a typed accessor.
 *
 * The pattern to copy — three pieces, all in your own module:
 *
 * ```
 * class LoadingPlugin : MVIPlugin { ... }                 // 1. the capability
 *
 * interface HasLoadingPlugin                              // 2. the marker
 *
 * val HasLoadingPlugin.loading: LoadingPlugin             // 3. the accessor
 *     get() = (this as PluggableMviViewModel<*, *, *>).requirePlugin()
 * ```
 *
 * Because the accessor extends the marker, `loading` only resolves inside a class that
 * declared that marker. A screen that never asked for the capability cannot name it — a
 * compile error rather than a null.
 *
 * @throws IllegalStateException if the plugin was never installed, which for a
 *   marker-backed accessor means the marker and the `plugins` list disagree.
 */
inline fun <reified P : MVIPlugin> PluggableMviViewModel<*, *, *>.requirePlugin(): P =
    pluginOrNull(P::class)
        ?: error("${P::class.simpleName} is not installed. Add it to the plugins list.")

/** Non-throwing variant, for a plugin that is genuinely optional. */
inline fun <reified P : MVIPlugin> PluggableMviViewModel<*, *, *>.pluginOrNull(): P? =
    pluginOrNull(P::class)

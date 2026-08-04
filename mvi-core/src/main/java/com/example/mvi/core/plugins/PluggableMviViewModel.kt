package com.example.mvi.core.plugins

import androidx.lifecycle.viewModelScope
import com.example.mvi.core.Effect
import com.example.mvi.core.Intent
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.ViewState
import kotlin.reflect.KClass

/**
 * [MviViewModel] with the seams wired to a list of plugins.
 *
 * This class is the *only* place the two ideas meet, and it is deliberately tiny: it
 * overrides the four seams the pure base declares and forwards each to every installed
 * plugin. Nothing about the MVI loop changes.
 *
 * Extend this when a screen needs installable capabilities; extend [MviViewModel]
 * directly when it does not, and never import this package at all.
 *
 * ```
 * class ProfileViewModel(
 *     private val repository: UserRepository,
 * ) : PluggableMviViewModel<ProfileViewState, ProfileIntent, ProfileEffect>(
 *     listOf(LoadingPlugin(), ErrorPlugin()),
 * ),
 *     HasLoadingPlugin,
 *     HasErrorPlugin {
 *
 *     override suspend fun handleIntent(intent: ProfileIntent) {
 *         loading.withLoading { ... }
 *     }
 * }
 * ```
 *
 * @param plugins the capabilities to install, in the order they should see intents.
 */
abstract class PluggableMviViewModel<T : ViewState, I : Intent, E : Effect>(
    private val plugins: List<MVIPlugin>,
) : MviViewModel<T, I, E>() {

    init {
        // Safe here, and only here: this init block runs after `plugins` is assigned,
        // whereas anything the pure base called from *its* init would still see it null.
        plugins.forEach { plugin -> plugin.onCreate(this, viewModelScope) }
    }

    /**
     * Every plugin sees every intent; any of them may claim it.
     *
     * Mapped before reduced on purpose: `any { }` short-circuits, which would make
     * whether a plugin observes an intent depend on its position in the list.
     */
    final override fun interceptIntent(intent: I): Boolean =
        plugins.map { plugin -> plugin.onIntent(intent) }.any { it }

    final override fun afterStateUpdate(oldState: T, newState: T) {
        plugins.forEach { it.onStateChanged(oldState, newState) }
    }

    final override fun afterEffect(effect: E) {
        plugins.forEach { it.onEffectEmitted(effect) }
    }

    final override fun onDispose() {
        plugins.forEach { it.onCleared() }
    }

    /**
     * Finds an installed plugin by type, or null.
     *
     * This is what lets a typed accessor be written for a plugin — see [requirePlugin].
     */
    @Suppress("UNCHECKED_CAST")
    fun <P : MVIPlugin> pluginOrNull(type: KClass<P>): P? =
        plugins.firstOrNull { type.isInstance(it) } as P?
}

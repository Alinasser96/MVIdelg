package com.example.mvi.core.plugins

import androidx.lifecycle.ViewModel
import com.example.mvi.core.Effect
import com.example.mvi.core.Intent
import com.example.mvi.core.ViewState
import kotlinx.coroutines.CoroutineScope

/**
 * Base plugin interface — the extension point of the whole architecture.
 *
 * A plugin is a self-contained capability (loading, errors, navigation, analytics) that
 * observes the MVI loop through these five hooks. Every method has a default, so a plugin
 * implements only the hooks it cares about.
 *
 * This is what "delegation" means here: [com.example.mvi.core.MviViewModel] keeps the MVI
 * loop itself, and delegates every *cross-cutting concern* to plugins it installs. Adding
 * a capability to the app never means adding a method to the base class.
 */
interface MVIPlugin {

    /**
     * Called once, when the ViewModel's state is first observed. The plugin receives the
     * ViewModel, its scope, and the shared [MviPluginDependencies] bag — which is how a
     * plugin gets a navigator or an analytics logger without every ViewModel having to
     * inject one it may not use.
     */
    fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope, dependencies: MviPluginDependencies) {}

    /**
     * Gives the plugin first look at every intent.
     *
     * Return `true` to mark the intent **handled**, which stops it reaching the
     * ViewModel's `handleIntent`. That is what lets a plugin own a whole class of intents
     * — a retry, a back press — with no branch in any feature. Return `false` to observe
     * and pass it on.
     */
    fun onIntent(intent: Intent): Boolean = false

    fun onStateChanged(oldState: ViewState, newState: ViewState) {}

    fun onEffectEmitted(effect: Effect) {}

    fun onCleared() {}
}

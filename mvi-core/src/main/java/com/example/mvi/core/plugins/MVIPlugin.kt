package com.example.mvi.core.plugins

import androidx.lifecycle.ViewModel
import com.example.mvi.core.Effect
import com.example.mvi.core.Intent
import com.example.mvi.core.ViewState
import kotlinx.coroutines.CoroutineScope

/**
 * A capability that observes the MVI loop.
 *
 * This is the entire extension mechanism, and the library ships **no implementations** on
 * purpose. Loading, errors, navigation and analytics are not privileged concepts — they
 * are just the four plugins the sample app happens to write. Yours sit alongside them as
 * equals.
 *
 * Every hook has a default, so a plugin implements only what it cares about. A plugin
 * takes whatever it needs in its own constructor; the ViewModel that installs it supplies
 * that.
 */
interface MVIPlugin {

    /**
     * Called once, during ViewModel construction, before any intent is handled and before
     * the subclass's own `init` runs.
     *
     * Use it to capture the scope for work that outlives a single intent. Anything the
     * plugin needs from the app belongs in its constructor instead.
     */
    fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope) {}

    /**
     * Gives the plugin first look at every intent.
     *
     * Return `true` to mark the intent **handled**, which stops it reaching the
     * ViewModel's `handleIntent`. That is what lets a plugin own a whole class of intents
     * with no branch in any feature. Return `false` to observe and pass it on.
     *
     * Note a consuming plugin cannot change screen state — `updateState` is the
     * ViewModel's. Consumption suits guards, gating and interception.
     */
    fun onIntent(intent: Intent): Boolean = false

    fun onStateChanged(oldState: ViewState, newState: ViewState) {}

    fun onEffectEmitted(effect: Effect) {}

    fun onCleared() {}
}

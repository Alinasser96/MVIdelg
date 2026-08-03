package com.example.mvi.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.mvi.core.Effect
import kotlinx.coroutines.flow.Flow

/**
 * Consumes one-shot effects safely from Compose.
 *
 * Three details that are easy to get wrong by hand, all handled here:
 *
 * 1. **`repeatOnLifecycle`** — collection stops when the screen goes below
 *    [minActiveState], so an effect can never fire while the screen is in the back stack.
 * 2. **`rememberUpdatedState`** — [onEffect] usually closes over something recreated on
 *    recomposition. Without this, the collector would keep calling the first lambda forever.
 * 3. **Stable keys** — [LaunchedEffect] is keyed on the flow and the owner, not on
 *    [onEffect], so a new lambda every recomposition does not restart collection.
 *
 * ```
 * CollectEffects(viewModel.effect) { effect ->
 *     when (effect) {
 *         is ShowMessage -> snackbarHostState.showSnackbar(effect.text)
 *     }
 * }
 * ```
 *
 * Note that `MviViewModel.effect` is a `MutableSharedFlow` with no replay and no buffer,
 * so `emitEffect` suspends until this collector is active. An effect emitted while the
 * screen is stopped is delivered when it returns, but ordering across several such
 * effects is not guaranteed — keep effects idempotent-ish and few.
 */
@Composable
fun <E : Effect> CollectEffects(
    effects: Flow<E>,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (E) -> Unit,
) {
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner, minActiveState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            effects.collect { effect -> currentOnEffect(effect) }
        }
    }
}

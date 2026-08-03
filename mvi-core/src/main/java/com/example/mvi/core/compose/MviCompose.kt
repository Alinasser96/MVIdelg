package com.example.mvi.core.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.mvi.core.MviEffect
import kotlinx.coroutines.flow.Flow

/**
 * Consumes one-shot effects safely from Compose.
 *
 * Three details that are easy to get wrong by hand, all handled here:
 *
 * 1. **`repeatOnLifecycle`** — collection stops when the screen goes below
 *    [minActiveState], so a navigation effect can never fire while the screen is in the
 *    back stack. Buffered effects are delivered when it comes back.
 * 2. **`rememberUpdatedState`** — [onEffect] usually closes over a `NavController` that
 *    is recreated on recomposition. Without this, the collector would keep calling the
 *    very first lambda forever.
 * 3. **Stable keys** — [LaunchedEffect] is keyed on the flow and the owner, not on
 *    [onEffect], so a new lambda every recomposition does not restart collection.
 *
 * ```
 * CollectEffects(viewModel.effect) { effect ->
 *     when (effect) {
 *         is Navigate -> navController.navigate(effect.route)
 *         is ShowMessage -> snackbarHostState.showSnackbar(effect.text)
 *     }
 * }
 * ```
 *
 * [onEffect] is `suspend` so it can call suspending UI APIs such as `showSnackbar`
 * directly. It runs on the collector, so effects stay strictly ordered — and a handler
 * that waits a long time (a snackbar with an action) holds the next effect until it
 * returns. When that is not what you want, `launch { ... }` inside the handler.
 */
@Composable
fun <E : MviEffect> CollectEffects(
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

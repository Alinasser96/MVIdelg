package com.example.mvi.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvi.core.effect.EffectEmitter
import com.example.mvi.core.effect.EffectEmitterDelegate
import com.example.mvi.core.intent.IntentReceiver
import com.example.mvi.core.intent.IntentReceiverDelegate
import com.example.mvi.core.state.StateStore
import com.example.mvi.core.state.StateStoreDelegate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The base every screen's ViewModel extends.
 *
 * **This class contains almost no code, and that is the point.** It does not *implement*
 * state holding, effect emitting or intent receiving — it *acquires* those three
 * capabilities from three independent delegates:
 *
 * ```
 * StateStore<S>     by StateStoreDelegate(initialState)
 * EffectEmitter<E>  by EffectEmitterDelegate()
 * IntentReceiver<I> by IntentReceiverDelegate()
 * ```
 *
 * Why delegation instead of just putting the three `MutableStateFlow`s in the base class:
 *
 * - **Each capability stays swappable.** A ViewModel that needs state restored across
 *   process death can pass a `SavedStateStoreDelegate` instead, with no change here.
 * - **Each capability is unit-testable on its own**, without constructing a ViewModel or
 *   touching the Android main looper.
 * - **The base cannot grow into a god object.** Anything new — pagination, search,
 *   polling, undo — becomes another delegate a *feature* composes (see the `delegate`
 *   package), not another method every screen in the app inherits whether it needs it
 *   or not. That is the rule this blueprint is built around: **the base gives you the
 *   MVI loop and nothing else; everything else is composed.**
 *
 * Subclasses implement exactly one function, [handleIntent].
 *
 * @param initialState what the screen shows before anything has loaded.
 */
abstract class MviViewModel<I : MviIntent, S : MviState, E : MviEffect>(
    initialState: S,
) : ViewModel(),
    StateStore<S> by StateStoreDelegate(initialState),
    EffectEmitter<E> by EffectEmitterDelegate(),
    IntentReceiver<I> by IntentReceiverDelegate() {

    init {
        // The one wire in the whole base class: drain the intent stream into handleIntent.
        // Collection is sequential, so intents are processed strictly in order and a
        // reducer never races another reducer. Anything slow must therefore *start* work
        // and return (delegate.load(), viewModelScope.launch { ... }) rather than await it
        // inside handleIntent, or it would stall the queue.
        viewModelScope.launch {
            intents.collect { intent ->
                onIntentReceived(intent)
                try {
                    handleIntent(intent)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    onIntentError(intent, error)
                }
            }
        }
    }

    /**
     * Reduce one intent: update state, send effects, start work.
     *
     * Implement it as an exhaustive `when` over the sealed intent type and let the
     * compiler tell you when a new intent has no handler.
     */
    protected abstract suspend fun handleIntent(intent: I)

    /**
     * Called for every intent before it is handled. The blueprint's logging /
     * analytics / time-travel-debugging seam — override it to `Log.d(...)` every intent
     * in debug builds and get a free audit trail of the whole screen.
     */
    protected open fun onIntentReceived(intent: I) = Unit

    /**
     * Last-resort handler for an exception thrown out of [handleIntent].
     *
     * Without this, one unhandled throw would cancel `viewModelScope` and the screen
     * would silently stop responding to *all* further intents. The default rethrows so
     * bugs stay loud; override to map the error into state instead.
     */
    protected open fun onIntentError(intent: I, error: Throwable): Unit = throw error
}

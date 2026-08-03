package com.example.mvi.core.intent

import com.example.mvi.core.MviIntent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The single entry point the UI uses to talk to a ViewModel.
 *
 * The third primitive capability. One `onIntent(intent)` function replaces the dozen
 * `onRetryClicked()` / `onQueryChanged()` methods a plain ViewModel would expose, which
 * is what makes a screen's whole input surface enumerable — and testable — from its
 * sealed [MviIntent] hierarchy.
 */
interface IntentReceiver<I : MviIntent> {

    /**
     * Intents in the order they were received.
     *
     * The delegate deliberately exposes a *stream* instead of taking a handler callback
     * in its constructor. A delegate expression in a class header (`by IntentReceiverDelegate(...)`)
     * is evaluated before `this` exists, so it could never be handed `viewModelScope` or
     * `::handleIntent`. Publishing the stream lets the owner start collecting in `init`,
     * once it is fully constructed.
     */
    val intents: Flow<I>

    /** Called by the UI. Never suspends, never blocks — the intent is queued and returns. */
    fun onIntent(intent: I)
}

/**
 * The only implementation of [IntentReceiver].
 *
 * A [Channel] (not a `SharedFlow`) guarantees that every intent is handled exactly once
 * and in order, even the ones sent before anyone started collecting — for example from a
 * `LaunchedEffect` that runs during the very first composition.
 */
class IntentReceiverDelegate<I : MviIntent>(
    capacity: Int = Channel.BUFFERED,
) : IntentReceiver<I> {

    private val channel = Channel<I>(capacity)

    override val intents: Flow<I> = channel.receiveAsFlow()

    override fun onIntent(intent: I) {
        channel.trySend(intent)
    }
}

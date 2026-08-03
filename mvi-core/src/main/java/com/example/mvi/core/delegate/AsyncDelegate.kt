package com.example.mvi.core.delegate

import com.example.mvi.core.Async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs one suspending load and reports it as [Async].
 *
 * The smallest *feature* delegate — the second kind of delegation in this blueprint.
 * The primitives in `state` / `effect` / `intent` are mixed into the base class with
 * `by`; feature delegates like this one are **composed as private fields** by an
 * individual ViewModel:
 *
 * ```
 * private val profile = AsyncDelegate(viewModelScope) { repository.user(id) }
 *
 * init {
 *     profile.state
 *         .onEach { async -> updateState { copy(user = async.valueOrNull, ...) } }
 *         .launchIn(viewModelScope)
 * }
 * ```
 *
 * The delegate owns its own little state machine and knows nothing about the screen;
 * the ViewModel *projects* that state into its own [com.example.mvi.core.MviState].
 * That projection step is what keeps the screen's state a single flat object the UI can
 * render without knowing any delegate exists.
 *
 * Handles the three things everyone re-writes by hand and gets subtly wrong: cancelling
 * the previous attempt, not losing the old value while reloading, and not swallowing
 * [CancellationException].
 */
class AsyncDelegate<T>(
    private val scope: CoroutineScope,
    private val block: suspend () -> T,
) {

    private val _state = MutableStateFlow<Async<T>>(Async.Idle)
    val state: StateFlow<Async<T>> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts the load.
     *
     * @param force when false (the default) an already-loaded value is kept and nothing
     *   runs, which makes this safe to call from `init` *and* from an `onResume` intent.
     */
    fun load(force: Boolean = false) {
        val current = _state.value
        if (!force && (current is Async.Success || current is Async.Loading)) return
        val previous = current.valueOrNull
        job?.cancel()
        // Set Loading synchronously so the guard above is already true for a second
        // load() in the same frame; a flag flipped inside launch {} would be too late.
        _state.value = Async.Loading(previous)
        job = scope.launch {
            try {
                _state.value = Async.Success(block())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _state.value = Async.Failure(error, previous)
            }
        }
    }

    /** Re-runs the load even if it already succeeded. Wire this to your `RetryClicked` intent. */
    fun retry() = load(force = true)

    /** Cancels an in-flight load and returns to [Async.Idle]. */
    fun reset() {
        job?.cancel()
        _state.value = Async.Idle
    }
}

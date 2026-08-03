package com.example.mvi.core.state

import com.example.mvi.core.MviState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns the single source of truth for one screen.
 *
 * This is the first of the three primitive capabilities that make up an MVI ViewModel.
 * It is an *interface* rather than a set of fields on a base class so that the base
 * class can acquire the capability by delegation (`by`) instead of by inheritance —
 * see [com.example.mvi.core.MviViewModel].
 */
interface StateStore<S : MviState> {

    /** The stream the UI collects. Always has a value, never emits duplicates in a row. */
    val state: StateFlow<S>

    /**
     * The value right now. Use inside the ViewModel when you need to *read* state to make
     * a decision; never use it in the UI layer, collect [state] there instead.
     */
    val currentState: S

    /**
     * Atomically replaces the state with the result of [reducer].
     *
     * [reducer] must be **pure**: it may be invoked more than once when two coroutines
     * update concurrently, so it must not send effects, start work, or touch anything
     * outside the state object.
     */
    fun updateState(reducer: S.() -> S)
}

/**
 * The only implementation of [StateStore] — a `MutableStateFlow` with the mutable half
 * kept private.
 *
 * Because the capability is a delegate rather than a base-class field, it can be reused
 * anywhere: in a ViewModel, in a feature delegate, in a plain Kotlin class in tests.
 */
class StateStoreDelegate<S : MviState>(initialState: S) : StateStore<S> {

    private val _state = MutableStateFlow(initialState)

    override val state: StateFlow<S> = _state.asStateFlow()

    override val currentState: S get() = _state.value

    override fun updateState(reducer: S.() -> S) {
        _state.update(reducer)
    }
}

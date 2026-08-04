package com.example.mvi.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The MVI loop, and nothing else.
 *
 * **This class has no idea plugins exist.** Extend it for a screen that is only MVI: an
 * intent channel, a state flow, an effect channel, and a reducer. Nothing to install,
 * nothing to configure, no `com.example.mvi.core.plugins` import anywhere.
 *
 * ```
 * class ProfileViewModel(
 *     private val repository: UserRepository,
 * ) : MviViewModel<ProfileViewState, ProfileIntent, ProfileEffect>() {
 *
 *     override fun initialState() = ProfileViewState()
 *
 *     override suspend fun handleIntent(intent: ProfileIntent) {
 *         val user = repository.user(id)   // suspend call, no launch needed
 *         updateState { copy(user = user) }
 *     }
 * }
 * ```
 *
 * Intents are serialized through a channel — handled one at a time, in order — which is
 * why [handleIntent] can suspend: while it awaits the network, the next intent waits its
 * turn.
 *
 * Cross-cutting behaviour goes through the four **seams** at the bottom of this class.
 * They are ordinary `protected open` functions that do nothing by default. If you want
 * them driven by installable capabilities, extend
 * [com.example.mvi.core.plugins.PluggableMviViewModel] instead — that is all it does.
 */
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect> : ViewModel() {

    private val intentChannel = Channel<I>(Channel.UNLIMITED)

    /**
     * Effects go through a channel, for the same reasons intents do: sending never
     * suspends, buffering is explicit, and each effect is delivered to exactly one
     * collector and never replayed on rotation.
     */
    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /**
     * Deferred because [initialState] is an abstract member — calling it from this
     * constructor would run before the subclass finished initializing.
     */
    private val _viewState: MutableStateFlow<T> by lazy { MutableStateFlow(initialState()) }

    val viewState: StateFlow<T> by lazy { _viewState.asStateFlow() }

    val effect: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: T
        get() = _viewState.value

    init {
        viewModelScope.launch {
            intentChannel.receiveAsFlow().collect { intent ->
                if (!interceptIntent(intent)) {
                    handleIntent(intent)
                }
            }
        }
    }

    abstract fun initialState(): T

    abstract suspend fun handleIntent(intent: I)

    /** The single entry point from the UI. Never suspends, never drops an intent. */
    fun processIntent(intent: I) {
        intentChannel.trySend(intent)
    }

    /**
     * Atomically reduce state, then run the [afterStateUpdate] seam.
     *
     * [getAndUpdate] rather than a read-modify-write pair: `updateState` is `protected`,
     * so a subclass collecting a flow in its own coroutine can call it concurrently with
     * intent handling, and a plain assignment would silently drop one of the two updates.
     *
     * [reducer] must therefore be **pure** — it may run more than once under contention.
     */
    protected fun updateState(reducer: T.() -> T) {
        lateinit var newState: T
        val oldState = _viewState.getAndUpdate { current ->
            current.reducer().also { newState = it }
        }
        afterStateUpdate(oldState, newState)
    }

    /** Queues a one-shot effect. Non-suspending, so it is safe from any reducer branch. */
    protected fun emitEffect(effect: E) {
        effectChannel.trySend(effect)
        afterEffect(effect)
    }

    // ---- Seams ----
    //
    // Four no-ops. Override them directly for one-off behaviour, or let
    // PluggableMviViewModel wire them to a list of plugins. Note they are typed in T/I/E,
    // so an override never has to cast.

    /** Runs before [handleIntent]. Return true to swallow the intent. */
    protected open fun interceptIntent(intent: I): Boolean = false

    /** Runs after every successful [updateState]. */
    protected open fun afterStateUpdate(oldState: T, newState: T) {}

    /** Runs after every [emitEffect]. */
    protected open fun afterEffect(effect: E) {}

    /** Runs with [onCleared], after the channels are closed. */
    protected open fun onDispose() {}

    final override fun onCleared() {
        super.onCleared()
        intentChannel.close()
        effectChannel.close()
        onDispose()
    }
}

package com.example.mvi.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvi.core.plugins.MVIPlugin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Core MVI ViewModel.
 *
 * The parent class owns the MVI loop outright — the intent channel, the state flow, the
 * effect channel, and the reducer. There is no indirection to chase: `updateState`,
 * `emitEffect` and `processIntent` are all right here.
 *
 * Intents are serialized through a channel — they are processed one at a time, in order.
 * `handleIntent` is a suspend function, so async work can be called directly without
 * `viewModelScope.launch`; the next intent simply waits its turn.
 *
 * **Cross-cutting behaviour comes from [plugins].** The library ships none of them: a
 * plugin is something you write, holding whatever it needs in its own constructor. See
 * `:app`'s `plugins` package for four worked examples — loading, errors, navigation and
 * analytics — and the pattern that gives each a marker interface and a typed accessor.
 *
 * ```
 * class ProfileViewModel(
 *     private val repository: UserRepository,
 *     navigator: Navigator,
 * ) : MviViewModel<ProfileViewState, ProfileIntent, NoEffect>(
 *     plugins = listOf(LoadingPlugin(), ErrorPlugin(), NavigationPlugin(navigator)),
 * ),
 *     HasLoadingPlugin,
 *     HasErrorPlugin {
 *
 *     override fun initialState() = ProfileViewState()
 *
 *     override suspend fun handleIntent(intent: ProfileIntent) {
 *         loading.withLoading {
 *             val user = repository.user(id)   // suspend call, no launch needed
 *             updateState { copy(user = user) }
 *         }
 *     }
 * }
 * ```
 *
 * @param plugins the capabilities this screen installs, in the order they should see
 *   intents. Nothing here is required — a screen with no cross-cutting needs passes none.
 */
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect>(
    private val plugins: List<MVIPlugin> = emptyList(),
) : ViewModel() {

    private val intentChannel = Channel<I>(Channel.UNLIMITED)

    /**
     * Effects go through a channel, for the same reasons intents do: sending never
     * suspends, buffering is explicit, and each effect is delivered to exactly one
     * collector and never replayed on rotation.
     */
    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /**
     * Only [initialState] is deferred, because it is an abstract member and calling it
     * from this constructor would run before the subclass finished initializing. Plugin
     * setup deliberately does *not* wait for it — see the `init` block.
     */
    private val _viewState: MutableStateFlow<T> by lazy { MutableStateFlow(initialState()) }

    val viewState: StateFlow<T> by lazy { _viewState.asStateFlow() }

    val effect: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: T
        get() = _viewState.value

    init {
        // Plugins are set up eagerly, not on first observation of viewState, so they are
        // usable from a subclass's own init block.
        plugins.forEach { plugin -> plugin.onCreate(this, viewModelScope) }

        viewModelScope.launch {
            intentChannel.receiveAsFlow().collect { intent ->
                // Every plugin sees every intent; any of them may claim it. Mapping
                // before reducing matters: `any { }` short-circuits, which would make
                // whether a plugin observes an intent depend on its position in the list.
                val handled = plugins.map { plugin -> plugin.onIntent(intent) }.any { it }
                if (!handled) {
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
     * Atomically reduce state, then notify plugins.
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
        plugins.forEach { it.onStateChanged(oldState, newState) }
    }

    /** Queues a one-shot effect. Non-suspending, so it is safe from any reducer branch. */
    protected fun emitEffect(effect: E) {
        effectChannel.trySend(effect)
        plugins.forEach { it.onEffectEmitted(effect) }
    }

    /**
     * Finds an installed plugin by type, or null.
     *
     * This is what lets a typed accessor be written for a plugin — see the
     * `requirePlugin()` extension, and the marker + accessor pairs in `:app`.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P : MVIPlugin> pluginOrNull(type: KClass<P>): P? =
        plugins.firstOrNull { type.isInstance(it) } as P?

    override fun onCleared() {
        super.onCleared()
        intentChannel.close()
        effectChannel.close()
        plugins.forEach { it.onCleared() }
    }
}

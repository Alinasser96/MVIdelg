package com.example.mvi.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.plugins.ErrorPluginImpl
import com.example.mvi.core.plugins.HasErrorPlugin
import com.example.mvi.core.plugins.HasLoadingPlugin
import com.example.mvi.core.plugins.HasLoggingPlugin
import com.example.mvi.core.plugins.HasNavigationPlugin
import com.example.mvi.core.plugins.LoadingPluginImpl
import com.example.mvi.core.plugins.LoggingPluginImpl
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.NavigationPluginImpl
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
 * **Plugins are automatically installed based on implemented interfaces.** Just implement
 * a `HasXxxPlugin` interface to get the plugin functionality — the base checks
 * `this is HasLoadingPlugin` at construction and installs accordingly. That is where the
 * architecture's extensibility lives: capabilities are *installed*, never inherited as
 * methods every screen carries.
 *
 * Intents are serialized through a channel — they are processed one at a time, in order.
 * `handleIntent` is a suspend function, so async work can be called directly without
 * `viewModelScope.launch`; the next intent simply waits its turn.
 *
 * Example:
 * ```
 * class MyViewModel(
 *     dispatcherProvider: DispatcherProvider,
 *     pluginDependencies: MviPluginDependencies,
 * ) : MviViewModel<MyState, MyIntent, MyEffect>(dispatcherProvider, pluginDependencies),
 *     HasLoadingPlugin,
 *     HasErrorPlugin {
 *
 *     override fun initialState() = MyState()
 *
 *     override suspend fun handleIntent(intent: MyIntent) {
 *         loading.withLoading {
 *             val result = repository.fetch() // suspend call, no launch needed
 *             updateState { copy(data = result) }
 *         }
 *     }
 * }
 * ```
 *
 * @param additionalPlugins plugins this screen supplies itself, installed alongside the
 *   four the markers control. This is the seam that lets a *feature* module define a
 *   plugin without the base class having to know about it. It is a constructor parameter
 *   rather than an overridable function on purpose: an open function would be called
 *   during this class's initialization, before the subclass's own properties exist.
 */
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect>(
    protected val dispatcherProvider: DispatcherProvider,
    private val pluginDependencies: MviPluginDependencies,
    additionalPlugins: List<MVIPlugin> = emptyList(),
) : ViewModel() {

    internal val _loadingPlugin: LoadingPluginImpl? =
        if (this is HasLoadingPlugin) LoadingPluginImpl() else null

    internal val _errorPlugin: ErrorPluginImpl? =
        if (this is HasErrorPlugin) ErrorPluginImpl() else null

    internal val _navigationPlugin: NavigationPluginImpl? =
        if (this is HasNavigationPlugin) NavigationPluginImpl() else null

    internal val _loggingPlugin: LoggingPluginImpl? =
        if (this is HasLoggingPlugin) LoggingPluginImpl() else null

    private val plugins: List<MVIPlugin> = listOfNotNull(
        _loadingPlugin,
        _errorPlugin,
        _navigationPlugin,
        _loggingPlugin,
    ) + additionalPlugins

    private val intentChannel = Channel<I>(Channel.UNLIMITED)

    /**
     * Effects go through a channel, for the same reasons intents do: sending never
     * suspends, buffering is explicit, and each effect is delivered to exactly one
     * collector and never replayed on rotation.
     */
    private val effectChannel = Channel<E>(Channel.BUFFERED)

    /**
     * Only [initialState] stays lazy, because it is an abstract member and calling it
     * from this constructor would run before the subclass finished initializing. Plugin
     * setup deliberately does *not* wait for it — see the `init` block.
     */
    private val _viewState: MutableStateFlow<T> by lazy { MutableStateFlow(initialState()) }

    val viewState: StateFlow<T> by lazy { _viewState.asStateFlow() }

    val effect: Flow<E> = effectChannel.receiveAsFlow()

    protected val currentState: T
        get() = _viewState.value

    init {
        // Plugins are created eagerly, not on first observation of viewState. Deferring
        // this used to mean `navigation.navigateTo(...)` from a subclass init threw,
        // because the plugin had no Navigator yet.
        plugins.forEach { plugin -> plugin.onCreate(this, viewModelScope, pluginDependencies) }

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

    /**
     * Finds an installed plugin by type, or null.
     *
     * This is what lets code *outside* `:mvi-core` reproduce the marker + accessor pattern
     * the four built-in plugins use. The `_loadingPlugin`-style fields are `internal`, so
     * without this a consumer module could pass a plugin through [additionalPlugins] but
     * never look it back up — leaving it to hold the reference by hand and giving up the
     * compile-time scoping that makes the built-ins pleasant.
     *
     * Prefer the reified `requirePlugin()` extension at the call site.
     */
    @Suppress("UNCHECKED_CAST")
    fun <P : MVIPlugin> pluginOrNull(type: KClass<P>): P? =
        plugins.firstOrNull { type.isInstance(it) } as P?

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

    override fun onCleared() {
        super.onCleared()
        intentChannel.close()
        effectChannel.close()
        plugins.forEach { it.onCleared() }
    }
}

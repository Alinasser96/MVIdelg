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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * Core MVI ViewModel.
 *
 * The parent class owns the MVI loop outright — the intent channel, the state flow, the
 * effect flow, and the reducer. There is no indirection to chase: `updateState`,
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
 */
abstract class MviViewModel<T : ViewState, I : Intent, E : Effect>(
    protected val dispatcherProvider: DispatcherProvider,
    private val pluginDependencies: MviPluginDependencies,
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
    )

    private val intentChannel = Channel<I>(Channel.UNLIMITED)

    /**
     * Lazy on purpose: nothing spins up until something actually observes the screen.
     * Plugin `onCreate` and the intent collector both start on first access to
     * [viewState]. Until then intents are safely parked in the UNLIMITED channel.
     */
    private val _viewState: MutableStateFlow<T> by lazy {
        MutableStateFlow(initialState()).also {
            plugins.forEach { plugin -> plugin.onCreate(this, viewModelScope, pluginDependencies) }
            viewModelScope.launch {
                intentChannel.receiveAsFlow().collect { intent ->
                    // Plugins get first look. Any plugin returning true consumes the
                    // intent, and the feature's handleIntent never sees it.
                    val handled = plugins.any { plugin -> plugin.onIntent(intent) }
                    if (!handled) {
                        handleIntent(intent)
                    }
                }
            }
        }
    }

    val viewState: StateFlow<T> by lazy { _viewState.asStateFlow() }

    private val _effect = MutableSharedFlow<E>()
    val effect = _effect.asSharedFlow()

    protected val currentState: T
        get() = _viewState.value

    abstract fun initialState(): T

    abstract suspend fun handleIntent(intent: I)

    /** The single entry point from the UI. Never suspends, never drops an intent. */
    fun processIntent(intent: I) {
        intentChannel.trySend(intent)
    }

    /**
     * Reduce state, then notify plugins.
     *
     * The `onStateChanged(old, new)` broadcast is what lets a plugin react to state
     * without the feature knowing it exists — analytics on a step change, for instance.
     */
    protected fun updateState(reducer: T.() -> T) {
        val oldState = currentState
        _viewState.value = currentState.reducer()
        val newState = currentState
        plugins.forEach { it.onStateChanged(oldState, newState) }
    }

    protected fun emitEffect(effect: E) {
        viewModelScope.launch(dispatcherProvider.main) {
            _effect.emit(effect)
            plugins.forEach { it.onEffectEmitted(effect) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        intentChannel.close()
        plugins.forEach { it.onCleared() }
    }
}

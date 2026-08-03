package com.example.mvi.core

import androidx.lifecycle.ViewModel
import app.cash.turbine.test
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.navigation.NavCommand
import com.example.mvi.core.plugins.HasErrorPlugin
import com.example.mvi.core.plugins.HasLoadingPlugin
import com.example.mvi.core.plugins.HasLoggingPlugin
import com.example.mvi.core.plugins.HasNavigationPlugin
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.configureLogging
import com.example.mvi.core.plugins.errors
import com.example.mvi.core.plugins.loading
import com.example.mvi.core.plugins.logging
import com.example.mvi.core.plugins.navigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The base class: intents in, state and effects out, plugins installed by marker.
 */
class MviViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val navigator = ChannelNavigator()
    private val analytics = RecordingAnalyticsLogger()

    private fun dependencies(): MviPluginDependencies =
        testPluginDependencies(mainDispatcherRule.testDispatcher, navigator, analytics)

    private fun dispatchers(): DispatcherProvider =
        TestDispatcherProvider(mainDispatcherRule.testDispatcher)

    private fun viewModel(extraPlugins: List<MVIPlugin> = emptyList()) =
        FullyLoadedViewModel(dispatchers(), dependencies(), extraPlugins)

    // ---- The loop ----

    @Test
    fun `intents are reduced into state in order`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Double)
        advanceUntilIdle()

        // Order matters: (0+1+1)*2 = 4, not 0+1+(1*2).
        assertEquals(4, viewModel.viewState.value.count)
    }

    @Test
    fun `intents are handled without anything ever observing viewState`() = runTest {
        val viewModel = viewModel()

        // The collector starts in init, so nothing has to touch viewState first.
        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.viewState.value.count)
    }

    @Test
    fun `initialState is used until something reduces it`() = runTest {
        val viewModel = viewModel()

        assertEquals(CounterState(), viewModel.viewState.value)
    }

    // ---- Effects ----

    @Test
    fun `effects emitted before anyone collects are buffered, not lost`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        viewModel.effect.test {
            assertEquals(CounterEffect.Toast("nice"), awaitItem())
        }
    }

    @Test
    fun `an effect is delivered once and never replayed`() = runTest {
        val viewModel = viewModel()

        viewModel.effect.test {
            viewModel.processIntent(CounterIntent.Celebrate)
            advanceUntilIdle()
            assertEquals(CounterEffect.Toast("nice"), awaitItem())
        }

        // A replaying SharedFlow would hand the same effect to this second collector -
        // which is how "the app navigates twice after rotation" bugs happen.
        viewModel.effect.test { expectNoEvents() }
    }

    // ---- Plugin installation ----

    @Test
    fun `a plugin is installed only when its marker is implemented`() = runTest {
        val loaded = viewModel()

        assertFalse(loaded.loading.isLoading.value)
        assertNull(loaded.errors.error.value)

        // No markers -> nothing installed. Referencing `bare.loading` would not even
        // compile, so the strongest runtime check available is the marker itself.
        val bare: MviViewModel<*, *, *> = BareViewModel(dispatchers(), dependencies())
        assertFalse(bare is HasLoadingPlugin)
        assertFalse(bare is HasErrorPlugin)
        assertFalse(bare is HasNavigationPlugin)
        assertFalse(bare is HasLoggingPlugin)
    }

    @Test
    fun `plugins are created eagerly, so a subclass can navigate from its own init`() = runTest {
        // Regression: plugin onCreate used to run only when viewState was first read, so
        // navigating from init threw UninitializedPropertyAccessException.
        navigator.commands.test {
            NavigatingOnInitViewModel(dispatchers(), dependencies())
            assertEquals(NavCommand.To(TestDestination("from-init")), awaitItem())
        }
    }

    @Test
    fun `additionalPlugins are installed alongside the four the markers control`() = runTest {
        val extra = RecordingPlugin()
        val viewModel = viewModel(listOf(extra))

        assertTrue(extra.created)

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf(CounterIntent.Increment), extra.seenIntents)
    }

    // ---- The onIntent hook ----

    @Test
    fun `a plugin can consume an intent before handleIntent sees it`() = runTest {
        val gatekeeper = RecordingPlugin(consume = { it == CounterIntent.Increment })
        val viewModel = viewModel(listOf(gatekeeper))

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf(CounterIntent.Increment), gatekeeper.seenIntents)
        assertEquals(0, viewModel.viewState.value.count)
    }

    @Test
    fun `every plugin observes an intent even when an earlier one consumes it`() = runTest {
        val consumer = RecordingPlugin(consume = { true })
        val observer = RecordingPlugin()
        val viewModel = viewModel(listOf(consumer, observer))

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        // `any { }` short-circuits, so mapping first is what stops a plugin's visibility
        // depending on its position in the list.
        assertEquals(listOf(CounterIntent.Increment), observer.seenIntents)
    }

    // ---- Plugin broadcasts ----

    @Test
    fun `updateState reports the old and new state to plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(listOf(recorder))

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf(CounterState(0) to CounterState(1)), recorder.stateChanges)
    }

    @Test
    fun `emitEffect reports to plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(listOf(recorder))

        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        assertEquals(listOf(CounterEffect.Toast("nice")), recorder.effects)
    }

    // ---- Installed plugin behaviour ----

    @Test
    fun `the loading plugin drives the spinner across a suspending intent`() = runTest {
        val viewModel = viewModel()

        viewModel.loading.isLoading.test {
            assertFalse(awaitItem())

            viewModel.processIntent(CounterIntent.LoadSlowly)
            // Step, don't jump: advancing straight to idle would let StateFlow conflate
            // true -> false and the spinner would never be observed at all.
            advanceTimeBy(1)
            assertTrue(awaitItem())

            advanceUntilIdle()
            assertFalse(awaitItem())
        }
        assertEquals(99, viewModel.viewState.value.count)
    }

    @Test
    fun `a failing intent lands in the error plugin, not in the screen state`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.Explode)
        advanceUntilIdle()

        assertEquals("boom", viewModel.errors.error.value?.message)
        assertEquals(CounterState(), viewModel.viewState.value)
    }

    @Test
    fun `navigation goes through the plugin, never through the ViewModel`() = runTest {
        val viewModel = viewModel()

        navigator.commands.test {
            viewModel.processIntent(CounterIntent.GoHome)
            advanceUntilIdle()

            assertEquals(NavCommand.To(TestDestination("home")), awaitItem())
        }
    }

    @Test
    fun `analytics are logged through the logging plugin`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, analytics.events.size)
        assertEquals("counter", analytics.events.first().screenId)
    }

    // ---- Teardown ----

    @Test
    fun `onCleared closes the channels and tells the plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(listOf(recorder))
        advanceUntilIdle()

        viewModel.clear()
        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertTrue(recorder.cleared)
        assertEquals(0, viewModel.viewState.value.count)
    }
}

// ---- Test doubles ----

private data class CounterState(val count: Int = 0) : ViewState

private sealed interface CounterIntent : Intent {
    data object Increment : CounterIntent
    data object Double : CounterIntent
    data object Celebrate : CounterIntent
    data object LoadSlowly : CounterIntent
    data object Explode : CounterIntent
    data object GoHome : CounterIntent
}

private sealed interface CounterEffect : Effect {
    data class Toast(val message: String) : CounterEffect
}

/** Records every hook, and optionally claims intents. */
private class RecordingPlugin(
    private val consume: (Intent) -> Boolean = { false },
) : MVIPlugin {

    var created = false
    var cleared = false
    val seenIntents = mutableListOf<Intent>()
    val stateChanges = mutableListOf<Pair<ViewState, ViewState>>()
    val effects = mutableListOf<Effect>()

    override fun onCreate(
        viewModel: ViewModel,
        viewModelScope: CoroutineScope,
        dependencies: MviPluginDependencies,
    ) {
        created = true
    }

    override fun onIntent(intent: Intent): Boolean {
        seenIntents += intent
        return consume(intent)
    }

    override fun onStateChanged(oldState: ViewState, newState: ViewState) {
        stateChanges += oldState to newState
    }

    override fun onEffectEmitted(effect: Effect) {
        effects += effect
    }

    override fun onCleared() {
        cleared = true
    }
}

/** A screen that opts into all four plugins. */
private class FullyLoadedViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
    extraPlugins: List<MVIPlugin> = emptyList(),
) : MviViewModel<CounterState, CounterIntent, CounterEffect>(
    dispatcherProvider,
    pluginDependencies,
    extraPlugins,
),
    HasLoadingPlugin,
    HasErrorPlugin,
    HasNavigationPlugin,
    HasLoggingPlugin {

    init {
        configureLogging("counter")
    }

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> {
                updateState { copy(count = count + 1) }
                logging.logButtonEvent("increment")
            }

            CounterIntent.Double -> updateState { copy(count = count * 2) }

            CounterIntent.Celebrate -> emitEffect(CounterEffect.Toast("nice"))

            // handleIntent is suspend, so async work needs no launch.
            CounterIntent.LoadSlowly -> loading.withLoading {
                delay(100)
                updateState { copy(count = 99) }
            }

            CounterIntent.Explode -> errors.runCatchingError { error("boom") }

            CounterIntent.GoHome -> navigation.navigateTo(TestDestination("home"))
        }
    }

    fun clear() = onCleared()
}

/** Proves plugins are usable from a subclass's own init block. */
private class NavigatingOnInitViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : MviViewModel<CounterState, CounterIntent, NoEffect>(dispatcherProvider, pluginDependencies),
    HasNavigationPlugin {

    init {
        navigation.navigateTo(TestDestination("from-init"))
    }

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) = Unit
}

/** A screen that opts into nothing. No plugin is installed for it. */
private class BareViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : MviViewModel<CounterState, CounterIntent, NoEffect>(dispatcherProvider, pluginDependencies) {

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) {
        updateState { copy(count = count + 1) }
    }
}

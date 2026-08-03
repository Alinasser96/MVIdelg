package com.example.mvi.core

import app.cash.turbine.test
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.navigation.NavCommand
import com.example.mvi.core.plugins.HasErrorPlugin
import com.example.mvi.core.plugins.HasLoadingPlugin
import com.example.mvi.core.plugins.HasLoggingPlugin
import com.example.mvi.core.plugins.HasNavigationPlugin
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.configureLogging
import com.example.mvi.core.plugins.errors
import com.example.mvi.core.plugins.loading
import com.example.mvi.core.plugins.logging
import com.example.mvi.core.plugins.navigation
import com.example.mvi.core.helpers.DispatcherProvider
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

    private fun viewModel() = FullyLoadedViewModel(dispatchers(), dependencies())

    @Test
    fun `intents are reduced into state in order`() = runTest {
        val viewModel = viewModel()
        viewModel.viewState.value // touch: lazy init starts the intent collector

        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Double)
        advanceUntilIdle()

        // Order matters: (0+1+1)*2 = 4, not 0+1+(1*2).
        assertEquals(4, viewModel.viewState.value.count)
    }

    @Test
    fun `intents sent before the state is observed are not lost`() = runTest {
        val viewModel = viewModel()

        // Nothing has touched viewState, so the collector has not started yet. The
        // UNLIMITED channel parks these until it does.
        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Increment)

        viewModel.viewState.value
        advanceUntilIdle()

        assertEquals(2, viewModel.viewState.value.count)
    }

    @Test
    fun `initialState is used until something reduces it`() = runTest {
        val viewModel = viewModel()

        assertEquals(CounterState(), viewModel.viewState.value)
    }

    @Test
    fun `effects are emitted to a collector`() = runTest {
        val viewModel = viewModel()
        viewModel.viewState.value

        viewModel.effect.test {
            viewModel.processIntent(CounterIntent.Celebrate)
            advanceUntilIdle()

            assertEquals(CounterEffect.Toast("nice"), awaitItem())
        }
    }

    @Test
    fun `a plugin is installed only when its marker is implemented`() = runTest {
        val loaded = viewModel()

        // Declared markers -> the accessors resolve and the plugins are live.
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
    fun `the loading plugin drives the spinner across a suspending intent`() = runTest {
        val viewModel = viewModel()
        viewModel.viewState.value

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
        viewModel.viewState.value

        viewModel.processIntent(CounterIntent.Explode)
        advanceUntilIdle()

        assertEquals("boom", viewModel.errors.error.value?.message)
        // The screen's own state never grew an error field.
        assertEquals(CounterState(), viewModel.viewState.value)
    }

    @Test
    fun `navigation goes through the plugin, never through the ViewModel`() = runTest {
        val viewModel = viewModel()
        viewModel.viewState.value

        navigator.commands.test {
            viewModel.processIntent(CounterIntent.GoHome)
            advanceUntilIdle()

            assertEquals(NavCommand.To(TestDestination("home")), awaitItem())
        }
    }

    @Test
    fun `analytics are logged through the logging plugin`() = runTest {
        val viewModel = viewModel()
        viewModel.viewState.value

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, analytics.events.size)
        assertEquals("counter", analytics.events.first().screenId)
    }

    @Test
    fun `plugins are not created until the state is observed`() = runTest {
        val viewModel = viewModel()

        // NavigationPluginImpl gets its Navigator in onCreate, which the lazy viewState
        // triggers. Navigating before anything observes the screen would throw - worth
        // knowing, because it makes "navigate straight from init" a trap.
        val threw = runCatching { viewModel.navigation.navigateTo(TestDestination("home")) }.isFailure
        assertTrue(threw)

        viewModel.viewState.value
        viewModel.navigation.navigateTo(TestDestination("home"))
    }

    @Test
    fun `onCleared closes the intent channel`() = runTest {
        val viewModel = viewModel()
        viewModel.viewState.value
        advanceUntilIdle()

        viewModel.clear()
        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

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

/** A screen that opts into all four plugins. */
private class FullyLoadedViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : MviViewModel<CounterState, CounterIntent, CounterEffect>(dispatcherProvider, pluginDependencies),
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

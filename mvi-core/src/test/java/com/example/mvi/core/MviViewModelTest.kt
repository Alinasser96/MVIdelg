package com.example.mvi.core

import androidx.lifecycle.ViewModel
import app.cash.turbine.test
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.core.plugins.pluginOrNull
import com.example.mvi.core.plugins.requirePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The base class and the plugin mechanism — the whole of what `:mvi-core` provides.
 *
 * There are no built-in plugins to test, so every plugin here is a local test double.
 * That is the same position a consumer is in.
 */
class MviViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(vararg plugins: MVIPlugin) = CounterViewModel(plugins.toList())

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
        assertEquals(CounterState(), viewModel().viewState.value)
    }

    @Test
    fun `a suspending intent does not drop the ones queued behind it`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.SlowIncrement)
        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(2, viewModel.viewState.value.count)
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

    // ---- Plugins ----

    @Test
    fun `a ViewModel with no plugins works fine`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.viewState.value.count)
        assertNull(viewModel.pluginOrNull<RecordingPlugin>())
    }

    @Test
    fun `an installed plugin is found by type`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(recorder)

        assertEquals(recorder, viewModel.requirePlugin<RecordingPlugin>())
        assertTrue(recorder.created)
    }

    @Test
    fun `requirePlugin names the missing type`() = runTest {
        val error = assertThrows(IllegalStateException::class.java) {
            viewModel().requirePlugin<RecordingPlugin>()
        }
        assertTrue(error.message!!.contains("RecordingPlugin"))
    }

    @Test
    fun `plugins are created eagerly, so a subclass can use one from its own init`() = runTest {
        val recorder = RecordingPlugin()

        // Regression: plugin onCreate used to run only when viewState was first read, so
        // anything a plugin set up in onCreate was unavailable during subclass init.
        UsesPluginInInitViewModel(recorder)

        assertTrue(recorder.created)
        assertTrue(recorder.scopeReceived)
    }

    // ---- The onIntent hook ----

    @Test
    fun `a plugin can consume an intent before handleIntent sees it`() = runTest {
        val gatekeeper = RecordingPlugin(consume = { it == CounterIntent.Increment })
        val viewModel = viewModel(gatekeeper)

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf(CounterIntent.Increment), gatekeeper.seenIntents)
        assertEquals(0, viewModel.viewState.value.count)
    }

    @Test
    fun `every plugin observes an intent even when an earlier one consumes it`() = runTest {
        val consumer = RecordingPlugin(consume = { true })
        val observer = RecordingPlugin()
        val viewModel = viewModel(consumer, observer)

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
        val viewModel = viewModel(recorder)

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf(CounterState(0) to CounterState(1)), recorder.stateChanges)
    }

    @Test
    fun `emitEffect reports to plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(recorder)

        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        assertEquals(listOf(CounterEffect.Toast("nice")), recorder.effects)
    }

    @Test
    fun `a plugin can hold state of its own across intents`() = runTest {
        val counter = CountingPlugin()
        val viewModel = viewModel(counter)

        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        assertEquals(2, counter.intentCount)
    }

    @Test
    fun `plugin state can be observed while a suspending intent runs`() = runTest {
        val busy = BusyPlugin()
        val viewModel = viewModel(busy)

        busy.isBusy.test {
            assertEquals(false, awaitItem())

            viewModel.processIntent(CounterIntent.SlowIncrement)
            // Step, don't jump: advancing straight to idle would let StateFlow conflate
            // true -> false and the flag would never be observed at all.
            advanceTimeBy(1)
            assertEquals(true, awaitItem())

            advanceUntilIdle()
            assertEquals(false, awaitItem())
        }
    }

    // ---- Teardown ----

    @Test
    fun `onCleared closes the channels and tells the plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(recorder)
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
    data object SlowIncrement : CounterIntent
    data object Double : CounterIntent
    data object Celebrate : CounterIntent
}

private sealed interface CounterEffect : Effect {
    data class Toast(val message: String) : CounterEffect
}

/** Records every hook, and optionally claims intents. */
private class RecordingPlugin(
    private val consume: (Intent) -> Boolean = { false },
) : MVIPlugin {

    var created = false
    var scopeReceived = false
    var cleared = false
    val seenIntents = mutableListOf<Intent>()
    val stateChanges = mutableListOf<Pair<ViewState, ViewState>>()
    val effects = mutableListOf<Effect>()

    override fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope) {
        created = true
        scopeReceived = true
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

/** A plugin with state of its own, the shape most real plugins take. */
private class CountingPlugin : MVIPlugin {
    var intentCount = 0
    override fun onIntent(intent: Intent): Boolean {
        intentCount++
        return false
    }
}

/** A plugin exposing a flow the UI would collect, like a loading indicator would. */
private class BusyPlugin : MVIPlugin {
    private val _isBusy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isBusy = _isBusy

    suspend fun <R> whileBusy(block: suspend () -> R): R = try {
        _isBusy.value = true
        block()
    } finally {
        _isBusy.value = false
    }
}

private class CounterViewModel(
    plugins: List<MVIPlugin> = emptyList(),
) : MviViewModel<CounterState, CounterIntent, CounterEffect>(plugins) {

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> updateState { copy(count = count + 1) }

            // handleIntent is suspend, so async work needs no launch.
            CounterIntent.SlowIncrement -> {
                val busy = pluginOrNull<BusyPlugin>()
                if (busy != null) {
                    busy.whileBusy {
                        delay(100)
                        updateState { copy(count = count + 1) }
                    }
                } else {
                    delay(100)
                    updateState { copy(count = count + 1) }
                }
            }

            CounterIntent.Double -> updateState { copy(count = count * 2) }

            CounterIntent.Celebrate -> emitEffect(CounterEffect.Toast("nice"))
        }
    }

    fun clear() = onCleared()
}

/** Proves a plugin is usable from a subclass's own init block. */
private class UsesPluginInInitViewModel(
    plugin: MVIPlugin,
) : MviViewModel<CounterState, CounterIntent, NoEffect>(listOf(plugin)) {

    init {
        // Would fail if plugin setup were deferred until viewState was first read.
        requirePlugin<RecordingPlugin>()
    }

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) = Unit
}

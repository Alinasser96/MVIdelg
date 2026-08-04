package com.example.mvi.core.plugins

import androidx.lifecycle.ViewModel
import com.example.mvi.core.CounterEffect
import com.example.mvi.core.CounterIntent
import com.example.mvi.core.CounterState
import com.example.mvi.core.Effect
import com.example.mvi.core.Intent
import com.example.mvi.core.MainDispatcherRule
import com.example.mvi.core.NoEffect
import com.example.mvi.core.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The plugin layer — the only thing `PluggableMviViewModel` adds on top of the loop.
 *
 * There are no built-in plugins to test, so every plugin here is a local test double.
 * That is exactly the position a consumer is in.
 */
class PluggableMviViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(vararg plugins: MVIPlugin) = CounterViewModel(plugins.toList())

    @Test
    fun `a ViewModel with an empty plugin list behaves like plain MVI`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.viewState.value.count)
        assertNull(viewModel.pluginOrNull<RecordingPlugin>())
    }

    @Test
    fun `an installed plugin is found by type and told about onCreate`() = runTest {
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

        // The base class's init cannot do this — `plugins` is not assigned yet there.
        // PluggableMviViewModel's own init can, which is why it lives in that class.
        UsesPluginInInitViewModel(recorder)

        assertTrue(recorder.created)
    }

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

    @Test
    fun `updateState reports the old and new state to plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(recorder)

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf<Pair<ViewState, ViewState>>(CounterState(0) to CounterState(1)), recorder.stateChanges)
    }

    @Test
    fun `emitEffect reports to plugins`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(recorder)

        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        assertEquals(listOf<Effect>(CounterEffect.Toast("nice")), recorder.effects)
    }

    @Test
    fun `onCleared reaches every plugin`() = runTest {
        val recorder = RecordingPlugin()
        val viewModel = viewModel(recorder)
        advanceUntilIdle()

        viewModel.clear()

        assertTrue(recorder.cleared)
    }
}

// ---- Test doubles ----

private class RecordingPlugin(
    private val consume: (Intent) -> Boolean = { false },
) : MVIPlugin {

    var created = false
    var cleared = false
    val seenIntents = mutableListOf<Intent>()
    val stateChanges = mutableListOf<Pair<ViewState, ViewState>>()
    val effects = mutableListOf<Effect>()

    override fun onCreate(viewModel: ViewModel, viewModelScope: CoroutineScope) {
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

private class CounterViewModel(
    plugins: List<MVIPlugin>,
) : PluggableMviViewModel<CounterState, CounterIntent, CounterEffect>(plugins) {

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Celebrate -> emitEffect(CounterEffect.Toast("nice"))
            CounterIntent.Double -> updateState { copy(count = count * 2) }
            else -> updateState { copy(count = count + 1) }
        }
    }

    fun clear() = onCleared()
}

/** Proves a plugin is usable from a subclass's own init block. */
private class UsesPluginInInitViewModel(
    plugin: MVIPlugin,
) : PluggableMviViewModel<CounterState, CounterIntent, NoEffect>(listOf(plugin)) {

    init {
        requirePlugin<RecordingPlugin>()
    }

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) = Unit
}

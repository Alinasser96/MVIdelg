package com.example.mvi.core

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The MVI loop on its own — no plugins anywhere, not even imported.
 *
 * Every test here would still pass if `com.example.mvi.core.plugins` were deleted.
 */
class MviViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `intents are reduced into state in order`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Increment)
        viewModel.processIntent(CounterIntent.Double)
        advanceUntilIdle()

        // Order matters: (0+1+1)*2 = 4, not 0+1+(1*2).
        assertEquals(4, viewModel.viewState.value.count)
    }

    @Test
    fun `intents are handled without anything ever observing viewState`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.viewState.value.count)
    }

    @Test
    fun `initialState is used until something reduces it`() = runTest {
        assertEquals(CounterState(), CounterViewModel().viewState.value)
    }

    @Test
    fun `a suspending intent does not drop the ones queued behind it`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.processIntent(CounterIntent.SlowIncrement)
        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(2, viewModel.viewState.value.count)
    }

    @Test
    fun `effects emitted before anyone collects are buffered, not lost`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        viewModel.effect.test {
            assertEquals(CounterEffect.Toast("nice"), awaitItem())
        }
    }

    @Test
    fun `an effect is delivered once and never replayed`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.effect.test {
            viewModel.processIntent(CounterIntent.Celebrate)
            advanceUntilIdle()
            assertEquals(CounterEffect.Toast("nice"), awaitItem())
        }

        // A replaying SharedFlow would hand the same effect to this second collector -
        // which is how "the app navigates twice after rotation" bugs happen.
        viewModel.effect.test { expectNoEvents() }
    }

    // ---- The seams, used directly, with no plugin machinery ----

    @Test
    fun `interceptIntent can swallow an intent before handleIntent sees it`() = runTest {
        val viewModel = SeamViewModel(swallow = { it == CounterIntent.Increment })

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(0, viewModel.viewState.value.count)
        assertTrue(viewModel.handled.isEmpty())
    }

    @Test
    fun `afterStateUpdate receives the old and new state, typed`() = runTest {
        val viewModel = SeamViewModel()

        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(listOf(CounterState(0) to CounterState(1)), viewModel.stateChanges)
    }

    @Test
    fun `afterEffect receives every emitted effect, typed`() = runTest {
        val viewModel = SeamViewModel()

        viewModel.processIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        assertEquals(listOf(CounterEffect.Toast("nice")), viewModel.seenEffects)
    }

    @Test
    fun `onDispose runs when the ViewModel is cleared`() = runTest {
        val viewModel = SeamViewModel()
        advanceUntilIdle()

        viewModel.clear()

        assertTrue(viewModel.disposed)
        viewModel.processIntent(CounterIntent.Increment)
        advanceUntilIdle()
        assertEquals(0, viewModel.viewState.value.count)
    }
}

// ---- Test doubles ----

internal data class CounterState(val count: Int = 0) : ViewState

internal sealed interface CounterIntent : Intent {
    data object Increment : CounterIntent
    data object SlowIncrement : CounterIntent
    data object Double : CounterIntent
    data object Celebrate : CounterIntent
}

internal sealed interface CounterEffect : Effect {
    data class Toast(val message: String) : CounterEffect
}

/** Plain MVI. Note the absence of any plugin import. */
private class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>() {

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> updateState { copy(count = count + 1) }
            // handleIntent is suspend, so async work needs no launch.
            CounterIntent.SlowIncrement -> {
                delay(100)
                updateState { copy(count = count + 1) }
            }
            CounterIntent.Double -> updateState { copy(count = count * 2) }
            CounterIntent.Celebrate -> emitEffect(CounterEffect.Toast("nice"))
        }
    }
}

/** Overrides the seams by hand — the option that exists before you reach for plugins. */
private class SeamViewModel(
    private val swallow: (CounterIntent) -> Boolean = { false },
) : MviViewModel<CounterState, CounterIntent, CounterEffect>() {

    val handled = mutableListOf<CounterIntent>()
    val stateChanges = mutableListOf<Pair<CounterState, CounterState>>()
    val seenEffects = mutableListOf<CounterEffect>()
    var disposed = false

    override fun initialState() = CounterState()

    override suspend fun handleIntent(intent: CounterIntent) {
        handled += intent
        when (intent) {
            CounterIntent.Celebrate -> emitEffect(CounterEffect.Toast("nice"))
            else -> updateState { copy(count = count + 1) }
        }
    }

    override fun interceptIntent(intent: CounterIntent) = swallow(intent)

    override fun afterStateUpdate(oldState: CounterState, newState: CounterState) {
        stateChanges += oldState to newState
    }

    override fun afterEffect(effect: CounterEffect) {
        seenEffects += effect
    }

    override fun onDispose() {
        disposed = true
    }

    fun clear() = onCleared()
}

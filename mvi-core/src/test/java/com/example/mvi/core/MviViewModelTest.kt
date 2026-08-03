package com.example.mvi.core

import app.cash.turbine.test
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * What testing an MVI screen looks like: send intents, assert on state and effects.
 * No Robolectric, no Android, no mocking framework.
 */
class MviViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `intents are reduced into state in order`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterIntent.Increment)
        viewModel.onIntent(CounterIntent.Increment)
        viewModel.onIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(3, viewModel.currentState.count)
    }

    @Test
    fun `intents sent before anyone collects are not lost`() = runTest {
        val viewModel = CounterViewModel()

        // Queued while the intent channel has no collector yet - the Channel buffers them.
        viewModel.onIntent(CounterIntent.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.currentState.count)
    }

    @Test
    fun `effects are delivered once`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.effect.test {
            viewModel.onIntent(CounterIntent.Celebrate)
            advanceUntilIdle()

            assertEquals(CounterEffect.Toast("100 reached"), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `onIntentReceived sees every intent - the logging seam`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterIntent.Increment)
        viewModel.onIntent(CounterIntent.Celebrate)
        advanceUntilIdle()

        assertEquals(
            listOf(CounterIntent.Increment, CounterIntent.Celebrate),
            viewModel.observedIntents,
        )
    }

    @Test
    fun `a throwing intent is routed to onIntentError and the ViewModel stays alive`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.onIntent(CounterIntent.Explode)
        advanceUntilIdle()
        assertEquals("boom", viewModel.currentState.lastError)

        // The real regression this guards: without the try/catch in the base class the
        // throw would cancel viewModelScope and every later intent would be ignored.
        viewModel.onIntent(CounterIntent.Increment)
        advanceUntilIdle()
        assertEquals(1, viewModel.currentState.count)
    }

    @Test
    fun `state exposes distinct values only`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.state.test {
            assertEquals(CounterState(), awaitItem())

            viewModel.onIntent(CounterIntent.Increment)
            advanceUntilIdle()
            assertEquals(1, awaitItem().count)

            // Reducing to an equal value emits nothing: StateFlow conflates duplicates.
            viewModel.onIntent(CounterIntent.NoOp)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `initial state is the one passed to the base class`() = runTest {
        val viewModel = CounterViewModel()

        assertEquals(0, viewModel.currentState.count)
        assertNull(viewModel.currentState.lastError)
    }
}

private data class CounterState(
    val count: Int = 0,
    val lastError: String? = null,
) : MviState

private sealed interface CounterIntent : MviIntent {
    data object Increment : CounterIntent
    data object NoOp : CounterIntent
    data object Celebrate : CounterIntent
    data object Explode : CounterIntent
}

private sealed interface CounterEffect : MviEffect {
    data class Toast(val message: String) : CounterEffect
}

private class CounterViewModel :
    MviViewModel<CounterIntent, CounterState, CounterEffect>(CounterState()) {

    val observedIntents = mutableListOf<CounterIntent>()

    override suspend fun handleIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> updateState { copy(count = count + 1) }
            CounterIntent.NoOp -> updateState { this }
            CounterIntent.Celebrate -> sendEffect(CounterEffect.Toast("100 reached"))
            CounterIntent.Explode -> error("boom")
        }
    }

    override fun onIntentReceived(intent: CounterIntent) {
        observedIntents += intent
    }

    override fun onIntentError(intent: CounterIntent, error: Throwable) {
        updateState { copy(lastError = error.message) }
    }
}

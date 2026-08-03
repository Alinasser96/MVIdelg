package com.example.mvi.core

import app.cash.turbine.test
import com.example.mvi.core.effect.EffectEmitterDelegate
import com.example.mvi.core.intent.IntentReceiverDelegate
import com.example.mvi.core.state.StateStoreDelegate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three primitives, tested without a ViewModel in sight. If these had been fields on
 * a base class instead of delegates, none of these tests could exist on their own.
 */
class PrimitiveDelegatesTest {

    @Test
    fun `state store reduces and exposes the current value`() = runTest {
        val store = StateStoreDelegate(Counter(0))

        store.updateState { copy(value = value + 1) }
        store.updateState { copy(value = value * 10) }

        assertEquals(Counter(10), store.currentState)
        assertEquals(Counter(10), store.state.value)
    }

    @Test
    fun `effects buffered before collection are still delivered`() = runTest {
        val emitter = EffectEmitterDelegate<Ping>()

        emitter.sendEffect(Ping(1))
        emitter.sendEffect(Ping(2))

        emitter.effect.test {
            assertEquals(Ping(1), awaitItem())
            assertEquals(Ping(2), awaitItem())
        }
    }

    @Test
    fun `an effect is delivered to exactly one collector, never replayed`() = runTest {
        val emitter = EffectEmitterDelegate<Ping>()

        emitter.effect.test {
            emitter.sendEffect(Ping(1))
            assertEquals(Ping(1), awaitItem())
        }

        // A SharedFlow(replay = 1) would hand Ping(1) to this second collector too - which
        // is exactly how "the app navigates twice after rotation" bugs happen.
        emitter.effect.test {
            expectNoEvents()
        }
    }

    @Test
    fun `intents are queued in order`() = runTest {
        val receiver = IntentReceiverDelegate<Tap>()

        receiver.onIntent(Tap(1))
        receiver.onIntent(Tap(2))

        receiver.intents.test {
            assertEquals(Tap(1), awaitItem())
            assertEquals(Tap(2), awaitItem())
        }
    }
}

private data class Counter(val value: Int) : MviState
private data class Ping(val id: Int) : MviEffect
private data class Tap(val id: Int) : MviIntent

package com.example.mvi.core.delegate

import com.example.mvi.core.Async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncDelegateTest {

    @Test
    fun `starts idle and ends in success`() = runTest {
        val delegate = AsyncDelegate(this) { "loaded" }

        assertEquals(Async.Idle, delegate.state.value)
        delegate.load()
        advanceUntilIdle()

        assertEquals(Async.Success("loaded"), delegate.state.value)
    }

    @Test
    fun `a failure is captured instead of thrown`() = runTest {
        val delegate = AsyncDelegate<String>(this) { error("nope") }

        delegate.load()
        advanceUntilIdle()

        assertTrue(delegate.state.value is Async.Failure)
        assertEquals("nope", delegate.state.value.errorOrNull?.message)
    }

    @Test
    fun `load is a no-op once loaded, so it is safe to call from init and onResume`() = runTest {
        var calls = 0
        val delegate = AsyncDelegate(this) { calls++; "value" }

        delegate.load()
        advanceUntilIdle()
        delegate.load()
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `retry reloads even after success`() = runTest {
        var calls = 0
        val delegate = AsyncDelegate(this) { "value ${calls++}" }

        delegate.load()
        advanceUntilIdle()
        delegate.retry()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals(Async.Success("value 1"), delegate.state.value)
    }

    @Test
    fun `the previous value survives a reload so the screen does not blink`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val delegate = AsyncDelegate(this) {
            if (calls++ > 0) gate.await()
            "first"
        }

        delegate.load()
        advanceUntilIdle()
        delegate.retry()

        assertEquals(Async.Loading("first"), delegate.state.value)
        assertEquals("first", delegate.state.value.valueOrNull)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a failed refresh keeps the stale value available`() = runTest {
        var shouldFail = false
        val delegate = AsyncDelegate(this) {
            if (shouldFail) error("offline")
            "cached"
        }

        delegate.load()
        advanceUntilIdle()
        shouldFail = true
        delegate.retry()
        advanceUntilIdle()

        assertTrue(delegate.state.value is Async.Failure)
        assertEquals("cached", delegate.state.value.valueOrNull)
    }
}

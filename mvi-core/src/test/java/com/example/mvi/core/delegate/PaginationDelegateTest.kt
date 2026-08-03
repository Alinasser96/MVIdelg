package com.example.mvi.core.delegate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The delegate takes its scope as a constructor parameter, so these tests need no
 * ViewModel, no `Dispatchers.setMain`, and no Android. That portability is the main
 * practical argument for composing behaviour instead of inheriting it.
 */
class PaginationDelegateTest {

    @Test
    fun `refresh loads the first page`() = runTest {
        val delegate = pagingOf(totalItems = 10, pageSize = 3)

        delegate.refresh()
        advanceUntilIdle()

        val state = delegate.state.value
        assertEquals(listOf(0, 1, 2), state.items)
        assertEquals(0, state.page)
        assertFalse(state.isRefreshing)
        assertFalse(state.endReached)
    }

    @Test
    fun `loadNextPage appends instead of replacing`() = runTest {
        val delegate = pagingOf(totalItems = 10, pageSize = 3)

        delegate.refresh()
        advanceUntilIdle()
        delegate.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2, 3, 4, 5), delegate.state.value.items)
        assertEquals(1, delegate.state.value.page)
    }

    @Test
    fun `a short page means the end was reached`() = runTest {
        val delegate = pagingOf(totalItems = 4, pageSize = 3)

        delegate.refresh()
        advanceUntilIdle()
        delegate.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2, 3), delegate.state.value.items)
        assertTrue(delegate.state.value.endReached)
    }

    @Test
    fun `loadNextPage is ignored once the end is reached`() = runTest {
        var calls = 0
        val delegate = PaginationDelegate(this, pageSize = 3) { _, _ ->
            calls++
            listOf(1)
        }

        delegate.refresh()
        advanceUntilIdle()
        delegate.loadNextPage()
        delegate.loadNextPage()
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test
    fun `a second loadNextPage in the same frame does not fetch the page twice`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val delegate = PaginationDelegate(this, pageSize = 3) { page, _ ->
            calls++
            if (page > PaginationDelegate.FIRST_PAGE) gate.await()
            listOf(1, 2, 3)
        }

        delegate.refresh()
        advanceUntilIdle()

        // Two scroll events arriving back to back, before the first load can even start.
        delegate.loadNextPage()
        delegate.loadNextPage()
        advanceUntilIdle()

        assertEquals("refresh + exactly one append", 2, calls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `a failed append keeps the pages already on screen`() = runTest {
        var shouldFail = false
        val delegate = PaginationDelegate(this, pageSize = 3) { page, size ->
            if (shouldFail) throw IllegalStateException("network down")
            List(size) { page * size + it }
        }

        delegate.refresh()
        advanceUntilIdle()
        shouldFail = true
        delegate.loadNextPage()
        advanceUntilIdle()

        val state = delegate.state.value
        assertEquals(listOf(0, 1, 2), state.items)
        assertNotNull(state.error)
        assertFalse(state.isAppending)
    }

    @Test
    fun `retry resumes the page that failed`() = runTest {
        var shouldFail = true
        val delegate = PaginationDelegate(this, pageSize = 3) { page, size ->
            if (shouldFail) throw IllegalStateException("network down")
            List(size) { page * size + it }
        }

        delegate.refresh()
        advanceUntilIdle()
        assertNotNull(delegate.state.value.error)

        shouldFail = false
        delegate.retry()
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2), delegate.state.value.items)
        assertEquals(null, delegate.state.value.error)
    }

    @Test
    fun `reset clears every page`() = runTest {
        val delegate = pagingOf(totalItems = 10, pageSize = 3)

        delegate.refresh()
        advanceUntilIdle()
        delegate.reset()

        assertEquals(PagingState<Int>(), delegate.state.value)
    }

    @Test
    fun `isEmpty is false while the first page is still loading`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val delegate = PaginationDelegate<Int>(this, pageSize = 3) { _, _ ->
            gate.await()
            emptyList()
        }

        delegate.refresh()
        assertFalse(delegate.state.value.isEmpty)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(delegate.state.value.isEmpty)
    }
}

/** A page source over `0 until totalItems`. */
private fun CoroutineScope.pagingOf(
    totalItems: Int,
    pageSize: Int,
) = PaginationDelegate(this, pageSize) { page, size ->
    (0 until totalItems).drop(page * size).take(size)
}

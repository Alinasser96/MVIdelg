package com.example.mvi.core.delegate

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryDelegateTest {

    @Test
    fun `query updates on every keystroke`() = runTest {
        val delegate = SearchQueryDelegate(debounceMillis = 300)

        delegate.onQueryChanged("a")
        delegate.onQueryChanged("an")
        delegate.onQueryChanged("ann")

        assertEquals("ann", delegate.query.value)
    }

    @Test
    fun `debouncedQuery only emits what the user settled on`() = runTest {
        val delegate = SearchQueryDelegate(debounceMillis = 300)

        delegate.debouncedQuery.test {
            delegate.onQueryChanged("a")
            delegate.onQueryChanged("an")
            delegate.onQueryChanged("ann")

            // runTest's virtual clock: no real 300ms is spent here.
            assertEquals("ann", awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `the initial value never triggers a search`() = runTest {
        val delegate = SearchQueryDelegate(debounceMillis = 300, initialQuery = "preset")

        delegate.debouncedQuery.test {
            // Without drop(1) the StateFlow would replay "preset" and duplicate the
            // request the ViewModel already fires in init.
            expectNoEvents()
        }
    }

    @Test
    fun `typing back to the same text does not search twice`() = runTest {
        val delegate = SearchQueryDelegate(debounceMillis = 300)

        delegate.debouncedQuery.test {
            delegate.onQueryChanged("kotlin")
            assertEquals("kotlin", awaitItem())

            delegate.onQueryChanged("kotlinx")
            assertEquals("kotlinx", awaitItem())

            delegate.onQueryChanged("kotlin")
            assertEquals("kotlin", awaitItem())

            expectNoEvents()
        }
    }

    @Test
    fun `clear resets the query`() = runTest {
        val delegate = SearchQueryDelegate(initialQuery = "something")

        delegate.clear()

        assertEquals("", delegate.query.value)
    }
}

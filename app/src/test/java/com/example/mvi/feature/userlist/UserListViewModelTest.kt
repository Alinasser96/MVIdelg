package com.example.mvi.feature.userlist

import app.cash.turbine.test
import com.example.mvi.MainDispatcherRule
import com.example.mvi.data.User
import com.example.mvi.data.UserRepository
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
 * Testing a whole screen: send intents, assert on state and effects.
 *
 * No Robolectric, no Compose test rule, no mocking library, and no `Thread.sleep` — the
 * debounce is skipped with virtual time. The screen's entire behaviour is reachable
 * through the sealed intent type, so these tests can be written straight off the contract.
 */
class UserListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the first page loads on init`() = runTest {
        val viewModel = UserListViewModel(FakeUserRepository(userCount = 50))
        advanceUntilIdle()

        val state = viewModel.currentState
        assertEquals(UserListViewModel.PAGE_SIZE, state.users.size)
        assertFalse(state.isRefreshing)
        assertNull(state.errorMessage)
    }

    @Test
    fun `LoadNextPage appends the next page`() = runTest {
        val viewModel = UserListViewModel(FakeUserRepository(userCount = 50))
        advanceUntilIdle()

        viewModel.onIntent(UserListIntent.LoadNextPage)
        advanceUntilIdle()

        assertEquals(UserListViewModel.PAGE_SIZE * 2, viewModel.currentState.users.size)
    }

    @Test
    fun `the end of the list is reported once every page is in`() = runTest {
        val viewModel = UserListViewModel(FakeUserRepository(userCount = 25))
        advanceUntilIdle()

        viewModel.onIntent(UserListIntent.LoadNextPage)
        advanceUntilIdle()

        assertEquals(25, viewModel.currentState.users.size)
        assertTrue(viewModel.currentState.endReached)
    }

    @Test
    fun `typing updates the field immediately but searches only once`() = runTest {
        val repository = FakeUserRepository(userCount = 50)
        val viewModel = UserListViewModel(repository)
        advanceUntilIdle()
        val queriesBefore = repository.queries.size

        viewModel.onIntent(UserListIntent.QueryChanged("H"))
        viewModel.onIntent(UserListIntent.QueryChanged("Ha"))
        viewModel.onIntent(UserListIntent.QueryChanged("Han"))
        advanceUntilIdle()

        // The text field is already up to date...
        assertEquals("Han", viewModel.currentState.query)
        // ...and exactly one request went out, for the settled query.
        assertEquals(queriesBefore + 1, repository.queries.size)
        assertEquals("Han", repository.queries.last())
    }

    @Test
    fun `a new query restarts paging from the first page`() = runTest {
        val repository = FakeUserRepository(userCount = 50)
        val viewModel = UserListViewModel(repository)
        advanceUntilIdle()
        viewModel.onIntent(UserListIntent.LoadNextPage)
        advanceUntilIdle()
        assertEquals(40, viewModel.currentState.users.size)

        viewModel.onIntent(UserListIntent.QueryChanged("Name"))
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(UserListViewModel.PAGE_SIZE, viewModel.currentState.users.size)
    }

    @Test
    fun `tapping a user emits a navigation effect and does not touch state`() = runTest {
        val viewModel = UserListViewModel(FakeUserRepository(userCount = 50))
        advanceUntilIdle()
        val stateBefore = viewModel.currentState

        viewModel.effect.test {
            viewModel.onIntent(UserListIntent.UserClicked(7))
            advanceUntilIdle()

            assertEquals(UserListEffect.OpenUser(7), awaitItem())
        }
        assertEquals(stateBefore, viewModel.currentState)
    }

    @Test
    fun `a failed first load becomes a blocking error that retry clears`() = runTest {
        val repository = FakeUserRepository(userCount = 50, failing = true)
        val viewModel = UserListViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.currentState.isBlockingError)
        assertEquals("offline", viewModel.currentState.errorMessage)

        repository.failing = false
        viewModel.onIntent(UserListIntent.RetryClicked)
        advanceUntilIdle()

        assertNull(viewModel.currentState.errorMessage)
        assertEquals(UserListViewModel.PAGE_SIZE, viewModel.currentState.users.size)
    }

    @Test
    fun `a failed load-more keeps the list and asks for a snackbar instead`() = runTest {
        val repository = FakeUserRepository(userCount = 50)
        val viewModel = UserListViewModel(repository)
        advanceUntilIdle()

        viewModel.effect.test {
            repository.failing = true
            viewModel.onIntent(UserListIntent.LoadNextPage)
            advanceUntilIdle()

            assertEquals(UserListEffect.ShowMessage("offline"), awaitItem())
        }

        // The pages already on screen survived, so this is not a blocking error.
        assertEquals(UserListViewModel.PAGE_SIZE, viewModel.currentState.users.size)
        assertFalse(viewModel.currentState.isBlockingError)
    }
}

/** Ten lines, no mocking framework — the payoff of depending on an interface. */
private class FakeUserRepository(
    userCount: Int,
    var failing: Boolean = false,
) : UserRepository {

    val queries = mutableListOf<String>()

    private val all = List(userCount) { User(it, "Name $it", "@user$it", "Engineer", "Bio $it") }

    override suspend fun users(query: String, page: Int, pageSize: Int): List<User> {
        queries += query
        if (failing) throw IllegalStateException("offline")
        return all.filter { it.name.contains(query, ignoreCase = true) }
            .drop(page * pageSize)
            .take(pageSize)
    }

    override suspend fun user(id: Int): User = all.first { it.id == id }
}

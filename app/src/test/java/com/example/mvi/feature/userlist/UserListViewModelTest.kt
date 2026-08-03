package com.example.mvi.feature.userlist

import app.cash.turbine.test
import com.example.mvi.MainDispatcherRule
import com.example.mvi.core.analytics.AnalyticsEvent
import com.example.mvi.core.analytics.AnalyticsLogger
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.navigation.NavCommand
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.errors
import com.example.mvi.core.plugins.loading
import com.example.mvi.data.User
import com.example.mvi.data.UserRepository
import com.example.mvi.navigation.AppDestination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Testing a whole screen: send intents, assert on state, plugins, and effects.
 *
 * No Robolectric, no Compose test rule, no mocking library. Loading and errors are
 * asserted on the *plugins*, not on the screen's state — which is exactly where they live.
 */
class UserListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val navigator = ChannelNavigator()
    private val analytics = RecordingAnalyticsLogger()

    private fun viewModel(repository: UserRepository) = UserListViewModel(
        repository = repository,
        dispatcherProvider = TestDispatchers(mainDispatcherRule.testDispatcher),
        pluginDependencies = MviPluginDependencies(
            navigator = navigator,
            analyticsLogger = analytics,
            dispatcherProvider = TestDispatchers(mainDispatcherRule.testDispatcher),
        ),
    )

    @Test
    fun `Load fills the list and clears the spinner`() = runTest {
        val viewModel = viewModel(FakeUserRepository(userCount = 3))

        viewModel.processIntent(UserListIntent.Load)
        advanceUntilIdle()

        assertEquals(3, viewModel.viewState.value.users.size)
        assertFalse(viewModel.loading.isLoading.value)
        assertNull(viewModel.errors.error.value)
    }

    @Test
    fun `a failure lands in the error plugin and the spinner still clears`() = runTest {
        val repository = FakeUserRepository(userCount = 3, failing = true)
        val viewModel = viewModel(repository)

        viewModel.processIntent(UserListIntent.Load)
        advanceUntilIdle()

        assertEquals("offline", viewModel.errors.error.value?.message)
        // withLoading's finally is what guarantees this on the failure path.
        assertFalse(viewModel.loading.isLoading.value)
        assertTrue(viewModel.viewState.value.users.isEmpty())
    }

    @Test
    fun `Retry clears the previous error and reloads`() = runTest {
        val repository = FakeUserRepository(userCount = 3, failing = true)
        val viewModel = viewModel(repository)

        viewModel.processIntent(UserListIntent.Load)
        advanceUntilIdle()
        assertEquals("offline", viewModel.errors.error.value?.message)

        repository.failing = false
        viewModel.processIntent(UserListIntent.Retry)
        advanceUntilIdle()

        assertNull(viewModel.errors.error.value)
        assertEquals(3, viewModel.viewState.value.users.size)
    }

    @Test
    fun `tapping a user navigates through the plugin, not through state or an effect`() = runTest {
        val viewModel = viewModel(FakeUserRepository(userCount = 3))
        val stateBefore = viewModel.viewState.value

        navigator.commands.test {
            viewModel.processIntent(UserListIntent.UserClicked(7))
            advanceUntilIdle()

            assertEquals(NavCommand.To(AppDestination.UserDetail(7)), awaitItem())
        }
        assertEquals(stateBefore, viewModel.viewState.value)
    }

    @Test
    fun `intents are logged through the logging plugin`() = runTest {
        val viewModel = viewModel(FakeUserRepository(userCount = 3))

        viewModel.processIntent(UserListIntent.UserClicked(1))
        advanceUntilIdle()

        assertEquals(
            AnalyticsEvent.Button("user_row", "user_list"),
            analytics.events.single(),
        )
    }

    @Test
    fun `toggling failure updates state and announces it via an effect`() = runTest {
        val viewModel = viewModel(FakeUserRepository(userCount = 3))

        viewModel.effect.test {
            viewModel.processIntent(UserListIntent.SimulateFailureToggled(true))
            advanceUntilIdle()

            assertEquals(UserListEffect.ShowMessage("Next load will fail."), awaitItem())
        }
        assertTrue(viewModel.viewState.value.simulateFailure)
    }
}

// ---- Test doubles ----

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

private class RecordingAnalyticsLogger : AnalyticsLogger {
    val events = mutableListOf<AnalyticsEvent>()
    override suspend fun log(event: AnalyticsEvent) {
        events += event
    }
}

/** Five lines, no mocking framework — the payoff of depending on an interface. */
private class FakeUserRepository(
    userCount: Int,
    var failing: Boolean = false,
) : UserRepository {

    private val all = List(userCount) { User(it, "Name $it", "@user$it", "Engineer", "Bio $it") }

    override suspend fun users(): List<User> {
        if (failing) throw IllegalStateException("offline")
        return all
    }

    override suspend fun user(id: Int): User {
        if (failing) throw IllegalStateException("offline")
        return all.first { it.id == id }
    }
}

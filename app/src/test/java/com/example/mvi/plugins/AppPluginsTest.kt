package com.example.mvi.plugins

import androidx.lifecycle.ViewModel
import app.cash.turbine.test
import com.example.mvi.platform.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four example plugins, each tested on its own.
 *
 * None of them needs a ViewModel, a scope, or Android — they are plain classes that
 * happen to implement `MVIPlugin`. That is the practical payoff of a plugin owning its
 * own state and taking its own dependencies.
 */
class AppPluginsTest {

    // ---- LoadingPlugin ----

    @Test
    fun `withLoading toggles around the block`() = runTest {
        val plugin = LoadingPlugin()
        assertFalse(plugin.isLoading.value)

        plugin.withLoading {
            assertTrue(plugin.isLoading.value)
        }

        assertFalse(plugin.isLoading.value)
    }

    @Test
    fun `withLoading clears the spinner even when the block throws`() = runTest {
        val plugin = LoadingPlugin()

        runCatching { plugin.withLoading { error("boom") } }

        // The regression this guards: a hand-written show()/hide() pair leaves the
        // spinner stuck forever on the failure path.
        assertFalse(plugin.isLoading.value)
    }

    // ---- ErrorPlugin ----

    @Test
    fun `runCatchingError captures the failure instead of throwing`() = runTest {
        val plugin = ErrorPlugin()

        val result = plugin.runCatchingError { error("offline") }

        assertNull(result)
        assertEquals("offline", plugin.error.value?.message)
    }

    @Test
    fun `a successful run clears any previous error`() = runTest {
        val plugin = ErrorPlugin()
        plugin.setError(OperationError("stale"))

        val result = plugin.runCatchingError { "ok" }

        assertEquals("ok", result)
        assertNull(plugin.error.value)
    }

    // ---- NavigationPlugin ----

    @Test
    fun `navigation commands reach the navigator`() = runTest {
        val navigator = ChannelNavigator()
        val plugin = NavigationPlugin(navigator)

        navigator.commands.test {
            plugin.navigateTo(TestDestination("profile"))
            assertEquals(NavCommand.To(TestDestination("profile")), awaitItem())

            plugin.navigateBack()
            assertEquals(NavCommand.Back(null), awaitItem())
        }
    }

    // ---- LoggingPlugin ----

    @Test
    fun `logging attributes every event to the screen given at construction`() = runTest {
        val logger = RecordingAnalyticsLogger()
        val plugin = LoggingPlugin(
            analyticsLogger = logger,
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            screenId = "checkout",
        )
        plugin.onCreate(FakeViewModel(), this)

        plugin.logButtonEvent("pay_clicked")
        plugin.logFieldEvent("card_number")
        advanceUntilIdle()

        // Taking screenId in the constructor is what removed the old configure-me-first
        // step, and with it the chance of shipping events with a blank screen id.
        assertEquals(
            listOf(
                AnalyticsEvent.Button("pay_clicked", "checkout"),
                AnalyticsEvent.Field("card_number", "checkout"),
            ),
            logger.events,
        )
    }
}

// ---- Test doubles ----

private data class TestDestination(override val route: String) : Destination

private class FakeViewModel : ViewModel()

private class RecordingAnalyticsLogger : AnalyticsLogger {
    val events = mutableListOf<AnalyticsEvent>()
    override suspend fun log(event: AnalyticsEvent) {
        events += event
    }
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

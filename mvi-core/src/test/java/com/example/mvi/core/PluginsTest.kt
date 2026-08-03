package com.example.mvi.core

import androidx.lifecycle.ViewModel
import app.cash.turbine.test
import com.example.mvi.core.analytics.AnalyticsEvent
import com.example.mvi.core.error.OperationError
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.navigation.NavCommand
import com.example.mvi.core.plugins.ErrorPluginImpl
import com.example.mvi.core.plugins.LoadingPluginImpl
import com.example.mvi.core.plugins.LoggingPluginImpl
import com.example.mvi.core.plugins.NavigationPluginImpl
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every plugin is a plain class with no ViewModel and no Android in sight, so each one
 * tests on its own. That is the practical payoff of installing capabilities rather than
 * inheriting them.
 */
class PluginsTest {

    // ---- LoadingPlugin ----

    @Test
    fun `withLoading toggles around the block`() = runTest {
        val plugin = LoadingPluginImpl()
        assertFalse(plugin.isLoading.value)

        plugin.withLoading {
            assertTrue(plugin.isLoading.value)
        }

        assertFalse(plugin.isLoading.value)
    }

    @Test
    fun `withLoading clears the spinner even when the block throws`() = runTest {
        val plugin = LoadingPluginImpl()

        runCatching { plugin.withLoading { error("boom") } }

        // The regression this guards: a hand-written show()/hide() pair leaves the
        // spinner stuck forever on the failure path.
        assertFalse(plugin.isLoading.value)
    }

    // ---- ErrorPlugin ----

    @Test
    fun `runCatchingError captures the failure instead of throwing`() = runTest {
        val plugin = ErrorPluginImpl()

        val result = plugin.runCatchingError { error("offline") }

        assertNull(result)
        assertEquals("offline", plugin.error.value?.message)
    }

    @Test
    fun `a successful run clears any previous error`() = runTest {
        val plugin = ErrorPluginImpl()
        plugin.setError(OperationError("stale"))

        val result = plugin.runCatchingError { "ok" }

        assertEquals("ok", result)
        assertNull(plugin.error.value)
    }

    // ---- NavigationPlugin ----

    @Test
    fun `navigation commands reach the navigator`() = runTest {
        val navigator = ChannelNavigator()
        val plugin = NavigationPluginImpl()
        plugin.onCreate(
            FakeViewModel(),
            this,
            testPluginDependencies(StandardTestDispatcher(testScheduler), navigator),
        )

        navigator.commands.test {
            plugin.navigateTo(TestDestination("profile"))
            assertEquals(NavCommand.To(TestDestination("profile")), awaitItem())

            plugin.navigateBack()
            assertEquals(NavCommand.Back(null), awaitItem())
        }
    }

    // ---- LoggingPlugin ----

    @Test
    fun `logging attributes every event to the configured screen`() = runTest {
        val logger = RecordingAnalyticsLogger()
        val plugin = LoggingPluginImpl()
        plugin.onCreate(
            FakeViewModel(),
            this,
            testPluginDependencies(StandardTestDispatcher(testScheduler), analyticsLogger = logger),
        )

        plugin.configure("checkout")
        plugin.logButtonEvent("pay_clicked")
        plugin.logFieldEvent("card_number")
        advanceUntilIdle()

        assertEquals(
            listOf(
                AnalyticsEvent.Button("pay_clicked", "checkout"),
                AnalyticsEvent.Field("card_number", "checkout"),
            ),
            logger.events,
        )
    }

    @Test
    fun `logging before configure fails loudly rather than sending a blank screen id`() = runTest {
        val plugin = LoggingPluginImpl()
        plugin.onCreate(
            FakeViewModel(),
            this,
            testPluginDependencies(StandardTestDispatcher(testScheduler)),
        )

        assertThrows(IllegalStateException::class.java) {
            plugin.logButtonEvent("pay_clicked")
        }
    }
}

private class FakeViewModel : ViewModel()

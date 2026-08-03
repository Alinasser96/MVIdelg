package com.example.mvi

import com.example.mvi.core.Intent
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.NoEffect
import com.example.mvi.core.ViewState
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.navigation.ChannelNavigator
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.pluginOrNull
import com.example.mvi.core.plugins.requirePlugin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Everything here lives in `:app`, not in `:mvi-core` — so this is the real test of
 * whether *a consumer of the library* can add a plugin without editing the library.
 *
 * It reproduces the built-in marker + accessor pattern end to end: a marker interface, an
 * accessor built on `requirePlugin()`, and installation through `additionalPlugins`.
 */
class CustomPluginTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun dispatchers() = TestDispatchers(mainDispatcherRule.testDispatcher)

    private fun dependencies() = MviPluginDependencies(
        navigator = ChannelNavigator(),
        analyticsLogger = { },
        dispatcherProvider = dispatchers(),
    )

    private fun viewModel(guard: GuardPlugin? = null) =
        EditorViewModel(dispatchers(), dependencies(), guard = guard)

    @Test
    fun `a plugin defined outside the library is installed and reachable by its marker`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(EditIntent.Type("a"))
        viewModel.processIntent(EditIntent.Type("ab"))
        advanceUntilIdle()

        assertEquals("ab", viewModel.viewState.value.text)
        // The accessor resolves exactly like the built-in `loading` or `errors` would.
        assertEquals(listOf("", "a"), viewModel.undo.history)
    }

    @Test
    fun `the custom plugin works from inside handleIntent`() = runTest {
        val viewModel = viewModel()

        viewModel.processIntent(EditIntent.Type("a"))
        viewModel.processIntent(EditIntent.Type("ab"))
        viewModel.processIntent(EditIntent.Undo)
        advanceUntilIdle()

        assertEquals("a", viewModel.viewState.value.text)
    }

    @Test
    fun `a custom plugin can consume an intent before handleIntent sees it`() = runTest {
        val guard = GuardPlugin(blocks = { it is EditIntent.Type })
        val viewModel = viewModel(guard)

        viewModel.processIntent(EditIntent.Type("hello"))
        advanceUntilIdle()

        // onIntent returned true, so the ViewModel's `when` never ran for it.
        assertEquals(listOf<Intent>(EditIntent.Type("hello")), guard.blocked)
        assertTrue(viewModel.handledIntents.isEmpty())
        assertEquals("", viewModel.viewState.value.text)
    }

    @Test
    fun `looking up a plugin that was never installed fails with a useful message`() = runTest {
        val viewModel = NoPluginsViewModel(dispatchers(), dependencies())

        assertNull(viewModel.pluginOrNull<UndoPlugin>())

        val error = assertThrows(IllegalStateException::class.java) {
            viewModel.requirePlugin<UndoPlugin>()
        }
        assertTrue(error.message!!.contains("UndoPlugin"))
    }
}

// ---- Step 1: a plugin written entirely outside :mvi-core ----

class UndoPlugin : MVIPlugin {

    val history = mutableListOf<String>()

    override fun onStateChanged(oldState: ViewState, newState: ViewState) {
        if (oldState is EditViewState && newState is EditViewState && oldState.text != newState.text) {
            history += oldState.text
        }
    }

    fun previous(): String = history.removeLastOrNull() ?: ""
}

/** A second plugin, showing the `onIntent` hook claiming an intent. */
class GuardPlugin(private val blocks: (Intent) -> Boolean) : MVIPlugin {

    val blocked = mutableListOf<Intent>()

    override fun onIntent(intent: Intent): Boolean {
        if (!blocks(intent)) return false
        blocked += intent
        return true
    }
}

// ---- Step 2: the marker ----

interface HasUndoPlugin

// ---- Step 3: the accessor, built on the library's requirePlugin() ----

val HasUndoPlugin.undo: UndoPlugin
    get() = (this as MviViewModel<*, *, *>).requirePlugin()

// ---- Step 4: use it ----

private data class EditViewState(val text: String = "") : ViewState

private sealed interface EditIntent : Intent {
    data class Type(val text: String) : EditIntent
    data object Undo : EditIntent
}

private class EditorViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
    // Held as constructor params so they can be passed to the superclass call below.
    undoPlugin: UndoPlugin = UndoPlugin(),
    guard: GuardPlugin? = null,
) : MviViewModel<EditViewState, EditIntent, NoEffect>(
    dispatcherProvider,
    pluginDependencies,
    listOfNotNull(undoPlugin, guard),
),
    HasUndoPlugin {

    val handledIntents = mutableListOf<EditIntent>()

    override fun initialState() = EditViewState()

    override suspend fun handleIntent(intent: EditIntent) {
        handledIntents += intent
        when (intent) {
            is EditIntent.Type -> updateState { copy(text = intent.text) }
            EditIntent.Undo -> updateState { copy(text = undo.previous()) }
        }
    }
}

private class NoPluginsViewModel(
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : MviViewModel<EditViewState, EditIntent, NoEffect>(dispatcherProvider, pluginDependencies) {
    override fun initialState() = EditViewState()
    override suspend fun handleIntent(intent: EditIntent) = Unit
}

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
}

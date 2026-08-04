package com.example.mvi

import com.example.mvi.core.Intent
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.NoEffect
import com.example.mvi.core.ViewState
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.core.plugins.pluginOrNull
import com.example.mvi.core.plugins.requirePlugin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Writing a plugin from scratch, in four steps.
 *
 * `:mvi-core` ships no plugins at all, so this is exactly the same path the app's own
 * `LoadingPlugin` and `NavigationPlugin` took — there is no privileged built-in set to
 * imitate. If this works, anything does.
 */
class CustomPluginTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `a plugin is installed and reachable through its marker`() = runTest {
        val viewModel = EditorViewModel()

        viewModel.processIntent(EditIntent.Type("a"))
        viewModel.processIntent(EditIntent.Type("ab"))
        advanceUntilIdle()

        assertEquals("ab", viewModel.viewState.value.text)
        // The accessor resolves, exactly like the app's `loading` or `errors` do.
        assertEquals(listOf("", "a"), viewModel.undo.history)
    }

    @Test
    fun `the plugin is usable from inside handleIntent`() = runTest {
        val viewModel = EditorViewModel()

        viewModel.processIntent(EditIntent.Type("a"))
        viewModel.processIntent(EditIntent.Type("ab"))
        viewModel.processIntent(EditIntent.Undo)
        advanceUntilIdle()

        assertEquals("a", viewModel.viewState.value.text)
    }

    @Test
    fun `a plugin can consume an intent before handleIntent sees it`() = runTest {
        val guard = GuardPlugin(blocks = { it is EditIntent.Type })
        val viewModel = EditorViewModel(guard = guard)

        viewModel.processIntent(EditIntent.Type("hello"))
        advanceUntilIdle()

        // onIntent returned true, so the ViewModel's `when` never ran for it.
        assertEquals(listOf<Intent>(EditIntent.Type("hello")), guard.blocked)
        assertTrue(viewModel.handledIntents.isEmpty())
        assertEquals("", viewModel.viewState.value.text)
    }

    @Test
    fun `a marker without its plugin fails with a message naming the type`() = runTest {
        // The one thing the compiler cannot check: the marker says the capability is
        // there, but the plugins list disagrees.
        val viewModel = ForgotToInstallViewModel()

        assertNull(viewModel.pluginOrNull<UndoPlugin>())

        val error = assertThrows(IllegalStateException::class.java) {
            viewModel.requirePlugin<UndoPlugin>()
        }
        assertTrue(error.message!!.contains("UndoPlugin"))
    }
}

// ---- Step 1: the plugin ----

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

// ---- Step 3: the accessor ----

val HasUndoPlugin.undo: UndoPlugin
    get() = (this as MviViewModel<*, *, *>).requirePlugin()

// ---- Step 4: install it ----

private data class EditViewState(val text: String = "") : ViewState

private sealed interface EditIntent : Intent {
    data class Type(val text: String) : EditIntent
    data object Undo : EditIntent
}

private class EditorViewModel(
    // Constructor params, so they can be passed to the superclass call below.
    undoPlugin: UndoPlugin = UndoPlugin(),
    guard: GuardPlugin? = null,
) : MviViewModel<EditViewState, EditIntent, NoEffect>(listOfNotNull(undoPlugin, guard)),
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

/** Declares the marker but never installs the plugin. */
private class ForgotToInstallViewModel :
    MviViewModel<EditViewState, EditIntent, NoEffect>(),
    HasUndoPlugin {

    override fun initialState() = EditViewState()
    override suspend fun handleIntent(intent: EditIntent) = Unit
}

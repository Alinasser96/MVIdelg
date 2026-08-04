package com.example.mvi.plugins

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.mvi.core.plugins.MVIPlugin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.reflect.KClass

/** Somewhere the app can navigate to. Features declare their own destinations. */
interface Destination {
    val route: String
}

/** One navigation instruction, queued for whoever owns the back stack. */
sealed interface NavCommand {
    data class To(val destination: Destination) : NavCommand
    data class Back(val results: Map<String, Any?>? = null) : NavCommand
    data class PopUpTo(val route: String, val inclusive: Boolean, val results: Map<String, Any>) : NavCommand
}

/** The seam between a ViewModel and the navigation library. */
interface Navigator {
    val commands: Flow<NavCommand>
    fun send(command: NavCommand)
}

class ChannelNavigator : Navigator {
    private val channel = Channel<NavCommand>(Channel.BUFFERED)
    override val commands: Flow<NavCommand> = channel.receiveAsFlow()
    override fun send(command: NavCommand) {
        channel.trySend(command)
    }
}

/**
 * Example plugin: navigation.
 *
 * The first one with a dependency — and it takes it in its **constructor**, like any
 * ordinary class. There is no dependency bag in `:mvi-core` and no framework involved;
 * the ViewModel that installs the plugin passes the [Navigator] it was given.
 *
 * A ViewModel calls `navigation.navigateTo(...)` and never touches a `NavController`, so
 * it stays a plain JVM object under test.
 */
class NavigationPlugin(private val navigator: Navigator) : MVIPlugin {

    fun navigateTo(destination: Destination) {
        navigator.send(NavCommand.To(destination))
    }

    fun navigateBack(results: Map<String, Any?>? = null) {
        navigator.send(NavCommand.Back(results))
    }

    fun navigateBackWithResults(results: Map<String, Any?>) {
        navigator.send(NavCommand.Back(results))
    }

    fun <T : Any> popUpTo(
        routeClass: KClass<T>,
        results: Map<String, Any> = emptyMap(),
        inclusive: Boolean = false,
    ) {
        navigator.send(
            NavCommand.PopUpTo(
                route = routeClass.qualifiedName.orEmpty(),
                inclusive = inclusive,
                results = results,
            )
        )
    }
}

/**
 * Drains the app-wide [Navigator] into whatever owns the back stack.
 *
 * Collected once, at the NavHost — not per screen.
 */
@Composable
fun CollectNavigation(
    navigator: Navigator,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onCommand: (NavCommand) -> Unit,
) {
    val currentOnCommand by rememberUpdatedState(onCommand)
    LaunchedEffect(navigator, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            navigator.commands.collect { command -> currentOnCommand(command) }
        }
    }
}

package com.example.mvi.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Somewhere the app can navigate to. Features declare their own destinations. */
interface Destination {
    val route: String
}

/** One navigation instruction, queued for whoever owns the back stack. */
sealed interface NavCommand {
    data class To(val destination: Destination) : NavCommand
    data class Back(val results: Map<String, Any?>? = null) : NavCommand
    data class PopUpTo(val route: String, val inclusive: Boolean, val results: Map<String, Any?>) : NavCommand
}

/**
 * The seam between a ViewModel and the navigation library.
 *
 * `NavigationPluginImpl` talks to this, never to a `NavController`, so ViewModels stay
 * testable on the JVM and the app can swap navigation libraries without touching a
 * single feature.
 */
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

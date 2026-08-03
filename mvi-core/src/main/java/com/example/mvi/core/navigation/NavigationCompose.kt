package com.example.mvi.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Drains the app-wide [Navigator] into whatever owns the back stack.
 *
 * Collected once, at the NavHost — not per screen. Individual features only ever call
 * `navigation.navigateTo(...)`, which is why no ViewModel in the sample imports anything
 * from `androidx.navigation`.
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

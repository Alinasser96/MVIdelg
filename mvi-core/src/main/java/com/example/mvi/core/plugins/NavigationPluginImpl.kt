package com.example.mvi.core.plugins

import androidx.lifecycle.ViewModel
import com.example.mvi.core.navigation.Destination
import com.example.mvi.core.navigation.NavCommand
import com.example.mvi.core.navigation.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Plugin for navigation.
 *
 * A ViewModel calls `navigation.navigateTo(...)` and never touches a `NavController`,
 * so it stays a plain JVM object under test. The [Navigator] arrives through
 * [MviPluginDependencies] in [onCreate], which is why nothing has to be passed in by the
 * feature.
 */
class NavigationPluginImpl : MVIPlugin {

    private lateinit var navigator: Navigator

    override fun onCreate(
        viewModel: ViewModel,
        viewModelScope: CoroutineScope,
        dependencies: MviPluginDependencies,
    ) {
        navigator = dependencies.navigator
    }

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

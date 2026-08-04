package com.example.mvi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mvi.plugins.CollectNavigation
import com.example.mvi.plugins.NavCommand
import com.example.mvi.di.ServiceLocator
import com.example.mvi.feature.userdetail.UserDetailRoute
import com.example.mvi.feature.userlist.UserListRoute

/**
 * The single place that knows about `androidx.navigation`.
 *
 * ViewModels call `navigation.navigateTo(...)`, the `NavigationPlugin` turns that into a
 * [NavCommand] on the app-wide `Navigator`, and this collector applies it. No feature
 * holds a `NavHostController`, which is why every ViewModel in the sample is a plain JVM
 * object under test.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    CollectNavigation(ServiceLocator.navigator) { command ->
        when (command) {
            is NavCommand.To -> navController.navigate(command.destination.route)
            is NavCommand.Back -> navController.popBackStack()
            is NavCommand.PopUpTo -> navController.popBackStack(
                route = command.route,
                inclusive = command.inclusive,
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppDestination.ROUTE_USER_LIST,
        modifier = modifier,
    ) {
        composable(AppDestination.ROUTE_USER_LIST) {
            UserListRoute()
        }

        composable(
            route = AppDestination.ROUTE_USER_DETAIL,
            arguments = listOf(navArgument(AppDestination.ARG_USER_ID) { type = NavType.IntType }),
        ) { entry ->
            UserDetailRoute(
                userId = entry.arguments?.getInt(AppDestination.ARG_USER_ID) ?: 0,
            )
        }
    }
}

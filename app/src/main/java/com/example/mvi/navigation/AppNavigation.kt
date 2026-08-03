package com.example.mvi.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mvi.feature.userdetail.UserDetailRoute
import com.example.mvi.feature.userlist.UserListRoute

/**
 * Navigation lives outside the features.
 *
 * A screen never holds a `NavController`; it emits an effect (`OpenUser`, `NavigateBack`)
 * and its Route translates that into a call here. That is why every ViewModel in this
 * project is testable without Android: navigation is a value they emit, not an API they call.
 */
object Destinations {
    const val USER_LIST = "users"
    const val USER_DETAIL = "users/{userId}"
    const val USER_ID_ARG = "userId"

    fun userDetail(userId: Int) = "users/$userId"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.USER_LIST,
        modifier = modifier,
    ) {
        composable(Destinations.USER_LIST) {
            UserListRoute(
                onOpenUser = { userId ->
                    navController.navigate(Destinations.userDetail(userId))
                },
            )
        }

        composable(
            route = Destinations.USER_DETAIL,
            arguments = listOf(navArgument(Destinations.USER_ID_ARG) { type = NavType.IntType }),
        ) { entry ->
            UserDetailRoute(
                userId = entry.arguments?.getInt(Destinations.USER_ID_ARG) ?: 0,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

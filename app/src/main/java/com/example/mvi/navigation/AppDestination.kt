package com.example.mvi.navigation

import com.example.mvi.core.navigation.Destination

/**
 * Where the app can go. Features name a destination; only the NavHost knows how to get
 * there, so no ViewModel ever imports `androidx.navigation`.
 */
sealed class AppDestination(override val route: String) : Destination {

    data object UserList : AppDestination(ROUTE_USER_LIST)

    data class UserDetail(val userId: Int) : AppDestination("users/$userId")

    companion object {
        const val ROUTE_USER_LIST = "users"
        const val ROUTE_USER_DETAIL = "users/{userId}"
        const val ARG_USER_ID = "userId"
    }
}

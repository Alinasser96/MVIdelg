package com.example.mvi.feature.userlist

import com.example.mvi.core.MviEffect
import com.example.mvi.core.MviIntent
import com.example.mvi.core.MviState
import com.example.mvi.data.User

/**
 * The whole screen, described in one file.
 *
 * Read top to bottom you learn: everything the user can do, everything the screen can
 * look like, and everything that can happen once. Nothing else in the feature adds a
 * capability that is not declared here.
 */

sealed interface UserListIntent : MviIntent {
    /** First load and pull-to-refresh. */
    data object Refresh : UserListIntent

    /** Sent on scroll. The delegate decides whether it is actually time to fetch. */
    data object LoadNextPage : UserListIntent

    data object RetryClicked : UserListIntent

    data class QueryChanged(val query: String) : UserListIntent

    data class UserClicked(val userId: Int) : UserListIntent
}

/**
 * Flat and render-ready.
 *
 * Note what is *not* here: no `PagingState`, no `Async`, no delegates. The ViewModel
 * projects those into these plain fields, so the UI never learns that pagination is
 * implemented by a delegate — and swapping that delegate later changes no Composable.
 */
data class UserListState(
    val query: String = "",
    val users: List<User> = emptyList(),
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
) : MviState {

    /** True only for a genuinely empty result — not while loading and not after an error. */
    val isEmpty: Boolean get() = users.isEmpty() && !isRefreshing && errorMessage == null

    /** A full-screen error, as opposed to a toast over a list that still has content. */
    val isBlockingError: Boolean get() = errorMessage != null && users.isEmpty()
}

sealed interface UserListEffect : MviEffect {
    data class OpenUser(val userId: Int) : UserListEffect

    /** Used when a *load more* fails: the list is still on screen, so a snackbar is enough. */
    data class ShowMessage(val message: String) : UserListEffect
}

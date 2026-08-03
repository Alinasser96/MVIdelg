package com.example.mvi.feature.userlist

import com.example.mvi.core.Effect
import com.example.mvi.core.Intent
import com.example.mvi.core.ViewState
import com.example.mvi.data.User

/**
 * The whole screen, described in one file.
 *
 * Note what is **not** in [UserListViewState]: no `isLoading`, no `errorMessage`. Those
 * belong to the `LoadingPlugin` and `ErrorPlugin`, which every screen installs rather
 * than re-declares. A screen's state holds only what is genuinely its own.
 */

sealed interface UserListIntent : Intent {
    data object Load : UserListIntent
    data object Retry : UserListIntent
    data class UserClicked(val userId: Int) : UserListIntent
    data class SimulateFailureToggled(val enabled: Boolean) : UserListIntent
}

data class UserListViewState(
    val users: List<User> = emptyList(),
    val simulateFailure: Boolean = false,
) : ViewState

/**
 * Effects are for one-shot UI events only. Navigation is deliberately absent — it goes
 * through `NavigationPlugin`, not through an effect.
 */
sealed interface UserListEffect : Effect {
    data class ShowMessage(val message: String) : UserListEffect
}

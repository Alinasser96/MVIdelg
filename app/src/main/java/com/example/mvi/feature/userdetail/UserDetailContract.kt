package com.example.mvi.feature.userdetail

import com.example.mvi.core.Intent
import com.example.mvi.core.ViewState
import com.example.mvi.data.User

sealed interface UserDetailIntent : Intent {
    data object Load : UserDetailIntent
    data object Retry : UserDetailIntent
    data object BackClicked : UserDetailIntent
}

data class UserDetailViewState(
    val user: User? = null,
) : ViewState

// No Effect type is declared: this screen has no one-shot UI events, so it uses the
// shared `NoEffect` marker in its MviViewModel signature instead of an empty sealed
// interface nobody implements.

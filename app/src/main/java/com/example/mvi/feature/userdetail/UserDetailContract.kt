package com.example.mvi.feature.userdetail

import com.example.mvi.core.MviEffect
import com.example.mvi.core.MviIntent
import com.example.mvi.core.MviState
import com.example.mvi.data.User

sealed interface UserDetailIntent : MviIntent {
    data object RetryClicked : UserDetailIntent
    data object BackClicked : UserDetailIntent
    data object ShareClicked : UserDetailIntent
}

data class UserDetailState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) : MviState

sealed interface UserDetailEffect : MviEffect {
    data object NavigateBack : UserDetailEffect
    data class Share(val text: String) : UserDetailEffect
}

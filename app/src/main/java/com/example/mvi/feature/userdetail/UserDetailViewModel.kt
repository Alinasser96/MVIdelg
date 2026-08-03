package com.example.mvi.feature.userdetail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.delegate.AsyncDelegate
import com.example.mvi.data.UserRepository
import com.example.mvi.di.ServiceLocator
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The same three steps as [com.example.mvi.feature.userlist.UserListViewModel] — compose,
 * project, route — with a different delegate.
 *
 * That is the payoff of composition: two screens with nothing in common behaviourally
 * still read identically, because neither of them inherited behaviour it did not ask for.
 */
class UserDetailViewModel(
    userId: Int,
    repository: UserRepository,
) : MviViewModel<UserDetailIntent, UserDetailState, UserDetailEffect>(UserDetailState()) {

    private val user = AsyncDelegate(viewModelScope) { repository.user(userId) }

    init {
        user.state
            .onEach { async ->
                updateState {
                    copy(
                        user = async.valueOrNull,
                        isLoading = async.isLoading,
                        errorMessage = async.errorOrNull?.message,
                    )
                }
            }
            .launchIn(viewModelScope)

        user.load()
    }

    override suspend fun handleIntent(intent: UserDetailIntent) {
        when (intent) {
            UserDetailIntent.RetryClicked -> user.retry()
            UserDetailIntent.BackClicked -> sendEffect(UserDetailEffect.NavigateBack)
            UserDetailIntent.ShareClicked -> {
                // Reading currentState to build an effect is exactly what it is for.
                val current = currentState.user ?: return
                sendEffect(UserDetailEffect.Share("${current.name} (${current.handle})"))
            }
        }
    }

    companion object {
        fun factory(userId: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer { UserDetailViewModel(userId, ServiceLocator.userRepository) }
        }
    }
}

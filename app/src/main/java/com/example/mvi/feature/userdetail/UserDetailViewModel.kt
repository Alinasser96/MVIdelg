package com.example.mvi.feature.userdetail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.NoEffect
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.plugins.HasErrorPlugin
import com.example.mvi.core.plugins.HasLoadingPlugin
import com.example.mvi.core.plugins.HasNavigationPlugin
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.errors
import com.example.mvi.core.plugins.loading
import com.example.mvi.core.plugins.navigation
import com.example.mvi.data.UserRepository
import com.example.mvi.di.ServiceLocator

/**
 * A screen that opts into **three** plugins, not four.
 *
 * There is no `HasLoggingPlugin` here, so no `LoggingPluginImpl` is constructed for this
 * ViewModel and `logging` does not resolve inside this class at all — it is a compile
 * error, not a null. That is the whole argument for markers over a fat base class: a
 * screen carries exactly the capabilities it asked for, enforced by the compiler.
 */
class UserDetailViewModel(
    private val userId: Int,
    private val repository: UserRepository,
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : MviViewModel<UserDetailViewState, UserDetailIntent, NoEffect>(
    dispatcherProvider,
    pluginDependencies,
),
    HasLoadingPlugin,
    HasErrorPlugin,
    HasNavigationPlugin {

    override fun initialState() = UserDetailViewState()

    override suspend fun handleIntent(intent: UserDetailIntent) {
        when (intent) {
            UserDetailIntent.Load, UserDetailIntent.Retry -> loadUser()
            UserDetailIntent.BackClicked -> navigation.navigateBack()
        }
    }

    private suspend fun loadUser() {
        loading.withLoading {
            val user = errors.runCatchingError { repository.user(userId) } ?: return@withLoading
            updateState { copy(user = user) }
        }
    }

    companion object {
        fun factory(userId: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UserDetailViewModel(
                    userId = userId,
                    repository = ServiceLocator.userRepository,
                    dispatcherProvider = ServiceLocator.dispatcherProvider,
                    pluginDependencies = ServiceLocator.pluginDependencies,
                )
            }
        }
    }
}

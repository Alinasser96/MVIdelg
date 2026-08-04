package com.example.mvi.feature.userdetail

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mvi.core.plugins.PluggableMviViewModel
import com.example.mvi.core.NoEffect
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.data.UserRepository
import com.example.mvi.di.ServiceLocator
import com.example.mvi.plugins.ErrorPlugin
import com.example.mvi.plugins.HasErrorPlugin
import com.example.mvi.plugins.HasLoadingPlugin
import com.example.mvi.plugins.HasNavigationPlugin
import com.example.mvi.plugins.LoadingPlugin
import com.example.mvi.plugins.NavigationPlugin
import com.example.mvi.plugins.errors
import com.example.mvi.plugins.loading
import com.example.mvi.plugins.navigation

/**
 * A screen that installs **three** plugins, not four.
 *
 * There is no `HasLoggingPlugin` here and no `LoggingPlugin` in the list, so `logging`
 * does not resolve inside this class at all — it is a compile error, not a null. That is
 * the whole argument for markers: a screen carries exactly the capabilities it asked for,
 * enforced by the compiler.
 *
 * It also builds its plugin list by hand instead of calling `standardPlugins()`, which is
 * all that helper saves you.
 */
class UserDetailViewModel(
    private val userId: Int,
    private val repository: UserRepository,
    plugins: List<MVIPlugin>,
) : PluggableMviViewModel<UserDetailViewState, UserDetailIntent, NoEffect>(plugins),
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
                    plugins = listOf(
                        LoadingPlugin(),
                        ErrorPlugin(),
                        NavigationPlugin(ServiceLocator.navigator),
                    ),
                )
            }
        }
    }
}

package com.example.mvi.feature.userlist

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mvi.core.plugins.PluggableMviViewModel
import com.example.mvi.core.plugins.MVIPlugin
import com.example.mvi.data.FakeUserRepository
import com.example.mvi.data.UserRepository
import com.example.mvi.di.ServiceLocator
import com.example.mvi.navigation.AppDestination
import com.example.mvi.plugins.HasErrorPlugin
import com.example.mvi.plugins.HasLoadingPlugin
import com.example.mvi.plugins.HasLoggingPlugin
import com.example.mvi.plugins.HasNavigationPlugin
import com.example.mvi.plugins.errors
import com.example.mvi.plugins.loading
import com.example.mvi.plugins.logging
import com.example.mvi.plugins.navigation
import com.example.mvi.plugins.standardPlugins

/**
 * A screen that installs all four of this app's plugins.
 *
 * The `plugins` argument and the four markers are the whole wiring. Nothing is registered
 * anywhere, and `:mvi-core` knows about none of these capabilities — they are this app's
 * code, sitting in `com.example.mvi.plugins`.
 *
 * `handleIntent` is `suspend` and intents are serialized, so the repository is awaited
 * directly. No `viewModelScope.launch` anywhere in this file.
 */
class UserListViewModel(
    private val repository: UserRepository,
    plugins: List<MVIPlugin>,
) : PluggableMviViewModel<UserListViewState, UserListIntent, UserListEffect>(plugins),
    HasLoadingPlugin,
    HasErrorPlugin,
    HasNavigationPlugin,
    HasLoggingPlugin {

    override fun initialState() = UserListViewState()

    override suspend fun handleIntent(intent: UserListIntent) {
        when (intent) {
            UserListIntent.Load -> loadUsers()

            UserListIntent.Retry -> {
                logging.logButtonEvent("retry")
                loadUsers()
            }

            is UserListIntent.UserClicked -> {
                logging.logButtonEvent("user_row")
                // Navigation is a plugin call, not an effect the UI has to interpret.
                navigation.navigateTo(AppDestination.UserDetail(intent.userId))
            }

            is UserListIntent.SimulateFailureToggled -> {
                (repository as? FakeUserRepository)?.simulateFailure = intent.enabled
                updateState { copy(simulateFailure = intent.enabled) }
                emitEffect(
                    UserListEffect.ShowMessage(
                        if (intent.enabled) "Next load will fail." else "Failures disabled."
                    )
                )
            }
        }
    }

    /**
     * The shape worth copying: `withLoading` guarantees the spinner clears even on
     * failure, and `runCatchingError` routes the throwable into the error plugin so this
     * screen never declares an error field of its own.
     */
    private suspend fun loadUsers() {
        loading.withLoading {
            val users = errors.runCatchingError { repository.users() } ?: return@withLoading
            updateState { copy(users = users) }
        }
    }

    companion object {
        private const val SCREEN_ID = "user_list"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UserListViewModel(
                    repository = ServiceLocator.userRepository,
                    plugins = standardPlugins(SCREEN_ID),
                )
            }
        }
    }
}

package com.example.mvi.feature.userlist

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.helpers.DispatcherProvider
import com.example.mvi.core.plugins.HasErrorPlugin
import com.example.mvi.core.plugins.HasLoadingPlugin
import com.example.mvi.core.plugins.HasLoggingPlugin
import com.example.mvi.core.plugins.HasNavigationPlugin
import com.example.mvi.core.plugins.MviPluginDependencies
import com.example.mvi.core.plugins.configureLogging
import com.example.mvi.core.plugins.errors
import com.example.mvi.core.plugins.loading
import com.example.mvi.core.plugins.logging
import com.example.mvi.core.plugins.navigation
import com.example.mvi.data.FakeUserRepository
import com.example.mvi.data.UserRepository
import com.example.mvi.di.ServiceLocator
import com.example.mvi.navigation.AppDestination

/**
 * A screen that opts into all four plugins.
 *
 * The four marker interfaces on the class declaration *are* the wiring — nothing is
 * constructed, passed in, or registered. In exchange, `loading`, `errors`, `navigation`
 * and `logging` are all in scope below.
 *
 * `handleIntent` is `suspend` and intents are serialized, so the repository is awaited
 * directly. No `viewModelScope.launch` anywhere in this file.
 */
class UserListViewModel(
    private val repository: UserRepository,
    dispatcherProvider: DispatcherProvider,
    pluginDependencies: MviPluginDependencies,
) : MviViewModel<UserListViewState, UserListIntent, UserListEffect>(
    dispatcherProvider,
    pluginDependencies,
),
    HasLoadingPlugin,
    HasErrorPlugin,
    HasNavigationPlugin,
    HasLoggingPlugin {

    init {
        configureLogging(SCREEN_ID)
    }

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
                    dispatcherProvider = ServiceLocator.dispatcherProvider,
                    pluginDependencies = ServiceLocator.pluginDependencies,
                )
            }
        }
    }
}

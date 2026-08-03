package com.example.mvi.feature.userlist

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.mvi.core.MviViewModel
import com.example.mvi.core.delegate.PaginationDelegate
import com.example.mvi.core.delegate.SearchQueryDelegate
import com.example.mvi.data.UserRepository
import com.example.mvi.di.ServiceLocator
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A paged, searchable list — in about 60 lines, none of which are a state machine.
 *
 * The shape to copy for any list screen in the app:
 *
 * 1. **Compose** the delegates you need as private fields.
 * 2. **Project** each delegate's state into this screen's [UserListState] in `init`.
 * 3. **Route** intents to delegates in [handleIntent]. Every branch is one line.
 *
 * Everything hard — cancelling in-flight pages, not double-fetching, keeping the list on
 * screen when an append fails, debouncing the search — lives in `:mvi-core` and is
 * already unit-tested there. What is left here is only what makes *this* screen itself.
 */
class UserListViewModel(
    repository: UserRepository,
) : MviViewModel<UserListIntent, UserListState, UserListEffect>(UserListState()) {

    // 1. Compose.
    private val search = SearchQueryDelegate(debounceMillis = 300)

    private val paging = PaginationDelegate(
        scope = viewModelScope,
        pageSize = PAGE_SIZE,
        // Reads the *live* query, so the delegate never needs to know a search box exists.
        loader = { page, pageSize -> repository.users(search.query.value, page, pageSize) },
    )

    init {
        // 2. Project delegate state into screen state. This is the seam that keeps
        //    UserListState flat: the UI sees fields, not delegates.
        paging.state
            .onEach { paging ->
                val hadItems = currentState.users.isNotEmpty()
                updateState {
                    copy(
                        users = paging.items,
                        isRefreshing = paging.isRefreshing,
                        isAppending = paging.isAppending,
                        endReached = paging.endReached,
                        errorMessage = paging.error?.userMessage(),
                    )
                }
                // A failure with a list already on screen is a snackbar, not an error page.
                val error = paging.error
                if (error != null && hadItems) {
                    sendEffect(UserListEffect.ShowMessage(error.userMessage()))
                }
            }
            .launchIn(viewModelScope)

        search.query
            .onEach { query -> updateState { copy(query = query) } }
            .launchIn(viewModelScope)

        // A settled query restarts pagination from page 0.
        search.debouncedQuery
            .onEach {
                paging.reset()
                paging.refresh()
            }
            .launchIn(viewModelScope)

        paging.refresh()
    }

    // 3. Route. Note how little happens here - and that nothing suspends, so the intent
    //    queue is never blocked by a slow network call.
    override suspend fun handleIntent(intent: UserListIntent) {
        when (intent) {
            UserListIntent.Refresh -> paging.refresh()
            UserListIntent.LoadNextPage -> paging.loadNextPage()
            UserListIntent.RetryClicked -> paging.retry()
            is UserListIntent.QueryChanged -> search.onQueryChanged(intent.query)
            is UserListIntent.UserClicked -> sendEffect(UserListEffect.OpenUser(intent.userId))
        }
    }

    companion object {
        const val PAGE_SIZE = 20

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { UserListViewModel(ServiceLocator.userRepository) }
        }
    }
}

/** Maps a throwable to something worth showing a human. In a real app this is its own mapper. */
private fun Throwable.userMessage(): String = message ?: "Something went wrong."

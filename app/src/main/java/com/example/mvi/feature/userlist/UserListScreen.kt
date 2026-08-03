package com.example.mvi.feature.userlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mvi.core.compose.CollectEffects
import com.example.mvi.data.User
import com.example.mvi.ui.theme.MviTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * **Stateful half.** Owns the ViewModel, collects state and effects, and translates
 * effects into things only the host can do (navigate, show a snackbar).
 *
 * Splitting the screen in two is not ceremony: it is what makes the half below
 * previewable, screenshot-testable, and readable without a ViewModel in scope.
 */
@Composable
fun UserListRoute(
    onOpenUser: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = viewModel(factory = UserListViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is UserListEffect.OpenUser -> onOpenUser(effect.userId)
            is UserListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    UserListScreen(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * **Stateless half.** A pure function of [UserListState] whose only output is
 * [UserListIntent].
 *
 * It has no idea that pagination is a delegate, that search is debounced, or that a
 * repository exists. Every callback is `onIntent(SomethingHappened)` — the UI reports
 * facts and never decides what they mean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    state: UserListState,
    onIntent: (UserListIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()

    // Scrolling near the bottom is a *fact* the UI reports. Whether that should fetch
    // anything is the delegate's decision, made in the ViewModel.
    EndOfListEffect(listState, threshold = 4) { onIntent(UserListIntent.LoadNextPage) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Team") },
                actions = {
                    TextButton(
                        onClick = { onIntent(UserListIntent.Refresh) },
                        enabled = !state.isRefreshing,
                    ) { Text("Refresh") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(UserListIntent.QueryChanged(it)) },
                label = { Text("Search") },
                supportingText = { Text("Type \"fail\" to see the error and retry path.") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                state.isBlockingError -> ErrorPane(
                    message = state.errorMessage.orEmpty(),
                    onRetry = { onIntent(UserListIntent.RetryClicked) },
                )

                state.isRefreshing && state.users.isEmpty() -> CenteredProgress()

                state.isEmpty -> CenteredMessage("Nobody matches \"${state.query}\".")

                else -> UserList(
                    state = state,
                    listState = listState,
                    onUserClick = { onIntent(UserListIntent.UserClicked(it)) },
                )
            }
        }
    }
}

@Composable
private fun UserList(
    state: UserListState,
    listState: LazyListState,
    onUserClick: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = state.users, key = { it.id }) { user ->
            UserRow(user = user, onClick = { onUserClick(user.id) })
            HorizontalDivider()
        }

        if (state.isAppending) {
            item { CenteredProgress(fillParent = false) }
        }

        if (state.endReached && state.users.isNotEmpty()) {
            item { CenteredMessage("That's everyone.", fillParent = false) }
        }
    }
}

@Composable
private fun UserRow(user: User, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = user.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${user.handle} · ${user.role}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorPane(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun CenteredProgress(fillParent: Boolean = true) {
    CenteredBox(fillParent) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
}

@Composable
private fun CenteredMessage(text: String, fillParent: Boolean = true) {
    CenteredBox(fillParent) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenteredBox(fillParent: Boolean, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillParent) Modifier.fillMaxSize() else Modifier.padding(vertical = 16.dp)),
    ) {
        content()
    }
}

/** Fires [onReachEnd] when the last visible item is within [threshold] of the end. */
@Composable
private fun EndOfListEffect(
    listState: LazyListState,
    threshold: Int,
    onReachEnd: () -> Unit,
) {
    LaunchedEffect(listState, threshold) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index
            val total = layout.totalItemsCount
            if (lastVisible != null && total > 0 && lastVisible >= total - threshold) total else null
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { onReachEnd() }
    }
}

@Preview(showBackground = true)
@Composable
private fun UserListPreview() {
    MviTheme {
        UserListScreen(
            state = UserListState(
                query = "an",
                users = listOf(
                    User(1, "Hana Khalil", "@hana1", "Designer", ""),
                    User(2, "Omar Mansour", "@omar2", "Android Engineer", ""),
                ),
                isAppending = true,
            ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserListErrorPreview() {
    MviTheme {
        // Previewing an error state costs one line, because the screen is a function of state.
        UserListScreen(
            state = UserListState(errorMessage = "Could not reach the server."),
            onIntent = {},
        )
    }
}

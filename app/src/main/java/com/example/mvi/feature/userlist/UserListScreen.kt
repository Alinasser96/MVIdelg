package com.example.mvi.feature.userlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mvi.core.compose.CollectEffects
import com.example.mvi.plugins.OperationError
import com.example.mvi.plugins.errors
import com.example.mvi.plugins.loading
import com.example.mvi.data.User
import com.example.mvi.ui.theme.MviTheme

/**
 * **Stateful half.** Owns the ViewModel and collects the three streams a plugin-based
 * screen has: its own `viewState`, plus `loading.isLoading` and `errors.error` from the
 * installed plugins.
 *
 * Those last two are the visible cost of moving loading and errors out of `ViewState` —
 * and the reason no feature ever writes an `isLoading` flag again.
 */
@Composable
fun UserListRoute(
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = viewModel(factory = UserListViewModel.Factory),
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val isLoading by viewModel.loading.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.errors.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            is UserListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    LaunchedEffect(Unit) { viewModel.processIntent(UserListIntent.Load) }

    UserListScreen(
        state = state,
        isLoading = isLoading,
        error = error,
        onIntent = viewModel::processIntent,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * **Stateless half.** A pure function of its inputs whose only output is a
 * [UserListIntent]. No ViewModel, no plugins, no repository — which is what makes it
 * previewable and screenshot-testable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    state: UserListViewState,
    isLoading: Boolean,
    error: OperationError?,
    onIntent: (UserListIntent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Team") }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            SimulateFailureRow(
                enabled = state.simulateFailure,
                onToggle = { onIntent(UserListIntent.SimulateFailureToggled(it)) },
            )
            HorizontalDivider()

            when {
                // Order matters: keep the list on screen while a reload runs.
                state.users.isNotEmpty() -> UserList(
                    users = state.users,
                    onUserClick = { onIntent(UserListIntent.UserClicked(it)) },
                )

                isLoading -> Centered { CircularProgressIndicator() }

                error != null -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.message)
                        TextButton(onClick = { onIntent(UserListIntent.Retry) }) { Text("Retry") }
                    }
                }

                else -> Centered { Text("Nobody here yet.") }
            }
        }
    }
}

@Composable
private fun SimulateFailureRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Simulate failure", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Exercises ErrorPlugin and the retry path.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun UserList(users: List<User>, onUserClick: (Int) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = users, key = { it.id }) { user ->
            UserRow(user = user, onClick = { onUserClick(user.id) })
            HorizontalDivider()
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
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Preview(showBackground = true)
@Composable
private fun UserListPreview() {
    MviTheme {
        UserListScreen(
            state = UserListViewState(
                users = listOf(
                    User(1, "Hana Khalil", "@hana1", "Designer", ""),
                    User(2, "Omar Mansour", "@omar2", "Android Engineer", ""),
                ),
            ),
            isLoading = false,
            error = null,
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserListErrorPreview() {
    MviTheme {
        // Previewing the error state costs one line, because the screen is a pure
        // function of (state, isLoading, error).
        UserListScreen(
            state = UserListViewState(simulateFailure = true),
            isLoading = false,
            error = OperationError("Could not reach the server."),
            onIntent = {},
        )
    }
}

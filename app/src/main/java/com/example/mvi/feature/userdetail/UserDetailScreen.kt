package com.example.mvi.feature.userdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mvi.plugins.OperationError
import com.example.mvi.plugins.errors
import com.example.mvi.plugins.loading
import com.example.mvi.data.User
import com.example.mvi.ui.theme.MviTheme

@Composable
fun UserDetailRoute(
    userId: Int,
    modifier: Modifier = Modifier,
    viewModel: UserDetailViewModel = viewModel(
        // Keyed by id, so opening a different user builds a different ViewModel rather
        // than reusing the previous one's already-loaded state.
        key = "user-$userId",
        factory = UserDetailViewModel.factory(userId),
    ),
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val isLoading by viewModel.loading.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.errors.error.collectAsStateWithLifecycle()

    LaunchedEffect(userId) { viewModel.processIntent(UserDetailIntent.Load) }

    // No CollectEffects here: this screen's Effect type is NoEffect, and back navigation
    // goes through the NavigationPlugin rather than through the UI.
    UserDetailScreen(
        state = state,
        isLoading = isLoading,
        error = error,
        onIntent = viewModel::processIntent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    state: UserDetailViewState,
    isLoading: Boolean,
    error: OperationError?,
    onIntent: (UserDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.user?.name ?: "Profile") },
                navigationIcon = {
                    TextButton(onClick = { onIntent(UserDetailIntent.BackClicked) }) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.user != null -> UserCard(state.user)
                isLoading -> CircularProgressIndicator()
                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.message)
                    TextButton(onClick = { onIntent(UserDetailIntent.Retry) }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User) {
    Column(
        modifier = Modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = user.name, style = MaterialTheme.typography.headlineSmall)
        Text(text = user.handle, style = MaterialTheme.typography.titleMedium)
        Text(
            text = user.bio,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserDetailPreview() {
    MviTheme {
        UserDetailScreen(
            state = UserDetailViewState(
                user = User(1, "Hana Khalil", "@hana1", "Designer", "Designer #1. Joined in 2019."),
            ),
            isLoading = false,
            error = null,
            onIntent = {},
        )
    }
}

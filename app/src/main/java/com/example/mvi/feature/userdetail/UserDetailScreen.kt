package com.example.mvi.feature.userdetail

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mvi.core.compose.CollectEffects
import com.example.mvi.data.User
import com.example.mvi.ui.theme.MviTheme

@Composable
fun UserDetailRoute(
    userId: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserDetailViewModel = viewModel(
        // Keyed by id so navigating to a different user builds a different ViewModel
        // instead of reusing the previous one's already-loaded state.
        key = "user-$userId",
        factory = UserDetailViewModel.factory(userId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    CollectEffects(viewModel.effect) { effect ->
        when (effect) {
            UserDetailEffect.NavigateBack -> onBack()
            is UserDetailEffect.Share -> {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, effect.text)
                }
                context.startActivity(Intent.createChooser(share, null))
            }
        }
    }

    UserDetailScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    state: UserDetailState,
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
                actions = {
                    TextButton(
                        onClick = { onIntent(UserDetailIntent.ShareClicked) },
                        enabled = state.user != null,
                    ) { Text("Share") }
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
                // Order matters: a stale user is still shown while a retry is in flight,
                // because AsyncDelegate carried the previous value into Loading.
                state.user != null -> UserCard(state.user)
                state.isLoading -> CircularProgressIndicator()
                state.errorMessage != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.errorMessage)
                    TextButton(onClick = { onIntent(UserDetailIntent.RetryClicked) }) {
                        Text("Retry")
                    }
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
            state = UserDetailState(
                user = User(1, "Hana Khalil", "@hana1", "Designer", "Designer #1. Joined in 2019."),
            ),
            onIntent = {},
        )
    }
}

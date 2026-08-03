package com.example.mvi.core.plugins

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plugin for managing loading states.
 *
 * Note that loading lives *here*, not in any screen's `ViewState`. That is the point: no
 * feature ever declares `isLoading` again, and no feature can forget to clear it.
 */
class LoadingPluginImpl : MVIPlugin {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun show() {
        _isLoading.value = true
    }

    fun hide() {
        _isLoading.value = false
    }

    /**
     * Execute a block with loading state management.
     *
     * The `finally` is the reason to always prefer this over `show()`/`hide()` by hand:
     * the spinner is cleared even when the block throws or the coroutine is cancelled.
     */
    suspend fun <R> withLoading(block: suspend () -> R): R {
        return try {
            show()
            block()
        } finally {
            hide()
        }
    }
}

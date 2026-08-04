package com.example.mvi.plugins

import com.example.mvi.core.plugins.MVIPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Example plugin: loading state.
 *
 * The simplest possible one — no constructor dependencies, and only the fact that it
 * implements [MVIPlugin] connects it to the loop. It overrides no hooks at all; it just
 * wants to be installed so a marker and accessor can reach it.
 *
 * Note loading lives *here*, not in any screen's `ViewState`. That is the point: no
 * feature declares `isLoading` again, and no feature can forget to clear it.
 */
class LoadingPlugin : MVIPlugin {

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
     * The `finally` is the reason to always prefer this over [show]/[hide] by hand: the
     * spinner clears even when the block throws or the coroutine is cancelled.
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

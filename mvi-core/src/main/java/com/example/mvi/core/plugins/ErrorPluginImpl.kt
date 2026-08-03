package com.example.mvi.core.plugins

import com.example.mvi.core.error.OperationError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plugin for managing error states.
 */
class ErrorPluginImpl : MVIPlugin {

    private val _error = MutableStateFlow<OperationError?>(null)
    val error: StateFlow<OperationError?> = _error.asStateFlow()

    fun setError(error: OperationError?) {
        _error.value = error
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Runs [block], routing any failure into [error] instead of throwing.
     *
     * Returns null on failure, so a caller can `?: return` and skip the success path.
     */
    suspend fun <R> runCatchingError(block: suspend () -> R): R? = try {
        clearError()
        block()
    } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        setError(OperationError.from(throwable))
        null
    }
}

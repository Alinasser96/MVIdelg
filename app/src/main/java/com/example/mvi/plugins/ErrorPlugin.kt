package com.example.mvi.plugins

import com.example.mvi.core.plugins.MVIPlugin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** A failure worth showing a human, as opposed to a raw [Throwable]. */
data class OperationError(
    val message: String,
    val cause: Throwable? = null,
) {
    companion object {
        fun from(throwable: Throwable): OperationError =
            OperationError(throwable.message ?: "Something went wrong.", throwable)
    }
}

/**
 * Example plugin: error state.
 *
 * Like [LoadingPlugin], it holds state the whole app shares rather than something any one
 * screen owns — which is why no `ViewState` in the sample declares an `errorMessage`.
 */
class ErrorPlugin : MVIPlugin {

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
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        setError(OperationError.from(throwable))
        null
    }
}

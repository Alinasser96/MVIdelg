package com.example.mvi.core.error

/**
 * A failure worth showing a human, as opposed to a raw [Throwable].
 *
 * Held by `ErrorPluginImpl` rather than by any screen's [com.example.mvi.core.ViewState],
 * which is what lets error handling be installed rather than re-declared per screen.
 */
data class OperationError(
    val message: String,
    val cause: Throwable? = null,
) {
    companion object {
        fun from(throwable: Throwable): OperationError =
            OperationError(throwable.message ?: "Something went wrong.", throwable)
    }
}

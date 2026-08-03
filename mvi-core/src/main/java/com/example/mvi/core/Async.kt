package com.example.mvi.core

/**
 * The four states any one-shot load can be in.
 *
 * Modelling loading as a sealed type instead of three loose booleans
 * (`isLoading` / `data` / `error`) makes illegal combinations unrepresentable: you can
 * never be "loading and failed", and you can never read `data` without having proven
 * it is there.
 */
sealed interface Async<out T> {

    /** Nothing has been requested yet. */
    data object Idle : Async<Nothing>

    /** In flight. Carries the previous value, if any, so the UI can keep showing it. */
    data class Loading<out T>(val previous: T? = null) : Async<T>

    /** Finished with a value. */
    data class Success<out T>(val value: T) : Async<T>

    /** Finished with an error. Carries the previous value so a refresh failure can keep the list on screen. */
    data class Failure<out T>(val error: Throwable, val previous: T? = null) : Async<T>

    /** The value if we have one, whether it is fresh, stale-while-loading, or stale-after-failure. */
    val valueOrNull: T?
        get() = when (this) {
            is Success -> value
            is Loading -> previous
            is Failure -> previous
            Idle -> null
        }

    val isLoading: Boolean get() = this is Loading

    val errorOrNull: Throwable? get() = (this as? Failure)?.error
}

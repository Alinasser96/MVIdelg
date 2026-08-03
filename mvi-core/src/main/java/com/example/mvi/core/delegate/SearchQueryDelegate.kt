package com.example.mvi.core.delegate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * A debounced search box, as a reusable delegate.
 *
 * Two streams for two different consumers, which is the whole trick:
 *
 * - [query] updates on **every** keystroke and drives the text field, so typing never
 *   feels laggy;
 * - [debouncedQuery] updates only once the user pauses, and drives the network call.
 *
 * Feeding the text field from the debounced stream is the classic bug this separation
 * removes — the cursor jumps and characters get eaten.
 *
 * @param debounceMillis how long the user has to stop typing before a search is issued.
 */
class SearchQueryDelegate(
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    initialQuery: String = "",
) {

    private val _query = MutableStateFlow(initialQuery)

    /** Every keystroke. Render the text field from this. */
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Settled queries only. Trigger searches from this.
     *
     * `drop(1)` discards the initial value: a `StateFlow` always replays what it holds,
     * and re-searching for the query you already loaded on startup would double every
     * first request.
     */
    val debouncedQuery: Flow<String> = _query
        .drop(1)
        .debounce(debounceMillis)
        .distinctUntilChanged()

    fun onQueryChanged(value: String) {
        _query.value = value
    }

    fun clear() {
        _query.value = ""
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}

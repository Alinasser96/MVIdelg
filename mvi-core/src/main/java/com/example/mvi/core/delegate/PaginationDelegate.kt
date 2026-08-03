package com.example.mvi.core.delegate

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything a paged list needs to render itself. Owned by [PaginationDelegate]. */
data class PagingState<T>(
    val items: List<T> = emptyList(),
    /** Index of the last page successfully loaded, or -1 before the first load. */
    val page: Int = -1,
    /** A first page / pull-to-refresh load is running. */
    val isRefreshing: Boolean = false,
    /** A "load more" is running at the bottom of the list. */
    val isAppending: Boolean = false,
    /** The last page came back short, so there is nothing more to fetch. */
    val endReached: Boolean = false,
    val error: Throwable? = null,
) {
    /** True only for the genuinely-nothing-here case, not while loading or after a failure. */
    val isEmpty: Boolean get() = items.isEmpty() && !isRefreshing && error == null
}

/** Fetches one page. The only thing a feature has to supply. */
fun interface PageLoader<T> {
    suspend fun load(page: Int, pageSize: Int): List<T>
}

/**
 * An infinite-scrolling list, as a reusable delegate.
 *
 * This is the case that makes delegation pay for itself. Pagination is ~60 lines of
 * fiddly state machine — guard against double-loading, keep the old page on failure,
 * detect the end, cancel in-flight work on refresh — and it is needed by six screens in
 * a real app. Put it in the base ViewModel and every screen inherits it, including the
 * settings screen with no list at all; copy it per screen and you fix the same bug six
 * times. Compose it, and each list screen writes one field.
 *
 * ```
 * private val paging = PaginationDelegate(viewModelScope, pageSize = 20) { page, size ->
 *     repository.users(query = search.query.value, page = page, pageSize = size)
 * }
 * ```
 */
class PaginationDelegate<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val loader: PageLoader<T>,
) {

    private val _state = MutableStateFlow(PagingState<T>())
    val state: StateFlow<PagingState<T>> = _state.asStateFlow()

    private var job: Job? = null

    /** Loads page 0, replacing whatever is there. Pull-to-refresh and first load both use this. */
    fun refresh() = load(page = FIRST_PAGE, isRefresh = true)

    /**
     * Loads the next page, appending.
     *
     * Safe to call on every scroll event: it is a no-op while another load is running or
     * once the end has been reached, so the UI never has to track "am I already loading".
     */
    fun loadNextPage() {
        val current = _state.value
        if (current.isRefreshing || current.isAppending || current.endReached) return
        load(page = current.page + 1, isRefresh = false)
    }

    /** Retries whichever load failed — the first page if the list is empty, the next page otherwise. */
    fun retry() {
        val current = _state.value
        if (current.error == null) return
        if (current.items.isEmpty()) refresh() else load(current.page + 1, isRefresh = false)
    }

    /** Drops every page and cancels in-flight work. Call this when the query changes. */
    fun reset() {
        job?.cancel()
        _state.value = PagingState()
    }

    private fun load(page: Int, isRefresh: Boolean) {
        job?.cancel()
        // Flip the in-flight flags *before* launching, not inside the coroutine. A
        // coroutine body does not run until the dispatcher gets around to it, so a guard
        // set in there would still be false for a second loadNextPage() arriving in the
        // same frame — and the list would fetch the same page twice.
        _state.update {
            it.copy(isRefreshing = isRefresh, isAppending = !isRefresh, error = null)
        }
        job = scope.launch {
            try {
                val batch = loader.load(page, pageSize)
                _state.update { current ->
                    current.copy(
                        // Page 0 replaces; every other page appends. This is why refresh()
                        // does not need to clear the list first and make the screen blink.
                        items = if (page == FIRST_PAGE) batch else current.items + batch,
                        page = page,
                        // A short page means the server has nothing left.
                        endReached = batch.size < pageSize,
                        isRefreshing = false,
                        isAppending = false,
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Items are deliberately left untouched: a failed "load more" must not
                // wipe the pages the user is already looking at.
                _state.update {
                    it.copy(isRefreshing = false, isAppending = false, error = error)
                }
            }
        }
    }

    companion object {
        const val FIRST_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
    }
}

package com.example.mvi.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatchers.
 *
 * App-side, not library-side: `:mvi-core` never picks a dispatcher for you. Nothing here
 * references [Dispatchers] directly, so a test can hand every layer a single
 * `StandardTestDispatcher` and keep full control of virtual time.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}

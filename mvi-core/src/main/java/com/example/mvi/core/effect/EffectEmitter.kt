package com.example.mvi.core.effect

import com.example.mvi.core.MviEffect
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Emits one-shot events that must be consumed exactly once.
 *
 * The second of the three primitive capabilities. Backed by a [Channel] rather than a
 * `SharedFlow` on purpose:
 *
 * - a `Channel` *buffers* while nobody is collecting, so an effect emitted while the
 *   screen is in the background is still delivered when it comes back;
 * - `receiveAsFlow()` gives each element to exactly one collector, so an effect can
 *   never be replayed on rotation the way a `SharedFlow(replay = 1)` would.
 */
interface EffectEmitter<E : MviEffect> {

    /** Cold-ish flow of effects. Collect it once, from one place, lifecycle-aware. */
    val effect: Flow<E>

    /**
     * Queues [effect] for delivery. Non-suspending, so it is safe to call from a reducer
     * branch or any plain function.
     */
    fun sendEffect(effect: E)
}

/**
 * The only implementation of [EffectEmitter].
 *
 * @param capacity how many effects may pile up while nothing is collecting. The default
 *   is generous; on overflow the *oldest* effect is dropped, because a stale navigation
 *   command is less useful than the newest one.
 */
class EffectEmitterDelegate<E : MviEffect>(
    capacity: Int = DEFAULT_CAPACITY,
) : EffectEmitter<E> {

    private val channel = Channel<E>(capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val effect: Flow<E> = channel.receiveAsFlow()

    override fun sendEffect(effect: E) {
        channel.trySend(effect)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
    }
}

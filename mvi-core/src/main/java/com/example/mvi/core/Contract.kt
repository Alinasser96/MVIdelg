package com.example.mvi.core

/**
 * The three vocabulary types of MVI. Every screen declares its own triple, usually
 * together in one file called `<Feature>Contract.kt`, so a reader can learn everything
 * a screen can do by reading a single file.
 *
 * ```
 * sealed interface CartIntent : MviIntent { ... }   // what the user can ask for
 * data class CartState(...) : MviState              // what the screen looks like
 * sealed interface CartEffect : MviEffect { ... }   // what happens exactly once
 * ```
 *
 * They are marker interfaces on purpose: they carry no behaviour, they only make the
 * generic signatures of [MviViewModel] readable and stop unrelated types from being
 * passed where an intent is expected.
 */

/**
 * A user (or system) *intention*. Named after what happened, not after what the
 * ViewModel should do about it: `RetryClicked`, not `ReloadUsers`.
 *
 * Intents must be values — `data class` / `data object` — so they can be compared in
 * tests and safely queued.
 */
interface MviIntent

/**
 * The complete, immutable description of what the screen shows right now.
 *
 * One state object per screen. Everything the UI renders comes from here and nowhere
 * else, which is what makes the UI a pure function of state. Prefer a `data class` of
 * already-formatted, render-ready fields over exposing domain models directly.
 */
interface MviState

/**
 * Something that must happen exactly once and cannot be re-rendered: navigation, a
 * snackbar, a toast, a share sheet, haptics.
 *
 * The test for "is this state or an effect?" is: *if the screen rotated right now,
 * should this happen again?* If yes it belongs in [MviState]; if no it is an effect.
 */
interface MviEffect

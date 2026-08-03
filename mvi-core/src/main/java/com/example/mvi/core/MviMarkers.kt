package com.example.mvi.core

/**
 * Base marker interfaces for the MVI pattern.
 *
 * Every screen declares its own triple. They carry no behaviour; they exist so the
 * generic signature of [MviViewModel] reads clearly and so unrelated types cannot be
 * passed where an intent is expected.
 */
interface ViewState

interface Intent

interface Effect

/**
 * Use as the [Effect] type for ViewModels that don't emit side effects.
 */
object NoEffect : Effect

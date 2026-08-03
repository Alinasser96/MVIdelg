package com.example.mvi.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher, because `viewModelScope` runs on Main
 * and there is no Android main looper in a unit test.
 *
 * Needed only for tests that construct a real [MviViewModel]; the delegates themselves
 * take a scope as a parameter, so they can be tested with `runTest`'s own scope and no
 * rule at all — which is one of the practical payoffs of keeping them out of the base class.
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

package com.example.mvi.di

import com.example.mvi.data.FakeUserRepository
import com.example.mvi.data.UserRepository

/**
 * Deliberately the dumbest possible dependency injection.
 *
 * The blueprint has no opinion about DI, and adding Hilt here would bury the MVI ideas
 * under generated components and annotations. Swapping this for Hilt or Koin is a change
 * to the `Factory` in each ViewModel's companion and nothing else — the base class, the
 * delegates, and every Composable stay exactly as they are.
 */
object ServiceLocator {
    val userRepository: UserRepository by lazy { FakeUserRepository() }
}

package com.example.mvi.data

import kotlinx.coroutines.delay

/**
 * The data layer boundary. ViewModels depend on this interface only, which is what lets
 * their tests hand them a five-line fake instead of a mocking framework.
 */
interface UserRepository {
    suspend fun users(): List<User>
    suspend fun user(id: Int): User
}

/**
 * In-memory stand-in for a real API, so the blueprint runs with no network and no keys.
 *
 * [simulateFailure] is flipped from the UI, making the `ErrorPlugin` and retry paths
 * something you can demo on purpose rather than wait for.
 */
class FakeUserRepository(
    private val latencyMillis: Long = 700,
    userCount: Int = 24,
) : UserRepository {

    @Volatile
    var simulateFailure: Boolean = false

    private val all: List<User> = List(userCount) { index ->
        val first = FIRST_NAMES[index % FIRST_NAMES.size]
        val last = LAST_NAMES[(index / FIRST_NAMES.size) % LAST_NAMES.size]
        User(
            id = index,
            name = "$first $last",
            handle = "@${first.lowercase()}$index",
            role = ROLES[index % ROLES.size],
            bio = "${ROLES[index % ROLES.size]} #$index. Joined in ${2015 + index % 10}.",
        )
    }

    override suspend fun users(): List<User> {
        delay(latencyMillis)
        failIfRequested()
        return all
    }

    override suspend fun user(id: Int): User {
        delay(latencyMillis)
        failIfRequested()
        return all.firstOrNull { it.id == id } ?: throw NoSuchElementException("No user $id")
    }

    private fun failIfRequested() {
        if (simulateFailure) throw IllegalStateException("Could not reach the server.")
    }

    private companion object {
        val FIRST_NAMES = listOf(
            "Aly", "Nour", "Hana", "Omar", "Layla", "Youssef", "Mariam", "Karim",
            "Salma", "Tarek", "Dina", "Rami",
        )
        val LAST_NAMES = listOf(
            "Hassan", "Ibrahim", "Khalil", "Mansour", "Fahmy", "Sabry", "Zaki", "Rashad",
        )
        val ROLES = listOf(
            "Android Engineer", "Designer", "Backend Engineer", "QA", "Product Manager",
        )
    }
}

package com.example.mvi.data

import kotlinx.coroutines.delay

/**
 * The data layer boundary. The ViewModels depend on this interface only, which is what
 * lets their tests hand them a two-line fake instead of a mocking framework.
 */
interface UserRepository {

    /** One page of users matching [query]. Suspends; throws on failure. */
    suspend fun users(query: String, page: Int, pageSize: Int): List<User>

    /** One user by id. Suspends; throws if not found. */
    suspend fun user(id: Int): User
}

/**
 * In-memory stand-in for a real API, so the blueprint runs with no network and no keys.
 *
 * **Failure is deterministic, not random:** search for `fail` (or open a user whose name
 * contains it) and the call throws. That makes the error and retry paths something you
 * can demo on purpose rather than wait for.
 */
class FakeUserRepository(
    private val latencyMillis: Long = 700,
    userCount: Int = 137,
) : UserRepository {

    private val all: List<User> = List(userCount) { index ->
        val first = FIRST_NAMES[index % FIRST_NAMES.size]
        val last = LAST_NAMES[(index / FIRST_NAMES.size) % LAST_NAMES.size]
        User(
            id = index,
            name = "$first $last",
            handle = "@${first.lowercase()}${index}",
            role = ROLES[index % ROLES.size],
            bio = "${ROLES[index % ROLES.size]} #$index. Joined in ${2015 + index % 10}.",
        )
    }

    override suspend fun users(query: String, page: Int, pageSize: Int): List<User> {
        delay(latencyMillis)
        failIfRequested(query)
        return all
            .filter { it.matches(query) }
            .drop(page * pageSize)
            .take(pageSize)
    }

    override suspend fun user(id: Int): User {
        delay(latencyMillis)
        val user = all.firstOrNull { it.id == id } ?: throw NoSuchElementException("No user $id")
        failIfRequested(user.name)
        return user
    }

    private fun failIfRequested(text: String) {
        if (text.contains(FAILURE_TRIGGER, ignoreCase = true)) {
            throw IllegalStateException("Could not reach the server.")
        }
    }

    private fun User.matches(query: String): Boolean =
        query.isBlank() ||
            name.contains(query, ignoreCase = true) ||
            handle.contains(query, ignoreCase = true) ||
            role.contains(query, ignoreCase = true)

    companion object {
        /** Type this into the search box to exercise the error and retry paths. */
        const val FAILURE_TRIGGER = "fail"

        private val FIRST_NAMES = listOf(
            "Aly", "Nour", "Hana", "Omar", "Layla", "Youssef", "Mariam", "Karim",
            "Salma", "Tarek", "Dina", "Rami",
        )
        private val LAST_NAMES = listOf(
            "Hassan", "Ibrahim", "Khalil", "Mansour", "Fahmy", "Sabry", "Zaki",
            "Rashad", "Nabil", "Adel", "Shafik", "Gamal",
        )
        private val ROLES = listOf(
            "Android Engineer", "Designer", "Backend Engineer", "QA", "Product Manager",
        )
    }
}

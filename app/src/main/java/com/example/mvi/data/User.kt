package com.example.mvi.data

/** A domain model. Notice it is not an [com.example.mvi.core.MviState] — states are per screen. */
data class User(
    val id: Int,
    val name: String,
    val handle: String,
    val role: String,
    val bio: String,
)

@file:Suppress("unused")

package dev.hermannm.devlog.example

import dev.hermannm.devlog.Logger
import kotlinx.serialization.Serializable

private val log = Logger {}

fun example(user: User) {
  try {
    storeUser(user)
  } catch (e: Exception) {
    log.error {
      cause = e
      addMarker("user", user)
      "Failed to store user in database"
    }
  }
}

fun storeUser(user: User) {
  throw Exception("Database query failed")
}

@Serializable data class User(val id: String, val name: String)

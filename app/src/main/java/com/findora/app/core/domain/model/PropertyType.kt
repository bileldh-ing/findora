package com.findora.app.core.domain.model

/** Listing category. [id] is the stable value used by the seed JSON and the database. */
enum class PropertyType(
    val id: String,
) {
    House("house"),
    Apartment("apartment"),
    Room("room"),
    ;

    companion object {
        fun fromId(id: String): PropertyType =
            entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown property type: $id")
    }
}

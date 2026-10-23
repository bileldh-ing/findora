package com.findora.app.core.domain.repository

/** Clears what the app can download again: the image cache (memory and disk) and the map tile cache. */
interface CacheCleaner {
    /** Returns how many bytes were freed on disk. */
    suspend fun clear(): Long
}

/** Builds a JSON copy of everything the app stores for one user (profile, favourites, viewings, chats). */
interface UserDataExporter {
    suspend fun export(userId: String): String
}

/** Writes text to a document the user picked with the system file picker (Storage Access Framework). */
interface DocumentWriter {
    /** Throws [java.io.IOException] when the document cannot be written. */
    suspend fun write(
        uri: String,
        text: String,
    )
}

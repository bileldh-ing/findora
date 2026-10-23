package com.findora.app.core.data.export

import android.content.Context
import android.net.Uri
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.repository.DocumentWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Writes UTF-8 text to a `content://` document returned by ACTION_CREATE_DOCUMENT, truncating it first. */
class ContentResolverDocumentWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : DocumentWriter {
        override suspend fun write(
            uri: String,
            text: String,
        ) = withContext(io) {
            val stream =
                context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                    ?: throw IOException("Cannot open $uri for writing")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        }
    }

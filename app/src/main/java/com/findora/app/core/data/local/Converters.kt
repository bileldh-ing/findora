package com.findora.app.core.data.local

import androidx.room.TypeConverter
import com.findora.app.core.data.local.entity.ImageColumns
import com.findora.app.core.data.local.entity.PricePointColumns
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** JSON column converters. Lists are small and read whole; the Home filter matches amenity ids inside the JSON text. */
class Converters {
    @TypeConverter
    fun stringsToJson(value: List<String>): String = json.encodeToString(strings, value)

    @TypeConverter
    fun jsonToStrings(value: String): List<String> = json.decodeFromString(strings, value)

    @TypeConverter
    fun imagesToJson(value: List<ImageColumns>): String = json.encodeToString(images, value)

    @TypeConverter
    fun jsonToImages(value: String): List<ImageColumns> = json.decodeFromString(images, value)

    @TypeConverter
    fun historyToJson(value: List<PricePointColumns>): String = json.encodeToString(history, value)

    @TypeConverter
    fun jsonToHistory(value: String): List<PricePointColumns> = json.decodeFromString(history, value)

    // Explicit types: KSP (Room) runs before the serialization plugin synthesises serializer().
    private companion object {
        val json: Json = Json { ignoreUnknownKeys = true }
        val strings: KSerializer<List<String>> = ListSerializer(String.serializer())
        val images: KSerializer<List<ImageColumns>> = ListSerializer(ImageColumns.serializer())
        val history: KSerializer<List<PricePointColumns>> = ListSerializer(PricePointColumns.serializer())
    }
}

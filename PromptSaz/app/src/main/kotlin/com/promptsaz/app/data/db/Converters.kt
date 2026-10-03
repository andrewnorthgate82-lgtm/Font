package com.promptsaz.app.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Converts List<String> (tags) to/from a JSON array string for Room.
 * Malformed data degrades to an empty list instead of crashing the app.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun listToJson(value: List<String>?): String =
        if (value.isNullOrEmpty()) "[]" else json.encodeToString(listSerializer, value)

    @TypeConverter
    fun jsonToList(value: String?): List<String> =
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(listSerializer, value) }.getOrDefault(emptyList())
        }
}

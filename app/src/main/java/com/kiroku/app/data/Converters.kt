package com.kiroku.app.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun tagsToJson(tags: List<String>): String = JSONArray(tags).toString()

    @TypeConverter
    fun jsonToTags(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

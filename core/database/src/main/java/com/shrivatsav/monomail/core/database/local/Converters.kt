package com.shrivatsav.monomail.core.database.local
import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value == null) return "[]"
        return JSONArray(value).toString()
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        val array = JSONArray(value)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    }
}

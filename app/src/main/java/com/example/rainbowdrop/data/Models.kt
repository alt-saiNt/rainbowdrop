package com.example.rainbowdrop.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.example.rainbowdrop.engine.FilterType
import com.example.rainbowdrop.engine.Tool
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "projects")
data class ColoringProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalImageUri: String,
    val filterType: FilterType,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class ActionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val actionType: ActionType,
    val tool: Tool,
    val color: Int,
    val pathData: String? = null, // JSON list of points for BRUSH
    val x: Int? = null, // For BUCKET
    val y: Int? = null, // For BUCKET
    val timestamp: Long = System.currentTimeMillis()
)

enum class ActionType {
    DRAW, FILL
}

class Converters {
    @TypeConverter
    fun fromFilterType(value: FilterType) = value.name

    @TypeConverter
    fun toFilterType(value: String) = FilterType.valueOf(value)

    @TypeConverter
    fun fromTool(value: Tool) = value.name

    @TypeConverter
    fun toTool(value: String) = Tool.valueOf(value)

    @TypeConverter
    fun fromActionType(value: ActionType) = value.name

    @TypeConverter
    fun toActionType(value: String) = ActionType.valueOf(value)
}

fun List<Pair<Float, Float>>.serializePoints(): String =
    joinToString(";") { "${it.first},${it.second}" }

fun String.deserializePoints(): List<Pair<Float, Float>> {
    if (isBlank()) return emptyList()
    return split(";").mapNotNull {
        val parts = it.split(",")
        if (parts.size == 2) {
            val x = parts[0].toFloatOrNull()
            val y = parts[1].toFloatOrNull()
            if (x != null && y != null) {
                x to y
            } else null
        } else null
    }
}

fun List<Pair<Float, Float>>.toAndroidPath(): android.graphics.Path {
    val path = android.graphics.Path()
    if (isNotEmpty()) {
        path.moveTo(this[0].first, this[0].second)
        for (i in 1 until size) {
            path.lineTo(this[i].first, this[i].second)
        }
    }
    return path
}

package com.example.rainbowdrop.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ColoringProject): Long

    @Update
    suspend fun updateProject(project: ColoringProject)

    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<ColoringProject>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): ColoringProject?

    @Query("SELECT * FROM projects WHERE originalImageUri = :uri LIMIT 1")
    suspend fun getProjectByUri(uri: String): ColoringProject?

    @Insert
    suspend fun insertAction(action: ActionEntry)

    @Query("SELECT * FROM history WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getHistoryForProject(projectId: Long): Flow<List<ActionEntry>>

    @Query("DELETE FROM history WHERE projectId = :projectId AND timestamp > :timestamp")
    suspend fun deleteHistoryAfter(projectId: Long, timestamp: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("DELETE FROM history WHERE projectId = :id")
    suspend fun deleteHistoryForProject(id: Long)
}

@Database(entities = [ColoringProject::class, ActionEntry::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}

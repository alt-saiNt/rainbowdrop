package com.example.rainbowdrop.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _projectDao: Lazy<ProjectDao> = lazy {
    ProjectDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "a0193b80ded9532b20757c33340783f0", "24df637f4e3b5089bfcf423dafab7823") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalImageUri` TEXT NOT NULL, `filterType` TEXT NOT NULL, `tool` TEXT NOT NULL, `isMysteryMode` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, `actionType` TEXT NOT NULL, `tool` TEXT NOT NULL, `color` INTEGER NOT NULL, `pathData` TEXT, `x` INTEGER, `y` INTEGER, `timestamp` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a0193b80ded9532b20757c33340783f0')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `projects`")
        connection.execSQL("DROP TABLE IF EXISTS `history`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsProjects: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsProjects.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("originalImageUri", TableInfo.Column("originalImageUri", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("filterType", TableInfo.Column("filterType", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("tool", TableInfo.Column("tool", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("isMysteryMode", TableInfo.Column("isMysteryMode", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsProjects.put("lastModified", TableInfo.Column("lastModified", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysProjects: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesProjects: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoProjects: TableInfo = TableInfo("projects", _columnsProjects, _foreignKeysProjects,
            _indicesProjects)
        val _existingProjects: TableInfo = read(connection, "projects")
        if (!_infoProjects.equals(_existingProjects)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |projects(com.example.rainbowdrop.data.ColoringProject).
              | Expected:
              |""".trimMargin() + _infoProjects + """
              |
              | Found:
              |""".trimMargin() + _existingProjects)
        }
        val _columnsHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsHistory.put("id", TableInfo.Column("id", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("projectId", TableInfo.Column("projectId", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("actionType", TableInfo.Column("actionType", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("tool", TableInfo.Column("tool", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("color", TableInfo.Column("color", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("pathData", TableInfo.Column("pathData", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("x", TableInfo.Column("x", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("y", TableInfo.Column("y", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoHistory: TableInfo = TableInfo("history", _columnsHistory, _foreignKeysHistory,
            _indicesHistory)
        val _existingHistory: TableInfo = read(connection, "history")
        if (!_infoHistory.equals(_existingHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |history(com.example.rainbowdrop.data.ActionEntry).
              | Expected:
              |""".trimMargin() + _infoHistory + """
              |
              | Found:
              |""".trimMargin() + _existingHistory)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "projects", "history")
  }

  public override fun clearAllTables() {
    super.performClear(false, "projects", "history")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(ProjectDao::class, ProjectDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun projectDao(): ProjectDao = _projectDao.value
}

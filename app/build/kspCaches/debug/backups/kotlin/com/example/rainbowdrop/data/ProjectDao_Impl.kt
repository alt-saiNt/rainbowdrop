package com.example.rainbowdrop.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.example.rainbowdrop.engine.FilterType
import com.example.rainbowdrop.engine.Tool
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class ProjectDao_Impl(
  __db: RoomDatabase,
) : ProjectDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfColoringProject: EntityInsertAdapter<ColoringProject>

  private val __converters: Converters = Converters()

  private val __insertAdapterOfActionEntry: EntityInsertAdapter<ActionEntry>

  private val __updateAdapterOfColoringProject: EntityDeleteOrUpdateAdapter<ColoringProject>
  init {
    this.__db = __db
    this.__insertAdapterOfColoringProject = object : EntityInsertAdapter<ColoringProject>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `projects` (`id`,`originalImageUri`,`filterType`,`tool`,`isMysteryMode`,`lastModified`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ColoringProject) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.originalImageUri)
        val _tmp: String = __converters.fromFilterType(entity.filterType)
        statement.bindText(3, _tmp)
        val _tmp_1: String = __converters.fromTool(entity.tool)
        statement.bindText(4, _tmp_1)
        val _tmp_2: Int = if (entity.isMysteryMode) 1 else 0
        statement.bindLong(5, _tmp_2.toLong())
        statement.bindLong(6, entity.lastModified)
      }
    }
    this.__insertAdapterOfActionEntry = object : EntityInsertAdapter<ActionEntry>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `history` (`id`,`projectId`,`actionType`,`tool`,`color`,`pathData`,`x`,`y`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: ActionEntry) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.projectId)
        val _tmp: String = __converters.fromActionType(entity.actionType)
        statement.bindText(3, _tmp)
        val _tmp_1: String = __converters.fromTool(entity.tool)
        statement.bindText(4, _tmp_1)
        statement.bindLong(5, entity.color.toLong())
        val _tmpPathData: String? = entity.pathData
        if (_tmpPathData == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpPathData)
        }
        val _tmpX: Int? = entity.x
        if (_tmpX == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpX.toLong())
        }
        val _tmpY: Int? = entity.y
        if (_tmpY == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpY.toLong())
        }
        statement.bindLong(9, entity.timestamp)
      }
    }
    this.__updateAdapterOfColoringProject = object : EntityDeleteOrUpdateAdapter<ColoringProject>()
        {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `projects` SET `id` = ?,`originalImageUri` = ?,`filterType` = ?,`tool` = ?,`isMysteryMode` = ?,`lastModified` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: ColoringProject) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.originalImageUri)
        val _tmp: String = __converters.fromFilterType(entity.filterType)
        statement.bindText(3, _tmp)
        val _tmp_1: String = __converters.fromTool(entity.tool)
        statement.bindText(4, _tmp_1)
        val _tmp_2: Int = if (entity.isMysteryMode) 1 else 0
        statement.bindLong(5, _tmp_2.toLong())
        statement.bindLong(6, entity.lastModified)
        statement.bindLong(7, entity.id)
      }
    }
  }

  public override suspend fun insertProject(project: ColoringProject): Long =
      performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfColoringProject.insertAndReturnId(_connection, project)
    _result
  }

  public override suspend fun insertAction(action: ActionEntry): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfActionEntry.insert(_connection, action)
  }

  public override suspend fun updateProject(project: ColoringProject): Unit =
      performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfColoringProject.handle(_connection, project)
  }

  public override fun getAllProjects(): Flow<List<ColoringProject>> {
    val _sql: String = "SELECT * FROM projects ORDER BY lastModified DESC"
    return createFlow(__db, false, arrayOf("projects")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfOriginalImageUri: Int = getColumnIndexOrThrow(_stmt, "originalImageUri")
        val _columnIndexOfFilterType: Int = getColumnIndexOrThrow(_stmt, "filterType")
        val _columnIndexOfTool: Int = getColumnIndexOrThrow(_stmt, "tool")
        val _columnIndexOfIsMysteryMode: Int = getColumnIndexOrThrow(_stmt, "isMysteryMode")
        val _columnIndexOfLastModified: Int = getColumnIndexOrThrow(_stmt, "lastModified")
        val _result: MutableList<ColoringProject> = mutableListOf()
        while (_stmt.step()) {
          val _item: ColoringProject
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpOriginalImageUri: String
          _tmpOriginalImageUri = _stmt.getText(_columnIndexOfOriginalImageUri)
          val _tmpFilterType: FilterType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfFilterType)
          _tmpFilterType = __converters.toFilterType(_tmp)
          val _tmpTool: Tool
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfTool)
          _tmpTool = __converters.toTool(_tmp_1)
          val _tmpIsMysteryMode: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsMysteryMode).toInt()
          _tmpIsMysteryMode = _tmp_2 != 0
          val _tmpLastModified: Long
          _tmpLastModified = _stmt.getLong(_columnIndexOfLastModified)
          _item =
              ColoringProject(_tmpId,_tmpOriginalImageUri,_tmpFilterType,_tmpTool,_tmpIsMysteryMode,_tmpLastModified)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getProjectById(id: Long): ColoringProject? {
    val _sql: String = "SELECT * FROM projects WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfOriginalImageUri: Int = getColumnIndexOrThrow(_stmt, "originalImageUri")
        val _columnIndexOfFilterType: Int = getColumnIndexOrThrow(_stmt, "filterType")
        val _columnIndexOfTool: Int = getColumnIndexOrThrow(_stmt, "tool")
        val _columnIndexOfIsMysteryMode: Int = getColumnIndexOrThrow(_stmt, "isMysteryMode")
        val _columnIndexOfLastModified: Int = getColumnIndexOrThrow(_stmt, "lastModified")
        val _result: ColoringProject?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpOriginalImageUri: String
          _tmpOriginalImageUri = _stmt.getText(_columnIndexOfOriginalImageUri)
          val _tmpFilterType: FilterType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfFilterType)
          _tmpFilterType = __converters.toFilterType(_tmp)
          val _tmpTool: Tool
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfTool)
          _tmpTool = __converters.toTool(_tmp_1)
          val _tmpIsMysteryMode: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsMysteryMode).toInt()
          _tmpIsMysteryMode = _tmp_2 != 0
          val _tmpLastModified: Long
          _tmpLastModified = _stmt.getLong(_columnIndexOfLastModified)
          _result =
              ColoringProject(_tmpId,_tmpOriginalImageUri,_tmpFilterType,_tmpTool,_tmpIsMysteryMode,_tmpLastModified)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getProjectByUri(uri: String): ColoringProject? {
    val _sql: String = "SELECT * FROM projects WHERE originalImageUri = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, uri)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfOriginalImageUri: Int = getColumnIndexOrThrow(_stmt, "originalImageUri")
        val _columnIndexOfFilterType: Int = getColumnIndexOrThrow(_stmt, "filterType")
        val _columnIndexOfTool: Int = getColumnIndexOrThrow(_stmt, "tool")
        val _columnIndexOfIsMysteryMode: Int = getColumnIndexOrThrow(_stmt, "isMysteryMode")
        val _columnIndexOfLastModified: Int = getColumnIndexOrThrow(_stmt, "lastModified")
        val _result: ColoringProject?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpOriginalImageUri: String
          _tmpOriginalImageUri = _stmt.getText(_columnIndexOfOriginalImageUri)
          val _tmpFilterType: FilterType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfFilterType)
          _tmpFilterType = __converters.toFilterType(_tmp)
          val _tmpTool: Tool
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfTool)
          _tmpTool = __converters.toTool(_tmp_1)
          val _tmpIsMysteryMode: Boolean
          val _tmp_2: Int
          _tmp_2 = _stmt.getLong(_columnIndexOfIsMysteryMode).toInt()
          _tmpIsMysteryMode = _tmp_2 != 0
          val _tmpLastModified: Long
          _tmpLastModified = _stmt.getLong(_columnIndexOfLastModified)
          _result =
              ColoringProject(_tmpId,_tmpOriginalImageUri,_tmpFilterType,_tmpTool,_tmpIsMysteryMode,_tmpLastModified)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getHistoryForProject(projectId: Long): Flow<List<ActionEntry>> {
    val _sql: String = "SELECT * FROM history WHERE projectId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfProjectId: Int = getColumnIndexOrThrow(_stmt, "projectId")
        val _columnIndexOfActionType: Int = getColumnIndexOrThrow(_stmt, "actionType")
        val _columnIndexOfTool: Int = getColumnIndexOrThrow(_stmt, "tool")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfPathData: Int = getColumnIndexOrThrow(_stmt, "pathData")
        val _columnIndexOfX: Int = getColumnIndexOrThrow(_stmt, "x")
        val _columnIndexOfY: Int = getColumnIndexOrThrow(_stmt, "y")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<ActionEntry> = mutableListOf()
        while (_stmt.step()) {
          val _item: ActionEntry
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpProjectId: Long
          _tmpProjectId = _stmt.getLong(_columnIndexOfProjectId)
          val _tmpActionType: ActionType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfActionType)
          _tmpActionType = __converters.toActionType(_tmp)
          val _tmpTool: Tool
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfTool)
          _tmpTool = __converters.toTool(_tmp_1)
          val _tmpColor: Int
          _tmpColor = _stmt.getLong(_columnIndexOfColor).toInt()
          val _tmpPathData: String?
          if (_stmt.isNull(_columnIndexOfPathData)) {
            _tmpPathData = null
          } else {
            _tmpPathData = _stmt.getText(_columnIndexOfPathData)
          }
          val _tmpX: Int?
          if (_stmt.isNull(_columnIndexOfX)) {
            _tmpX = null
          } else {
            _tmpX = _stmt.getLong(_columnIndexOfX).toInt()
          }
          val _tmpY: Int?
          if (_stmt.isNull(_columnIndexOfY)) {
            _tmpY = null
          } else {
            _tmpY = _stmt.getLong(_columnIndexOfY).toInt()
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item =
              ActionEntry(_tmpId,_tmpProjectId,_tmpActionType,_tmpTool,_tmpColor,_tmpPathData,_tmpX,_tmpY,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteHistoryAfter(projectId: Long, timestamp: Long) {
    val _sql: String = "DELETE FROM history WHERE projectId = ? AND timestamp > ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, projectId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, timestamp)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteProject(id: Long) {
    val _sql: String = "DELETE FROM projects WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteHistoryForProject(id: Long) {
    val _sql: String = "DELETE FROM history WHERE projectId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}

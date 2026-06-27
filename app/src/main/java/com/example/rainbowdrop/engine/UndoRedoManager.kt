package com.example.rainbowdrop.engine

import android.graphics.Path
import androidx.compose.runtime.mutableStateListOf
import com.example.rainbowdrop.data.ActionEntry
import com.example.rainbowdrop.data.ActionType

data class CanvasAction(
    val type: ActionType,
    val tool: Tool,
    val color: Int,
    val path: Path? = null,
    val x: Int? = null,
    val y: Int? = null,
    val points: List<Pair<Float, Float>>? = null
)

class UndoRedoManager {
    private val history = mutableListOf<CanvasAction>()
    private val redoStack = mutableListOf<CanvasAction>()
    
    val currentHistory: List<CanvasAction> get() = history

    fun addAction(action: CanvasAction) {
        history.add(action)
        redoStack.clear()
    }

    fun undo(): CanvasAction? {
        if (history.isEmpty()) return null
        val action = history.removeAt(history.size - 1)
        redoStack.add(action)
        return action
    }

    fun redo(): CanvasAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeAt(redoStack.size - 1)
        history.add(action)
        return action
    }
    
    fun clear() {
        history.clear()
        redoStack.clear()
    }
}

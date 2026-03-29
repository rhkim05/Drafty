package com.drafty.state

import kotlinx.coroutines.flow.MutableStateFlow

interface DrawCommand {
    fun execute(state: MutableStateFlow<CanvasState>)
    fun undo(state: MutableStateFlow<CanvasState>)
}

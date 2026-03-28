package com.drafty.android.ui.canvas

import android.view.MotionEvent
import android.view.View
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke as InkStroke

fun buildTouchListener(
    inProgressStrokesView: InProgressStrokesView,
    brushProvider: () -> Brush,
    onStrokeFinished: (InkStroke) -> Unit
): View.OnTouchListener {
    val activeTouchIds = mutableMapOf<Int, InProgressStrokeId>()

    inProgressStrokesView.addFinishedStrokesListener(
        object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
                for ((id, stroke) in strokes) {
                    onStrokeFinished(stroke)
                }
                inProgressStrokesView.removeFinishedStrokes(strokes.keys)
            }
        }
    )

    return View.OnTouchListener { view, event ->
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS &&
            event.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER
        ) {
            return@OnTouchListener false
        }

        val pointerId = event.getPointerId(0)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.requestUnbufferedDispatch(event)
                val strokeId = inProgressStrokesView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brushProvider()
                )
                activeTouchIds[pointerId] = strokeId
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val strokeId = activeTouchIds[pointerId] ?: return@OnTouchListener false
                inProgressStrokesView.addToStroke(event, pointerId, strokeId)
                true
            }

            MotionEvent.ACTION_UP -> {
                val strokeId = activeTouchIds.remove(pointerId) ?: return@OnTouchListener false
                inProgressStrokesView.finishStroke(event, pointerId, strokeId)
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                val strokeId = activeTouchIds.remove(pointerId) ?: return@OnTouchListener false
                inProgressStrokesView.cancelStroke(strokeId, event)
                true
            }

            else -> false
        }
    }
}

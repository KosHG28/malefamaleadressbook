package com.koshg.interlude.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small dashed ring used to mark "today" on the calendar, independent of the day pill's own shape. */
@Composable
fun DottedRing(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val strokePx = 1.4.dp.toPx()
        drawCircle(
            color = color,
            radius = (this.size.minDimension - strokePx) / 2f,
            style = Stroke(
                width = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.5.dp.toPx()), 0f)
            )
        )
    }
}

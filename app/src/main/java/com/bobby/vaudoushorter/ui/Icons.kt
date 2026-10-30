package com.bobby.vaudoushorter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

enum class Glyph { SPARK, UNDO, MORE, TRASH, ARROW, FOLDER, PHOTO, PLAY, CLOSE, CHECK, REFRESH, SHIELD, CHEVRON }

@Composable
fun Mark(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            fun line(vararg points: Float) {
                val path = Path().apply {
                    moveTo(points[0], points[1])
                    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                }
                drawPath(path, tint, style = stroke)
            }
            when (glyph) {
                Glyph.SPARK -> drawPath(Path().apply {
                    moveTo(12f, 2f); lineTo(15f, 9f); lineTo(22f, 12f); lineTo(15f, 15f)
                    lineTo(12f, 22f); lineTo(9f, 15f); lineTo(2f, 12f); lineTo(9f, 9f); close()
                }, tint)
                Glyph.UNDO -> {
                    line(4f, 9f, 4f, 4f); line(4f, 9f, 9f, 9f)
                    drawPath(Path().apply {
                        moveTo(4f, 9f); cubicTo(9f, 0f, 22f, 5f, 20f, 14f)
                        cubicTo(19f, 20f, 11f, 23f, 6f, 18f)
                    }, tint, style = stroke)
                }
                Glyph.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.7f, Offset(12f, it)) }
                Glyph.TRASH -> {
                    line(4f, 7f, 20f, 7f); line(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
                    line(6f, 7f, 7f, 21f, 17f, 21f, 18f, 7f)
                    line(10f, 11f, 10f, 17f); line(14f, 11f, 14f, 17f)
                }
                Glyph.ARROW -> { line(4f, 12f, 20f, 12f); line(13f, 5f, 20f, 12f, 13f, 19f) }
                Glyph.FOLDER -> line(3f, 7f, 3f, 20f, 21f, 20f, 21f, 7f, 11f, 7f, 9f, 4f, 3f, 4f, 3f, 7f)
                Glyph.PHOTO -> {
                    line(3f, 3f, 21f, 3f, 21f, 21f, 3f, 21f, 3f, 3f)
                    line(3f, 17f, 9f, 11f, 13f, 15f, 16f, 12f, 21f, 17f)
                    drawCircle(tint, 1.6f, Offset(16f, 8f))
                }
                Glyph.PLAY -> drawPath(Path().apply {
                    moveTo(8f, 4f); lineTo(21f, 12f); lineTo(8f, 20f); close()
                }, tint)
                Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                Glyph.CHECK -> line(5f, 12f, 10f, 17f, 20f, 7f)
                Glyph.REFRESH -> {
                    drawArc(tint, 45f, 290f, false, Offset(4f, 4f), androidx.compose.ui.geometry.Size(16f, 16f), style = stroke)
                    line(20f, 3f, 20f, 9f, 14f, 9f)
                }
                Glyph.SHIELD -> {
                    line(12f, 2f, 21f, 6f, 20f, 14f, 17f, 19f, 12f, 22f, 7f, 19f, 4f, 14f, 3f, 6f, 12f, 2f)
                    line(8f, 12f, 11f, 15f, 16f, 9f)
                }
                Glyph.CHEVRON -> line(8f, 10f, 12f, 14f, 16f, 10f)
            }
        }
    }
}

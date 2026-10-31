package com.bobby.vaudoushorter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.bobby.vaudoushorter.R

enum class Glyph { HEART, SPARK, UNDO, MORE, TRASH, ARROW, FOLDER, PHOTO, PLAY, CLOSE, CHECK, REFRESH, SHIELD, CHEVRON }

/** Android robot, redrawn in the app's palette. */
@Composable
internal fun AndroidRobotMark(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            drawArc(tint, 180f, 180f, true, Offset(5f, 4f), Size(14f, 10f))
            drawLine(tint, Offset(8f, 5f), Offset(6.5f, 2.5f), 1.3f, StrokeCap.Round)
            drawLine(tint, Offset(16f, 5f), Offset(17.5f, 2.5f), 1.3f, StrokeCap.Round)
            drawRoundRect(tint, Offset(5f, 10f), Size(14f, 9f), CornerRadius(1.5f))
            drawLine(tint, Offset(2.5f, 11f), Offset(2.5f, 17f), 2.5f, StrokeCap.Round)
            drawLine(tint, Offset(21.5f, 11f), Offset(21.5f, 17f), 2.5f, StrokeCap.Round)
            drawLine(tint, Offset(8.5f, 18f), Offset(8.5f, 21.5f), 3f, StrokeCap.Round)
            drawLine(tint, Offset(15.5f, 18f), Offset(15.5f, 21.5f), 3f, StrokeCap.Round)
            drawCircle(Color.Black, .8f, Offset(8.5f, 7f))
            drawCircle(Color.Black, .8f, Offset(15.5f, 7f))
        }
    }
}

/** PhotoMatcher's brand mark, shared with the Android launcher icon. */
@Composable
fun PhotoMatcherMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // Compensate for the transparent breathing room built into the source PNG.
        modifier = modifier.size(24.dp).graphicsLayer(scaleX = 1.3f, scaleY = 1.3f)
    )
}

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
                Glyph.HEART -> drawPath(Path().apply {
                    moveTo(12f, 21f); cubicTo(9f, 18f, 2f, 13f, 2f, 8f)
                    cubicTo(2f, 2f, 9f, 1f, 12f, 6f)
                    cubicTo(15f, 1f, 22f, 2f, 22f, 8f)
                    cubicTo(22f, 13f, 15f, 18f, 12f, 21f); close()
                }, tint)

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

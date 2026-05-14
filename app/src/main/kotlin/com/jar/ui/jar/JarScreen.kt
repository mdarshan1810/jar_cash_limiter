package com.jar.ui.jar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jar.data.JarState
import com.jar.ui.theme.JarTheme
import com.jar.ui.theme.jarAccent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun JarScreen(viewModel: JarViewModel, onSettingsClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    JarScreenContent(state, onSettingsClick)
}

@Composable
private fun JarScreenContent(state: JarState, onSettingsClick: () -> Unit = {}) {
    Box(modifier = Modifier.fillMaxSize()) {
        JarColumnContent(state)
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun JarColumnContent(state: JarState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            JarCanvas(state = state, modifier = Modifier.fillMaxSize())
            BalanceLabel(state = state)
        }
        Text(
            text = "spent this month: ${formatRupees(state.spent)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * Geometry of the mason-jar silhouette as fractions of canvas size.
 *
 * ```
 *      ╭────╮          <- lid: rounded-top rectangle, narrower than body
 *      │    │
 *      ├────┤          <- rim band (decorative line)
 *     ╱      ╲         <- shoulders: cubic-bezier curve out to body width
 *    │  body  │
 *    │        │        <- liquid lives here, behind the glass
 *    ╰────────╯        <- bottom: rounded corners
 * ```
 */
private const val LID_WIDTH_FRACTION = 0.62f
private const val LID_HEIGHT_FRACTION = 0.08f
private const val LID_CORNER_FRACTION = 0.025f
private const val SHOULDER_HEIGHT_FRACTION = 0.10f
private const val BODY_BOTTOM_CORNER_FRACTION = 0.10f
private const val OUTLINE_STROKE_WIDTH = 8f

@Composable
private fun JarCanvas(state: JarState, modifier: Modifier) {
    val accent = jarAccent(state)
    val targetLevel = state.fractionRemaining.coerceIn(0f, 1f)
    val level by animateFloatAsState(
        targetValue = targetLevel,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f),
        label = "jar-level"
    )
    val tilt by rememberTiltAngle()

    // Two waves at different speeds + phases produce the layered slosh look. Phase loops
    // every 3.5s for the front wave and every 5s for the back so they rarely line up,
    // keeping the surface visually busy without being chaotic.
    val transition = rememberInfiniteTransition(label = "jar-waves")
    val frontPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "front-phase"
    )
    val backPhase by transition.animateFloat(
        initialValue = (PI / 2f).toFloat(),
        targetValue = (PI / 2f + 2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "back-phase"
    )

    val outlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
    val rimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lidWidth = w * LID_WIDTH_FRACTION
        val lidHeight = h * LID_HEIGHT_FRACTION
        val lidCorner = w * LID_CORNER_FRACTION
        val shoulderHeight = h * SHOULDER_HEIGHT_FRACTION
        val bodyTop = lidHeight + shoulderHeight
        val bodyBottom = h
        val bodyCorner = w * BODY_BOTTOM_CORNER_FRACTION
        val lidLeft = (w - lidWidth) / 2f
        val lidRight = lidLeft + lidWidth

        val jarPath = buildJarPath(
            w = w,
            lidLeft = lidLeft,
            lidRight = lidRight,
            lidHeight = lidHeight,
            lidCorner = lidCorner,
            bodyTop = bodyTop,
            bodyBottom = bodyBottom,
            bodyCorner = bodyCorner
        )

        val bodyPath = buildBodyPath(
            w = w,
            bodyTop = bodyTop,
            bodyBottom = bodyBottom,
            bodyCorner = bodyCorner
        )

        // Liquid surface position (centerline) before tilt + waves.
        val liquidAreaHeight = bodyBottom - bodyTop
        val flatSurfaceY = bodyBottom - liquidAreaHeight * level
        val (leftY, rightY) = surfaceTilt(
            jarBodyWidth = w,
            centerY = flatSurfaceY,
            tilt = tilt
        )

        clipPath(bodyPath) {
            if (level > 0.001f) {
                drawWaveLiquid(
                    w = w,
                    leftY = leftY,
                    rightY = rightY,
                    bodyBottom = bodyBottom,
                    accent = accent,
                    backPhase = backPhase,
                    frontPhase = frontPhase,
                    tiltMagnitude = abs(tilt)
                )
            }

            // Glass shine — soft diagonal highlight on the upper-left of the body.
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(w * 0.16f, bodyTop + liquidAreaHeight * 0.08f),
                end = Offset(w * 0.22f, bodyTop + liquidAreaHeight * 0.50f),
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
            // Smaller secondary highlight near the bottom-right for depth.
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(w * 0.82f, bodyTop + liquidAreaHeight * 0.65f),
                end = Offset(w * 0.84f, bodyTop + liquidAreaHeight * 0.85f),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }

        // Decorative rim band right under the lid — looks like the screw-on threads.
        drawLine(
            color = rimColor,
            start = Offset(lidLeft + lidCorner * 0.5f, lidHeight + shoulderHeight * 0.18f),
            end = Offset(lidRight - lidCorner * 0.5f, lidHeight + shoulderHeight * 0.18f),
            strokeWidth = 3f
        )

        // Outer silhouette stroke on top of everything for a clean glass edge.
        drawPath(path = jarPath, color = outlineColor, style = Stroke(width = OUTLINE_STROKE_WIDTH))
    }
}

/**
 * Draws the liquid as a wavy-topped polygon. Two stacked sine waves shifted in phase and
 * frequency simulate slosh; tilt magnitude bumps the amplitude so a vigorous tilt looks
 * more turbulent than a still-on-table read.
 *
 * The back wave is darker and offset slightly downward so it peeks through the front wave
 * troughs, adding depth without needing alpha compositing tricks.
 */
private fun DrawScope.drawWaveLiquid(
    w: Float,
    leftY: Float,
    rightY: Float,
    bodyBottom: Float,
    accent: Color,
    backPhase: Float,
    frontPhase: Float,
    tiltMagnitude: Float
) {
    val baseAmplitude = w * 0.012f
    val tiltAmplitudeBoost = w * 0.024f * tiltMagnitude.coerceIn(0f, 1f)
    val amplitude = baseAmplitude + tiltAmplitudeBoost
    val wavelength = w * 1.0f
    val steps = 40

    fun surfaceY(x: Float, phase: Float, ampScale: Float, vOffset: Float = 0f): Float {
        val tiltedCenter = leftY + (rightY - leftY) * (x / w)
        val sine = sin(2f * PI.toFloat() * x / wavelength + phase) * amplitude * ampScale
        return tiltedCenter + sine + vOffset
    }

    // Back wave (slower, slightly lower, darker) — drawn first so the front overlaps it.
    val backPath = Path().apply {
        moveTo(0f, surfaceY(0f, backPhase, ampScale = 0.7f, vOffset = amplitude * 0.6f))
        for (i in 1..steps) {
            val x = i.toFloat() * w / steps
            lineTo(x, surfaceY(x, backPhase, ampScale = 0.7f, vOffset = amplitude * 0.6f))
        }
        lineTo(w, bodyBottom)
        lineTo(0f, bodyBottom)
        close()
    }
    drawPath(backPath, color = accent.copy(alpha = 0.55f).darken(0.85f))

    // Front wave with a vertical gradient — slightly lighter at the surface, deeper at the
    // bottom. Sells the "depth of liquid" feel.
    val frontPath = Path().apply {
        moveTo(0f, surfaceY(0f, frontPhase, ampScale = 1.0f))
        for (i in 1..steps) {
            val x = i.toFloat() * w / steps
            lineTo(x, surfaceY(x, frontPhase, ampScale = 1.0f))
        }
        lineTo(w, bodyBottom)
        lineTo(0f, bodyBottom)
        close()
    }
    val gradient = Brush.verticalGradient(
        colors = listOf(accent.lighten(1.10f), accent, accent.darken(0.88f)),
        startY = leftY.coerceAtMost(rightY) - amplitude,
        endY = bodyBottom
    )
    drawPath(frontPath, brush = gradient)

    // Surface highlight — a thin lighter stroke at the front-wave crest line. Skipped in
    // an angled path stroke for simplicity; we approximate with a small offset polyline.
    val highlightPath = Path().apply {
        moveTo(0f, surfaceY(0f, frontPhase, ampScale = 1.0f))
        for (i in 1..steps) {
            val x = i.toFloat() * w / steps
            lineTo(x, surfaceY(x, frontPhase, ampScale = 1.0f))
        }
    }
    drawPath(
        path = highlightPath,
        color = Color.White.copy(alpha = 0.45f),
        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
    )
}

/** Small helper — multiplies all RGB channels by `factor` (>1 = lighter, <1 = darker). */
private fun Color.lighten(factor: Float = 1.15f): Color = Color(
    red = (red * factor).coerceIn(0f, 1f),
    green = (green * factor).coerceIn(0f, 1f),
    blue = (blue * factor).coerceIn(0f, 1f),
    alpha = alpha
)

private fun Color.darken(factor: Float = 0.85f): Color = lighten(factor)

/**
 * Outer silhouette: rounded-top lid, smooth cubic-bezier shoulders, vertical body sides,
 * rounded bottom corners. Cubic shoulders give a more "blown glass" curve than a single
 * quadratic — the curvature is gentler at both ends and steeper in the middle.
 */
private fun buildJarPath(
    w: Float,
    lidLeft: Float,
    lidRight: Float,
    lidHeight: Float,
    lidCorner: Float,
    bodyTop: Float,
    bodyBottom: Float,
    bodyCorner: Float
): Path = Path().apply {
    // Lid top-left rounded corner
    moveTo(lidLeft + lidCorner, 0f)
    lineTo(lidRight - lidCorner, 0f)
    quadraticBezierTo(lidRight, 0f, lidRight, lidCorner)
    lineTo(lidRight, lidHeight)
    // Right shoulder — cubic bezier with control points pulled toward the lid bottom
    // and the body's top-right, so the curve eases in and out instead of bulging.
    cubicTo(
        lidRight, lidHeight + (bodyTop - lidHeight) * 0.55f,
        w, lidHeight + (bodyTop - lidHeight) * 0.45f,
        w, bodyTop
    )
    lineTo(w, bodyBottom - bodyCorner)
    quadraticBezierTo(w, bodyBottom, w - bodyCorner, bodyBottom)
    lineTo(bodyCorner, bodyBottom)
    quadraticBezierTo(0f, bodyBottom, 0f, bodyBottom - bodyCorner)
    lineTo(0f, bodyTop)
    cubicTo(
        0f, lidHeight + (bodyTop - lidHeight) * 0.45f,
        lidLeft, lidHeight + (bodyTop - lidHeight) * 0.55f,
        lidLeft, lidHeight
    )
    lineTo(lidLeft, lidCorner)
    quadraticBezierTo(lidLeft, 0f, lidLeft + lidCorner, 0f)
    close()
}

/** Body-only path used to clip the liquid (excludes lid + shoulder). */
private fun buildBodyPath(
    w: Float,
    bodyTop: Float,
    bodyBottom: Float,
    bodyCorner: Float
): Path = Path().apply {
    moveTo(0f, bodyTop)
    lineTo(w, bodyTop)
    lineTo(w, bodyBottom - bodyCorner)
    quadraticBezierTo(w, bodyBottom, w - bodyCorner, bodyBottom)
    lineTo(bodyCorner, bodyBottom)
    quadraticBezierTo(0f, bodyBottom, 0f, bodyBottom - bodyCorner)
    lineTo(0f, bodyTop)
    close()
}

@Composable
private fun BalanceLabel(state: JarState) {
    val remaining = state.startingAmount - state.spent
    val percentLeft = (state.fractionRemaining * 100f).toInt().coerceAtLeast(0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatRupees(remaining),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$percentLeft% left",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun JarScreenPreviewFull() {
    JarTheme {
        Surface(color = Color.White) {
            JarScreenContent(
                state = JarState(
                    startingAmount = 30_00_000L,
                    spent = 5_00_000L,
                    monthlyLimit = 25_00_000L,
                    fractionRemaining = 25f / 30f,
                    isOverdrawn = false,
                    isOverLimit = false
                )
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun JarScreenPreviewMid() {
    JarTheme {
        Surface(color = Color.White) {
            JarScreenContent(
                state = JarState(
                    startingAmount = 30_00_000L,
                    spent = 15_00_000L,
                    monthlyLimit = 25_00_000L,
                    fractionRemaining = 0.50f,
                    isOverdrawn = false,
                    isOverLimit = false
                )
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun JarScreenPreviewLow() {
    JarTheme {
        Surface(color = Color.White) {
            JarScreenContent(
                state = JarState(
                    startingAmount = 30_00_000L,
                    spent = 27_00_000L,
                    monthlyLimit = 25_00_000L,
                    fractionRemaining = 3f / 30f,
                    isOverdrawn = false,
                    isOverLimit = true
                )
            )
        }
    }
}

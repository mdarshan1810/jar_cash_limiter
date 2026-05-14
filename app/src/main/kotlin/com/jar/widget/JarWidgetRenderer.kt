package com.jar.widget

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.toColorInt
import androidx.core.graphics.ColorUtils
import com.jar.data.JarState
import java.io.File
import java.io.FileOutputStream

/**
 * Static (no waves, no tilt, no animation) renderer used by the home-screen widget.
 *
 * Mirrors the geometry of [com.jar.ui.jar.JarScreen]'s in-app jar so the widget reads as
 * "the same jar" — same lid + shoulder + body + bottom-corner proportions, same color
 * thresholds. Animation is intentionally dropped: widgets render via RemoteViews and
 * cannot host continuous animations.
 *
 * Output is a [Bitmap] sized to the widget's target dimensions (caller-provided in px).
 * For convenience, [renderAndSave] performs the render then writes a PNG into
 * `context.filesDir/jar_widget.png` so the Glance widget can load it via `ImageProvider`.
 */
internal object JarWidgetRenderer {

    private const val FILE_NAME = "jar_widget.png"

    // Color constants kept in sync with com.jar.ui.theme.Color thresholds. Duplicated here
    // (vs imported) so the renderer module has zero Compose dependency and the bitmap can
    // be produced in any background context without a UI thread.
    private const val GREEN = "#FF2E8B57"
    private const val YELLOW = "#FFF5C518"
    private const val RED = "#FFCC3B3B"
    private const val OUTLINE = "#66111111"  // 40% black
    private const val GLASS_HIGHLIGHT = "#33FFFFFF"  // 20% white

    // Geometry fractions — match JarScreen's constants 1:1.
    private const val LID_WIDTH_FRACTION = 0.62f
    private const val LID_HEIGHT_FRACTION = 0.08f
    private const val LID_CORNER_FRACTION = 0.025f
    private const val SHOULDER_HEIGHT_FRACTION = 0.10f
    private const val BODY_BOTTOM_CORNER_FRACTION = 0.10f

    fun renderAndSave(
        context: Context,
        state: JarState,
        widthPx: Int,
        heightPx: Int,
        paceFractionRemaining: Float? = null
    ): File {
        val bitmap = render(state, widthPx, heightPx, paceFractionRemaining)
        val file = File(context.filesDir, FILE_NAME)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }

    /**
     * @param paceFractionRemaining where the liquid *should* be at this point in the
     *   period — i.e., `(periodEnd - now) / (periodEnd - periodStart)`. When non-null and
     *   inside (0, 1), a dashed horizontal pace line is drawn across the jar at that
     *   level so the user can compare it visually against the actual liquid level.
     */
    fun render(
        state: JarState,
        widthPx: Int,
        heightPx: Int,
        paceFractionRemaining: Float? = null
    ): Bitmap {
        require(widthPx > 0 && heightPx > 0) { "widget bitmap size must be positive" }
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawJar(canvas, state, widthPx.toFloat(), heightPx.toFloat(), paceFractionRemaining)
        return bitmap
    }

    private fun drawJar(
        canvas: Canvas,
        state: JarState,
        canvasW: Float,
        canvasH: Float,
        paceFractionRemaining: Float?
    ) {
        // Margins. Smaller side margin (10%) lets the jar fill the widget — the card
        // padding outside the bitmap supplies the visual breathing room.
        val sideMargin = canvasW * 0.10f
        val topMargin = canvasH * 0.05f
        val bottomMargin = canvasH * 0.06f
        val w = canvasW - sideMargin * 2f
        val h = canvasH - topMargin - bottomMargin

        val saveAll = canvas.save()
        canvas.translate(sideMargin, topMargin)

        val lidWidth = w * LID_WIDTH_FRACTION
        val lidHeight = h * LID_HEIGHT_FRACTION
        val lidCorner = w * LID_CORNER_FRACTION
        val shoulderHeight = h * SHOULDER_HEIGHT_FRACTION
        val bodyTop = lidHeight + shoulderHeight
        val bodyBottom = h
        val bodyCorner = w * BODY_BOTTOM_CORNER_FRACTION
        val lidLeft = (w - lidWidth) / 2f
        val lidRight = lidLeft + lidWidth

        val accent = accentColor(state)
        val accentLight = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.18f)
        val accentDark = ColorUtils.blendARGB(accent, 0xFF000000.toInt(), 0.18f)
        val level = state.fractionRemaining.coerceIn(0f, 1f)

        val jarPath = buildJarPath(w, lidLeft, lidRight, lidHeight, lidCorner, bodyTop, bodyBottom, bodyCorner)
        val bodyPath = buildBodyPath(w, bodyTop, bodyBottom, bodyCorner)

        // Soft outer glow drawn first (behind everything). Stroke + BlurMaskFilter sits
        // outside the jar's outline rather than filling the interior — avoids tinting
        // the air space above the liquid line.
        val glowOffsetY = canvasH * 0.012f
        canvas.translate(0f, glowOffsetY)
        val glowPaint = Paint().apply {
            color = "#33000000".toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = w * 0.06f
            maskFilter = BlurMaskFilter(w * 0.04f, BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
        }
        canvas.drawPath(jarPath, glowPaint)
        canvas.translate(0f, -glowOffsetY)

        // Liquid with a vertical gradient (lighter at the surface, deeper at the bottom)
        // — mirrors the in-app polish so the widget reads as the same artifact.
        if (level > 0.001f) {
            val surfaceY = bodyBottom - (bodyBottom - bodyTop) * level
            val gradient = LinearGradient(
                0f, surfaceY, 0f, bodyBottom,
                intArrayOf(accentLight, accent, accentDark),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            val liquidPaint = Paint().apply {
                shader = gradient
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            val saveCount = canvas.save()
            canvas.clipPath(bodyPath)
            canvas.drawRect(0f, surfaceY, w, bodyBottom, liquidPaint)

            // Bright surface line — separates air from liquid, sharper than the gradient.
            val surfacePaint = Paint().apply {
                color = "#A6FFFFFF".toColorInt()
                strokeWidth = w * 0.012f
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(0f, surfaceY, w, surfaceY, surfacePaint)

            // Glass shine on the body's upper-left.
            val shinePaint = Paint().apply {
                color = GLASS_HIGHLIGHT.toColorInt()
                strokeWidth = w * 0.05f
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            canvas.drawLine(
                w * 0.18f, bodyTop + (bodyBottom - bodyTop) * 0.12f,
                w * 0.22f, bodyTop + (bodyBottom - bodyTop) * 0.45f,
                shinePaint
            )
            canvas.restoreToCount(saveCount)
        }

        // Pace line — a dashed horizontal line marking where the liquid SHOULD be at this
        // point in the period. Drawn between the liquid + outline so it sits on top of
        // the liquid color band but underneath the jar outline.
        if (paceFractionRemaining != null && paceFractionRemaining > 0.01f && paceFractionRemaining < 0.99f) {
            val paceY = bodyBottom - (bodyBottom - bodyTop) * paceFractionRemaining.coerceIn(0f, 1f)
            val pacePaint = Paint().apply {
                color = "#99111111".toColorInt()
                style = Paint.Style.STROKE
                strokeWidth = w * 0.012f
                pathEffect = DashPathEffect(floatArrayOf(w * 0.04f, w * 0.025f), 0f)
                strokeCap = Paint.Cap.BUTT
                isAntiAlias = true
            }
            val saveCount = canvas.save()
            canvas.clipPath(bodyPath)
            // Tiny x-margin so the dashes don't kiss the jar walls.
            canvas.drawLine(w * 0.04f, paceY, w * 0.96f, paceY, pacePaint)
            canvas.restoreToCount(saveCount)
        }

        // Outline last. Stroke width scales with bitmap size so the outline reads cleanly
        // at 200×200 AND 400×400 without looking thread-thin or chunky.
        val outlinePaint = Paint().apply {
            color = OUTLINE.toColorInt()
            style = Paint.Style.STROKE
            strokeWidth = w * 0.025f
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(jarPath, outlinePaint)

        canvas.restoreToCount(saveAll)
    }

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
        moveTo(lidLeft + lidCorner, 0f)
        lineTo(lidRight - lidCorner, 0f)
        quadTo(lidRight, 0f, lidRight, lidCorner)
        lineTo(lidRight, lidHeight)
        cubicTo(
            lidRight, lidHeight + (bodyTop - lidHeight) * 0.55f,
            w, lidHeight + (bodyTop - lidHeight) * 0.45f,
            w, bodyTop
        )
        lineTo(w, bodyBottom - bodyCorner)
        quadTo(w, bodyBottom, w - bodyCorner, bodyBottom)
        lineTo(bodyCorner, bodyBottom)
        quadTo(0f, bodyBottom, 0f, bodyBottom - bodyCorner)
        lineTo(0f, bodyTop)
        cubicTo(
            0f, lidHeight + (bodyTop - lidHeight) * 0.45f,
            lidLeft, lidHeight + (bodyTop - lidHeight) * 0.55f,
            lidLeft, lidHeight
        )
        lineTo(lidLeft, lidCorner)
        quadTo(lidLeft, 0f, lidLeft + lidCorner, 0f)
        close()
    }

    private fun buildBodyPath(
        w: Float,
        bodyTop: Float,
        bodyBottom: Float,
        bodyCorner: Float
    ): Path = Path().apply {
        moveTo(0f, bodyTop)
        lineTo(w, bodyTop)
        lineTo(w, bodyBottom - bodyCorner)
        quadTo(w, bodyBottom, w - bodyCorner, bodyBottom)
        lineTo(bodyCorner, bodyBottom)
        quadTo(0f, bodyBottom, 0f, bodyBottom - bodyCorner)
        lineTo(0f, bodyTop)
        close()
    }

    @Suppress("unused") // kept to match the signature shape of the in-app jarAccent
    private fun rectFor(canvas: Canvas) = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())

    private fun accentColor(state: JarState): Int {
        val hex = when {
            state.isOverdrawn -> RED
            state.fractionRemaining < 0.30f -> RED
            state.fractionRemaining < 0.70f -> YELLOW
            else -> GREEN
        }
        return hex.toColorInt()
    }
}

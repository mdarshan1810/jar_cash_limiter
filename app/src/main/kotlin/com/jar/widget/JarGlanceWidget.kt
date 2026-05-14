package com.jar.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jar.MainActivity
import com.jar.data.JarState
import com.jar.data.currentPeriod
import com.jar.settings.Settings
import com.jar.ui.jar.formatRupees
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Bitmap dimensions. 400×400 ARGB_8888 = 640 KB, comfortably under the ~1 MB RemoteViews
 * binder transaction limit. At this resolution the jar stays sharp even when the widget
 * cell scales the image up by 2–3×.
 */
private const val WIDGET_BITMAP_W_PX = 400
private const val WIDGET_BITMAP_H_PX = 400

class JarGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as com.jar.JarApp).container
        val state: JarState = container.repository.observeJarState().first()
        val settings: Settings = container.settingsStore.flow.first()

        // Pace marker: where the jar SHOULD be at this point in the period. Liquid above
        // the line = under-budget pace; below = burning too fast.
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val period = currentPeriod(now, settings, zone)
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val periodLength = (period.endMillis - period.startMillis).coerceAtLeast(1L)
        val paceFractionRemaining = ((period.endMillis - nowMs).coerceAtLeast(0L).toFloat() / periodLength)
            .coerceIn(0f, 1f)

        val bitmap = JarWidgetRenderer.render(
            state = state,
            widthPx = WIDGET_BITMAP_W_PX,
            heightPx = WIDGET_BITMAP_H_PX,
            paceFractionRemaining = paceFractionRemaining
        )

        provideContent {
            GlanceTheme {
                JarWidgetContent(state = state, bitmap = bitmap, settings = settings)
            }
        }
    }
}

@Composable
private fun JarWidgetContent(state: JarState, bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") settings: Settings) {
    val percentLeft = (state.fractionRemaining * 100f).toInt().coerceAtLeast(0)
    val openAppAction = actionStartActivity<MainActivity>()

    // White rounded card with the jar centered. Card sells the widget as a tap target
    // and gives a clean edge against any wallpaper.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(ComposeColor(0xFFFAFAFA)))
            .cornerRadius(18.dp)
            .clickable(openAppAction),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = "Jar level: $percentLeft% left",
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize()
        )
    }
}

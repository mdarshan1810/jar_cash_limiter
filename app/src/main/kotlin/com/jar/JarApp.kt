package com.jar

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.jar.data.currentPeriod
import com.jar.widget.JarGlanceWidget
import com.jar.widget.JarWidgetRenderer
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class JarApp : Application() {

    lateinit var container: AppContainer
        private set

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Push widget refreshes whenever the jar state changes — covers transactions,
        // settings edits, manual resets, etc. The widget's own provideGlance also renders
        // on first add, so this is the *update* path, not the *initial* path.
        widgetScope.launch {
            container.repository
                .observeJarState()
                .combine(container.settingsStore.flow) { state, settings -> state to settings }
                .distinctUntilChanged()
                .collect { (state, settings) ->
                    runCatching {
                        val now = LocalDateTime.now()
                        val zone = ZoneId.systemDefault()
                        val period = currentPeriod(now, settings, zone)
                        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
                        val periodLength = (period.endMillis - period.startMillis).coerceAtLeast(1L)
                        val pace = ((period.endMillis - nowMs).coerceAtLeast(0L).toFloat() / periodLength)
                            .coerceIn(0f, 1f)
                        JarWidgetRenderer.renderAndSave(this@JarApp, state, 400, 400, pace)
                        JarGlanceWidget().updateAll(this@JarApp)
                    }
                }
        }
    }
}

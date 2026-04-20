package com.jar.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jar.JarApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class JarNotificationListener : NotificationListenerService() {

    private lateinit var pipeline: NotificationPipeline
    private lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        pipeline = (application as JarApp).container.pipeline
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val raw = sbn.toRawNotification() ?: return
        scope.launch { pipeline.handle(raw) }
    }

    private fun StatusBarNotification.toRawNotification(): RawNotification? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        val combined = listOfNotNull(title, text, bigText).joinToString(" · ")
        if (combined.isBlank()) return null
        return RawNotification(
            text = combined,
            sender = title,
            packageName = packageName,
            postTimeMillis = postTime
        )
    }
}

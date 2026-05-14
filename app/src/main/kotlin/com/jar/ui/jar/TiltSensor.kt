package com.jar.ui.jar

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Returns a [State] of the device's normalized X-axis tilt in roughly [-1, +1], where +1
 * means the right edge of the phone is tipped down (gravity vector points right).
 *
 * Wires up a [SensorEventListener] for [Sensor.TYPE_GRAVITY], falling back to
 * [Sensor.TYPE_ACCELEROMETER] on devices without a dedicated gravity sensor (raw accel
 * includes user motion but the low-pass filter below dampens enough that a phone sitting
 * on a table reads ~0).
 *
 * Lifecycle-aware: registers on composition + ON_RESUME, unregisters on dispose + ON_PAUSE.
 * This avoids battery drain when the screen is off or the app is backgrounded.
 */
@Composable
fun rememberTiltAngle(): State<Float> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tiltState = remember { mutableStateOf(0f) }

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // No sensor on this device (rare but possible for emulators / non-phones). Surface
        // stays flat — tiltState defaults to 0f.
        if (sensorManager == null || sensor == null) {
            return@DisposableEffect onDispose { }
        }

        // Low-pass filter: each new reading nudges the state by α toward the raw value.
        // α = 0.15 → ~5 Hz visual response at the 50 Hz SENSOR_DELAY_GAME tick rate.
        val alpha = 0.15f
        val gravityMagnitude = SensorManager.GRAVITY_EARTH  // ≈ 9.81 m/s²

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawX = event.values[0]
                // Normalize to [-1, +1]; raw values can briefly exceed g during motion.
                val normalized = (rawX / gravityMagnitude).coerceIn(-1f, 1f)
                // Phone's X-axis sign convention: +X to the right of the screen, +gravity
                // pointing down. When the device tilts so its RIGHT edge is lower,
                // gravity reads negative on X (because the gravity vector now has a
                // component pointing in the -X direction relative to the device frame).
                // Our "tilt" convention treats right-tilt as positive, so we negate.
                val tilt = -normalized
                tiltState.value = tiltState.value + alpha * (tilt - tiltState.value)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* unused */ }
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)

        // Pause/resume around the composition's lifecycle so we don't poll while the screen
        // is off or the app is backgrounded.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE ->
                    sensorManager.unregisterListener(listener, sensor)
                Lifecycle.Event.ON_RESUME ->
                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            sensorManager.unregisterListener(listener, sensor)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return tiltState
}

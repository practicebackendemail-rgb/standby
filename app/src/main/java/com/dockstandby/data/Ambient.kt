package com.dockstandby.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

/**
 * Smoothed ambient light in lux, or null when the phone has no light sensor.
 * Registered only while [active], so nothing runs when StandBy is in the background.
 */
@Composable
fun rememberLux(active: Boolean): State<Float?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Float?>(null) }
    val sm = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    DisposableEffect(sensor, active) {
        if (sensor == null || !active) return@DisposableEffect onDispose {}
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // Exponential smoothing so a passing shadow doesn't flicker the screen.
                val lux = e.values[0]
                state.value = state.value?.let { it + (lux - it) * .2f } ?: lux
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm!!.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(l) }
    }
    return state
}

/**
 * Calls [onPresence] when someone is likely reaching for the phone: the proximity sensor reports
 * "near", or the phone/desk gets a tap (a spike in linear acceleration).
 */
@Composable
fun PresenceSensors(active: Boolean, onPresence: (reason: String) -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPresence)
    val sm = remember { context.getSystemService(SensorManager::class.java) }
    DisposableEffect(sm, active) {
        if (sm == null || !active) return@DisposableEffect onDispose {}
        val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        val motion = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val l = object : SensorEventListener {
            private var wasNear = false
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> {
                        val near = e.values[0] < e.sensor.maximumRange
                        if (near && !wasNear) callback("proximity")
                        wasNear = near
                    }
                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val (x, y, z) = e.values
                        val a = sqrt(x * x + y * y + z * z)
                        if (a > TAP_THRESHOLD) callback("motion %.2f".format(a))
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        proximity?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_NORMAL) }
        motion?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
}

/** m/s² of linear acceleration that counts as a tap on the desk or picking the phone up. */
private const val TAP_THRESHOLD = 1.5f

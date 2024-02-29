package com.example.test1

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.MutableState

class Sensors(private val sensorManager: SensorManager, val deviceBearing: MutableState<Double>) : SensorEventListener {

    // Define properties and methods to manage sensors
    init {
        // Register this class as a listener for sensor events
        val rotationMatrix = FloatArray(9)
        val orientationValues = FloatArray(3)

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("Sensors", "Rotation sensor registered")
        } else {
            Log.e("Sensors", "Rotation sensor not available")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle changes in sensor accuracy
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            val orientationValues = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
//            val offset = 39.43 //i got from aligning with my iphone when facing 0degree
            // TODO: as of 27th feb 7pm, its correct alr, the angle and all.
            // TODO: but need to let it warm up by turning one round then it will auto calibrate.dky
            // TODO: now need to think of how to change the rotationDegree then can point to B from A bah
            var azimuthDegrees = Math.toDegrees(orientationValues[0].toDouble())
            azimuthDegrees = (azimuthDegrees + 360) % 360
            Log.d("Sensors", "Azimuth (degrees): $azimuthDegrees")
            deviceBearing.value = azimuthDegrees
            // Handle azimuth (bearing) value in degrees
        }
    }
}

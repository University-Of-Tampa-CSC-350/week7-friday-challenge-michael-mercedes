package com.example.fc_007

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var movementArea: FrameLayout
    private lateinit var ball: View
    private lateinit var sensorUnavailable: TextView

    /** Max |translationX| so the ball stays inside [movementArea]. */
    private var maxOffsetX = 0f
    private var maxOffsetY = 0f

    /** Current ball position offsets from the center. */
    private var ballX = 0f
    private var ballY = 0f

    /** Filtered sensor values to reduce jitter and ensure smooth movement. */
    private var filteredAx = 0f
    private var filteredAy = 0f
    private val alpha = 0.15f // Smoothing factor: lower is smoother, higher is more responsive

    /** Scaling factor for movement sensitivity. */
    private val sensitivity = 2.5f

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateMovementBounds()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        movementArea = findViewById(R.id.movement_area)
        ball = findViewById(R.id.ball)
        sensorUnavailable = findViewById(R.id.sensor_unavailable_message)

        // Initialize SensorManager and check for Accelerometer availability
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            handleMissingSensor()
        } else {
            movementArea.addOnLayoutChangeListener(layoutListener)
            movementArea.post { updateMovementBounds() }
        }
    }

    /**
     * Fallback behavior when the required accelerometer sensor is not found.
     */
    private fun handleMissingSensor() {
        Log.e("MainActivity", "Accelerometer sensor not found on this device.")
        sensorUnavailable.visibility = View.VISIBLE
        // Optionally hide the movement area or ball to indicate the feature is disabled
        ball.visibility = View.GONE
        movementArea.alpha = 0.5f // Dim the area to show it's inactive
    }

    override fun onResume() {
        super.onResume()
        // Prevent crashes by only registering if the sensor exists
        accelerometer?.let { sensor ->
            val supported = sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
            if (!supported) {
                Log.e("MainActivity", "Failed to register accelerometer listener.")
                handleMissingSensor()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Always unregister to save battery and prevent leaks, even if accelerometer is null
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        // Defensive check for sensor type and event data
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 2) return

        // Apply a simple low-pass filter to smooth the sensor data (Reduced Jitter)
        filteredAx = filteredAx + alpha * (event.values[0] - filteredAx)
        filteredAy = filteredAy + alpha * (event.values[1] - filteredAy)

        // Map tilt to incremental position change (Real-Time Continuous Movement)
        ballX -= filteredAx * sensitivity
        ballY -= filteredAy * sensitivity

        if (maxOffsetX <= 0f || maxOffsetY <= 0f) {
            updateMovementBounds()
        }

        // Apply boundaries to prevent the ball from moving off-screen
        ballX = ballX.coerceIn(-maxOffsetX, maxOffsetX)
        ballY = ballY.coerceIn(-maxOffsetY, maxOffsetY)

        // Update UI immediately
        ball.translationX = ballX
        ball.translationY = ballY
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation but required by SensorEventListener
    }

    private fun updateMovementBounds() {
        val wArea = movementArea.width
        val hArea = movementArea.height
        val wBall = ball.width
        val hBall = ball.height
        if (wArea <= 0 || hArea <= 0 || wBall <= 0 || hBall <= 0) return

        maxOffsetX = (wArea - wBall) / 2f
        maxOffsetY = (hArea - hBall) / 2f

        if (maxOffsetX < 0f) maxOffsetX = 0f
        if (maxOffsetY < 0f) maxOffsetY = 0f

        // Re-clamp position if bounds change (e.g. screen rotation or layout changes)
        ballX = ballX.coerceIn(-maxOffsetX, maxOffsetX)
        ballY = ballY.coerceIn(-maxOffsetY, maxOffsetY)
        ball.translationX = ballX
        ball.translationY = ballY
    }
}

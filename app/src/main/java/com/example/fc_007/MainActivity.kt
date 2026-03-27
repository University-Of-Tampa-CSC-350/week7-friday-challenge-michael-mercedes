package com.example.fc_007

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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

    /** Scales small raw accelerometer values (~±10) into on-screen movement. */
    private val movementScale = 45f

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

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        movementArea = findViewById(R.id.movement_area)
        ball = findViewById(R.id.ball)
        sensorUnavailable = findViewById(R.id.sensor_unavailable_message)

        if (accelerometer == null) {
            sensorUnavailable.visibility = View.VISIBLE
        }

        movementArea.addOnLayoutChangeListener(layoutListener)
        movementArea.post { updateMovementBounds() }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        @Suppress("UNUSED_VARIABLE")
        val az = event.values[2]

        // X → horizontal; Y → vertical (negated: screen Y grows downward).
        var tx = ax * movementScale
        var ty = -ay * movementScale

        if (maxOffsetX <= 0f || maxOffsetY <= 0f) {
            updateMovementBounds()
        }

        tx = tx.coerceIn(-maxOffsetX, maxOffsetX)
        ty = ty.coerceIn(-maxOffsetY, maxOffsetY)

        ball.translationX = tx
        ball.translationY = ty
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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

        ball.translationX = ball.translationX.coerceIn(-maxOffsetX, maxOffsetX)
        ball.translationY = ball.translationY.coerceIn(-maxOffsetY, maxOffsetY)
    }
}

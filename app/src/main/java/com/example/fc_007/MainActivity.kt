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
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private lateinit var movementArea: FrameLayout
    private lateinit var ball: View
    private lateinit var sensorUnavailable: TextView
    private lateinit var controlPanel: View
    private lateinit var textAccelX: TextView
    private lateinit var textAccelY: TextView
    private lateinit var seekSensitivity: SeekBar
    private lateinit var textSensitivityValue: TextView

    /** Max |translationX| so the ball stays inside [movementArea]. */
    private var maxOffsetX = 0f
    private var maxOffsetY = 0f

    /** Current ball position offsets from the center. */
    private var ballX = 0f
    private var ballY = 0f

    /** Filtered sensor values to reduce jitter and ensure smooth movement. */
    private var filteredAx = 0f
    private var filteredAy = 0f
    private val alpha = 0.15f

    /** Movement gain; user-adjustable via [seekSensitivity] (bonus). */
    private var sensitivity = DEFAULT_SENSITIVITY

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateMovementBounds()
    }

    private val sensitivitySeekListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            sensitivity = progressToSensitivity(progress)
            updateSensitivityLabel()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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
        controlPanel = findViewById(R.id.control_panel)
        textAccelX = findViewById(R.id.text_accel_x)
        textAccelY = findViewById(R.id.text_accel_y)
        seekSensitivity = findViewById(R.id.seek_sensitivity)
        textSensitivityValue = findViewById(R.id.text_sensitivity_value)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            handleMissingSensor()
        } else {
            setupBonusControls()
            movementArea.addOnLayoutChangeListener(layoutListener)
            movementArea.post { updateMovementBounds() }
        }
    }

    private fun setupBonusControls() {
        seekSensitivity.max = SEEK_MAX
        seekSensitivity.progress = sensitivityToProgress(sensitivity)
        updateSensitivityLabel()
        seekSensitivity.setOnSeekBarChangeListener(sensitivitySeekListener)
        textAccelX.text = getString(R.string.accel_x_fmt, 0f)
        textAccelY.text = getString(R.string.accel_y_fmt, 0f)
    }

    private fun updateSensitivityLabel() {
        textSensitivityValue.text = getString(R.string.sensitivity_value_fmt, sensitivity)
    }

    private fun progressToSensitivity(progress: Int): Float {
        val t = progress.coerceIn(0, SEEK_MAX) / SEEK_MAX.toFloat()
        return SENS_MIN + (SENS_MAX - SENS_MIN) * t
    }

    private fun sensitivityToProgress(s: Float): Int {
        val t = ((s - SENS_MIN) / (SENS_MAX - SENS_MIN)).coerceIn(0f, 1f)
        return (t * SEEK_MAX).roundToInt().coerceIn(0, SEEK_MAX)
    }

    private fun handleMissingSensor() {
        Log.e("MainActivity", "Accelerometer sensor not found on this device.")
        sensorUnavailable.visibility = View.VISIBLE
        controlPanel.visibility = View.GONE
        ball.visibility = View.GONE
        movementArea.alpha = 0.5f
    }

    override fun onResume() {
        super.onResume()
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
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        if (!ax.isFinite() || !ay.isFinite() || !az.isFinite()) return

        filteredAx = filteredAx + alpha * (ax - filteredAx)
        filteredAy = filteredAy + alpha * (ay - filteredAy)

        textAccelX.text = getString(R.string.accel_x_fmt, filteredAx)
        textAccelY.text = getString(R.string.accel_y_fmt, filteredAy)

        ballX -= filteredAx * sensitivity
        ballY -= filteredAy * sensitivity

        if (maxOffsetX <= 0f || maxOffsetY <= 0f) {
            updateMovementBounds()
        }

        ballX = ballX.coerceIn(-maxOffsetX, maxOffsetX)
        ballY = ballY.coerceIn(-maxOffsetY, maxOffsetY)

        ball.translationX = ballX
        ball.translationY = ballY
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

        ballX = ballX.coerceIn(-maxOffsetX, maxOffsetX)
        ballY = ballY.coerceIn(-maxOffsetY, maxOffsetY)
        ball.translationX = ballX
        ball.translationY = ballY
    }

    companion object {
        private const val SENS_MIN = 0.5f
        private const val SENS_MAX = 6f
        private const val SEEK_MAX = 100
        private const val DEFAULT_SENSITIVITY = 2.5f
    }
}

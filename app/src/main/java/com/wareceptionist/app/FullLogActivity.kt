package com.wareceptionist.app

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FullLogActivity : AppCompatActivity() {

    private lateinit var logTextFull: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_log)

        logTextFull = findViewById(R.id.logTextFull)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnClearLogs).setOnClickListener {
            AppLogger.clearLogs(this)
            updateLogs()
        }

        updateLogs()
    }

    private fun updateLogs() {
        logTextFull.text = AppLogger.getLogs(this)
    }
}

package com.example.a2_14_services

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView

    private var timerService: TimerForegroundService? = null
    private var isBound = false
    private var collectJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerForegroundService.LocalBinder
            val myService = binder.getService()
            timerService = myService
            isBound = true

            collectJob?.cancel()
            collectJob = lifecycleScope.launch {
                myService.getTimerFlow().collect { seconds ->
                    tvTimer.text = "$seconds с"
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            timerService = null
            collectJob?.cancel()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTimerService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTimer = findViewById(R.id.tvTimer)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    startTimerService()
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                startTimerService()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            timerService?.stopTimer()
            val intent = Intent(this, TimerForegroundService::class.java)
            stopService(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, TimerForegroundService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            collectJob?.cancel()
            unbindService(connection)
            isBound = false
            timerService = null
        }
    }

    private fun startTimerService() {
        val intent = Intent(this, TimerForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
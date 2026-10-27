package com.example.tushnitsa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var remoteView: View

    private lateinit var tvScriptText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnEdit: Button
    private lateinit var btnClose: Button
    private lateinit var seekBarOpacity: SeekBar
    private lateinit var seekBarTextSize: SeekBar
    private lateinit var overlayContainer: View

    private lateinit var btnRemotePlus: Button
    private lateinit var btnRemoteMinus: Button
    private lateinit var btnRemotePause: Button
    private lateinit var remoteContainer: View

    private var isScrolling = false
    private var isHoldingPause = false
    private var scrollSpeed = 5
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // Запуск нерасходуемой системы переднего плана (защита от вылета через 20 секунд)
        startForegroundServiceWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)

        overlayView = inflater.inflate(R.layout.overlay_teleprompter, null)
        remoteView = inflater.inflate(R.layout.overlay_remote, null)

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val textParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 60
        }

        val remoteParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 40
            y = 120
        }

        windowManager.addView(overlayView, textParams)
        windowManager.addView(remoteView, remoteParams)

        overlayContainer = overlayView.findViewById(R.id.overlayContainer)
        tvScriptText = overlayView.findViewById(R.id.tvScriptText)
        scrollView = overlayView.findViewById(R.id.scrollView)
        btnEdit = overlayView.findViewById(R.id.btnEdit)
        btnClose = overlayView.findViewById(R.id.btnClose)
        seekBarOpacity = overlayView.findViewById(R.id.seekBarOpacity)
        seekBarTextSize = overlayView.findViewById(R.id.seekBarTextSize)

        remoteContainer = remoteView.findViewById(R.id.remoteContainer)
        btnRemotePlus = remoteView.findViewById(R.id.btnRemotePlus)
        btnRemoteMinus = remoteView.findViewById(R.id.btnRemoteMinus)
        btnRemotePause = remoteView.findViewById(R.id.btnRemotePause)

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isHoldingPause) {
                    if (isScrolling) stopAutoScroll() else startAutoScroll()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                stopAutoScroll()
                scrollView.scrollTo(0, 0)
                return true
            }

            override fun onDown(e: MotionEvent): Boolean = true
        })

        scrollView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            false
        }

        var textInitialY = 0
        var textTouchY = 0f
        overlayContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    textInitialY = textParams.y
                    textTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    textParams.y = textInitialY + (event.rawY - textTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, textParams)
                    true
                }
                else -> false
            }
        }

        var remoteInitialX = 0
        var remoteInitialY = 0
        var remoteTouchX = 0f
        var remoteTouchY = 0f
        remoteContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    remoteInitialX = remoteParams.x
                    remoteInitialY = remoteParams.y
                    remoteTouchX = event.rawX
                    remoteTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    remoteParams.x = remoteInitialX - (event.rawX - remoteTouchX).toInt()
                    remoteParams.y = remoteInitialY - (event.rawY - remoteTouchY).toInt()
                    windowManager.updateViewLayout(remoteView, remoteParams)
                    true
                }
                else -> false
            }
        }

        btnRemotePlus.setOnClickListener {
            scrollSpeed = (scrollSpeed + 1).coerceAtMost(25)
        }

        btnRemoteMinus.setOnClickListener {
            scrollSpeed = (scrollSpeed - 1).coerceAtLeast(1)
        }

        btnRemotePause.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHoldingPause = true
                    handler.removeCallbacks(runnable!!)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHoldingPause = false
                    if (isScrolling) {
                        handler.post(runnable!!)
                    }
                    true
                }
                else -> false
            }
        }

        btnEdit.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            stopSelf()
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        seekBarOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = progress.coerceAtLeast(40) / 100f
                overlayContainer.alpha = alpha
                remoteContainer.alpha = alpha
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvScriptText.textSize = 14f + progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setupAutoScroll()
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "tushnitsa_prompter_channel"
        val channelName = "Tushnitsa Teleprompter"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Tushnitsa")
            .setContentText("Телесуфлер активен")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rawText = intent?.getStringExtra("SCRIPT_TEXT") ?: "Нет текста"
        tvScriptText.text = "\n\n$rawText\n\n"
        return START_NOT_STICKY
    }

    private fun setupAutoScroll() {
        runnable = object : Runnable {
            override fun run() {
                if (isScrolling && !isHoldingPause) {
                    scrollView.smoothScrollBy(0, scrollSpeed)
                    handler.postDelayed(this, 50)
                }
            }
        }
    }

    private fun startAutoScroll() {
        isScrolling = true
        handler.post(runnable!!)
    }

    private fun stopAutoScroll() {
        isScrolling = false
        handler.removeCallbacks(runnable!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoScroll()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
        if (::remoteView.isInitialized) {
            windowManager.removeView(remoteView)
        }
    }
}
package com.rideradar.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.rideradar.app.R
import com.rideradar.app.radar.PoiItem
import com.rideradar.app.radar.PoiSearchManager
import com.rideradar.app.radar.SmartVoiceAnnouncer
import com.rideradar.app.utils.AmapNaviHelper
import com.rideradar.app.utils.PreferencesManager
import kotlinx.coroutines.launch

class RadarFloatingService : LifecycleService(), LocationListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var prefs: PreferencesManager
    private lateinit var voiceAnnouncer: SmartVoiceAnnouncer
    private lateinit var poiSearchManager: PoiSearchManager
    private var locationManager: LocationManager? = null

    private var currentSpeedKmh = 0f
    private var latestPoi: PoiItem? = null

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE_KEY = "ACTION_UPDATE_KEY"
        const val BROADCAST_POI_FOUND = "com.rideradar.app.POI_FOUND"
        const val BROADCAST_STATUS_UPDATE = "com.rideradar.app.STATUS_UPDATE"
        private const val CHANNEL_ID = "ride_radar_channel"
        private const val NOTIFICATION_ID = 1001
        var isServiceRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        voiceAnnouncer = SmartVoiceAnnouncer(this)
        poiSearchManager = PoiSearchManager(prefs.amapKey)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        createNotificationChannel()
        val notification = buildForegroundNotification("伴游雷达正在运行", "已开启高德骑行周边探测")
        startForeground(NOTIFICATION_ID, notification)

        initFloatingBubble()
        startLocationUpdates()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_UPDATE_KEY -> {
                poiSearchManager = PoiSearchManager(prefs.amapKey)
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "骑行雷达后台伴游",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台GPS与高德联动探测"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(title: String, content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_radar)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.view_floating_bubble, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        val tvTitle = floatingView?.findViewById<TextView>(R.id.tv_bubble_title)
        val tvSub = floatingView?.findViewById<TextView>(R.id.tv_bubble_sub)
        val btnNav = floatingView?.findViewById<Button>(R.id.btn_bubble_nav)
        val btnClose = floatingView?.findViewById<View>(R.id.btn_bubble_close)

        btnNav?.setOnClickListener {
            latestPoi?.let { poi ->
                AmapNaviHelper.startRideNavi(this, poi)
            }
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }

        // 实现悬浮窗平滑拖拽手势
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        try {
            // 优先使用 GPS，其次网络定位
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    3f,
                    this
                )
            }
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    5f,
                    this
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        currentSpeedKmh = (location.speed * 3.6f)

        // 广播速度与坐标状态
        val statusIntent = Intent(BROADCAST_STATUS_UPDATE).apply {
            putExtra("speed", currentSpeedKmh)
            putExtra("lat", location.latitude)
            putExtra("lon", location.longitude)
        }
        sendBroadcast(statusIntent)

        // 自适应半径策略：如果开启了自适应且骑速 > 15 km/h，自动扩宽至 80~100米
        val effectiveRadius = if (prefs.isSpeedAdaptive && currentSpeedKmh > 15f) {
            (prefs.searchRadius * 1.6f).toInt().coerceAtMost(150)
        } else {
            prefs.searchRadius
        }

        lifecycleScope.launch {
            val pois = poiSearchManager.searchAround(
                lat = location.latitude,
                lon = location.longitude,
                radius = effectiveRadius,
                includeFood = prefs.isFilterFood,
                includeScenic = prefs.isFilterScenic
            )
            processFoundPois(pois)
        }
    }

    private fun processFoundPois(pois: List<PoiItem>) {
        if (pois.isEmpty()) return
        val bestPoi = pois.first()
        latestPoi = bestPoi

        // 更新悬浮胶囊界面
        floatingView?.post {
            floatingView?.findViewById<TextView>(R.id.tv_bubble_title)?.text = bestPoi.name
            floatingView?.findViewById<TextView>(R.id.tv_bubble_sub)?.text =
                "${bestPoi.type} · 距你${bestPoi.distance}米"
            floatingView?.findViewById<Button>(R.id.btn_bubble_nav)?.visibility = View.VISIBLE
        }

        // 智能语音播报
        if (prefs.isVoiceEnabled) {
            voiceAnnouncer.announcePoi(bestPoi)
        }

        // 广播给主界面记录
        val poiIntent = Intent(BROADCAST_POI_FOUND).apply {
            putExtra("poi_name", bestPoi.name)
            putExtra("poi_type", bestPoi.type)
            putExtra("poi_rating", bestPoi.rating)
            putExtra("poi_dist", bestPoi.distance)
            putExtra("poi_lat", bestPoi.latitude)
            putExtra("poi_lon", bestPoi.longitude)
            putExtra("poi_addr", bestPoi.address)
        }
        sendBroadcast(poiIntent)
    }



    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        locationManager?.removeUpdates(this)
        voiceAnnouncer.shutdown()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}

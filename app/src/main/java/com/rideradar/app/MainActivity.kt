package com.rideradar.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.rideradar.app.databinding.ActivityMainBinding
import com.rideradar.app.radar.PoiItem
import com.rideradar.app.service.RadarFloatingService
import com.rideradar.app.utils.AmapNaviHelper
import com.rideradar.app.utils.PreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager

    // 运行时权限请求器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            checkOverlayPermissionAndStart()
        } else {
            Toast.makeText(this, "需要精准定位权限以探测沿途美食景点", Toast.LENGTH_LONG).show()
        }
    }

    // 广播接收器：更新骑行状态与POI发现卡片
    private val radarUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RadarFloatingService.BROADCAST_STATUS_UPDATE -> {
                    val speed = intent.getFloatExtra("speed", 0f)
                    val lat = intent.getDoubleExtra("lat", 0.0)
                    val lon = intent.getDoubleExtra("lon", 0.0)
                    binding.tvLocationDebug.text = String.format("时速: %.1f km/h · 坐标: %.4f, %.4f", speed, lat, lon)
                }
                RadarFloatingService.BROADCAST_POI_FOUND -> {
                    val name = intent.getStringExtra("poi_name") ?: "未知"
                    val type = intent.getStringExtra("poi_type") ?: "生活"
                    val rating = intent.getStringExtra("poi_rating") ?: "4.6"
                    val dist = intent.getIntExtra("poi_dist", 50)
                    val lat = intent.getDoubleExtra("poi_lat", 0.0)
                    val lon = intent.getDoubleExtra("poi_lon", 0.0)
                    val addr = intent.getStringExtra("poi_addr") ?: ""

                    val poi = PoiItem(
                        id = System.currentTimeMillis().toString(),
                        name = name,
                        type = type,
                        rating = rating,
                        distance = dist,
                        latitude = lat,
                        longitude = lon,
                        address = addr
                    )
                    addPoiCard(poi)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        initViews()
        registerReceivers()
    }

    private fun initViews() {
        // 同步配置项
        binding.sliderRadius.value = prefs.searchRadius.toFloat()
        binding.tvRadiusDisplay.text = "探测半径：${prefs.searchRadius} 米"
        binding.switchVoice.isChecked = prefs.isVoiceEnabled
        binding.switchAdaptiveSpeed.isChecked = prefs.isSpeedAdaptive
        binding.switchFilterFood.isChecked = prefs.isFilterFood
        binding.switchFilterScenic.isChecked = prefs.isFilterScenic
        binding.etAmapKey.setText(prefs.amapKey)

        // 半径滑块监听
        binding.sliderRadius.addOnChangeListener { _, value, _ ->
            val r = value.toInt()
            prefs.searchRadius = r
            binding.tvRadiusDisplay.text = "探测半径：$r 米"
        }

        // 开关监听
        binding.switchVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.isVoiceEnabled = isChecked
        }
        binding.switchAdaptiveSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSpeedAdaptive = isChecked
        }
        binding.switchFilterFood.setOnCheckedChangeListener { _, isChecked ->
            prefs.isFilterFood = isChecked
        }
        binding.switchFilterScenic.setOnCheckedChangeListener { _, isChecked ->
            prefs.isFilterScenic = isChecked
        }

        // 高德 Key 实时监听与格式校验
        fun validateAndSaveKey(keyText: String) {
            val trimmed = keyText.trim()
            prefs.amapKey = trimmed

            when {
                trimmed.isEmpty() -> {
                    binding.tilAmapKey.helperText = "未填将默认使用智能模拟路过"
                    binding.tilAmapKey.error = null
                }
                trimmed.length == 32 -> {
                    binding.tilAmapKey.helperText = "✅ 高德 Web Key 格式正确，已实时生效"
                    binding.tilAmapKey.error = null
                }
                else -> {
                    binding.tilAmapKey.helperText = null
                    binding.tilAmapKey.error = "⚠️ 当前仅 ${trimmed.length} 位（高德标准为 32 位），请检查末尾是否被截断"
                }
            }

            // 如果服务正在后台运行，实时热更新它的 Key
            if (RadarFloatingService.isServiceRunning) {
                val updateIntent = Intent(this, RadarFloatingService::class.java).apply {
                    action = RadarFloatingService.ACTION_UPDATE_KEY
                }
                startService(updateIntent)
            }
        }

        validateAndSaveKey(prefs.amapKey)
        binding.etAmapKey.doAfterTextChanged {
            validateAndSaveKey(it?.toString() ?: "")
        }

        // 启停雷达按钮 (只有点下去才会开启)
        updateButtonStatus()
        binding.btnToggleRadar.setOnClickListener {
            prefs.amapKey = binding.etAmapKey.text?.toString()?.trim() ?: ""
            if (RadarFloatingService.isServiceRunning) {
                stopRadarService()
            } else {
                startRadarServiceWithCheck()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStatus()
    }

    private fun updateButtonStatus() {
        if (RadarFloatingService.isServiceRunning) {
            binding.tvRadarStatus.text = getString(R.string.radar_status_running)
            binding.btnToggleRadar.text = getString(R.string.btn_stop_radar)
            binding.btnToggleRadar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            binding.tvRadarStatus.text = getString(R.string.radar_status_stopped)
            binding.btnToggleRadar.text = getString(R.string.btn_start_radar)
            binding.btnToggleRadar.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun startRadarServiceWithCheck() {
        // 1. 检查定位权限
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestPermissionLauncher.launch(permissions.toTypedArray())
            return
        }

        checkOverlayPermissionAndStart()
    }

    private fun checkOverlayPermissionAndStart() {
        // 2. 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "骑行雷达需要“悬浮窗”权限以配合高德地图显示", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 3. 启动前台悬浮服务
        val serviceIntent = Intent(this, RadarFloatingService::class.java).apply {
            action = RadarFloatingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        updateButtonStatus()
        Toast.makeText(this, "伴游雷达已启动！可切到高德地图骑行", Toast.LENGTH_SHORT).show()
    }

    private fun stopRadarService() {
        val serviceIntent = Intent(this, RadarFloatingService::class.java).apply {
            action = RadarFloatingService.ACTION_STOP
        }
        stopService(serviceIntent)
        updateButtonStatus()
        Toast.makeText(this, "雷达已停止", Toast.LENGTH_SHORT).show()
    }

    private fun addPoiCard(poi: PoiItem) {
        val itemView = LayoutInflater.from(this).inflate(R.layout.item_poi_record, binding.layoutPoiList, false)
        itemView.findViewById<TextView>(R.id.tv_item_name).text = poi.name
        itemView.findViewById<TextView>(R.id.tv_item_tag).text = "${poi.type} ${poi.rating}★"
        itemView.findViewById<TextView>(R.id.tv_item_desc).text = "距探测点约 ${poi.distance} 米 · 已自动语音播报"

        itemView.findViewById<Button>(R.id.btn_item_amap).setOnClickListener {
            AmapNaviHelper.startRideNavi(this, poi)
        }

        // 插入到列表最上方
        binding.layoutPoiList.addView(itemView, 0)
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(RadarFloatingService.BROADCAST_STATUS_UPDATE)
            addAction(RadarFloatingService.BROADCAST_POI_FOUND)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(radarUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(radarUpdateReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(radarUpdateReceiver)
    }
}

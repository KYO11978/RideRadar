package com.rideradar.app.radar

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class SmartVoiceAnnouncer(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    // 记录已经播报过的地点ID及其时间戳 (毫秒)，用于去重
    private val announcedPoiCache = ConcurrentHashMap<String, Long>()

    // 上一次播报的时间戳
    private var lastAnnounceTime = 0L

    // 播报冷却时间：20秒 (骑行中避免频繁连珠炮式打扰)
    private val coolDownMillis = 20_000L

    // 重复播报限制：30分钟内同一地点不重复
    private val duplicateExpireMillis = 30 * 60 * 1000L

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            }
            // 设置导航引导类音频属性，保证蓝牙耳机和系统混音体验
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            tts?.setSpeechRate(1.05f) // 稍快语速，利于骑行快速捕捉
            isInitialized = true
            Log.d("SmartVoiceAnnouncer", "TTS 语音播报初始化成功")
        } else {
            Log.e("SmartVoiceAnnouncer", "TTS 语音引擎初始化失败: $status")
        }
    }

    /**
     * 智能评估并播报 POI
     * @return 是否成功播报
     */
    fun announcePoi(poi: PoiItem): Boolean {
        val now = System.currentTimeMillis()

        // 1. 冷却时间检查 (20秒内不连播)
        if (now - lastAnnounceTime < coolDownMillis) {
            return false
        }

        // 2. 去重检查 (30分钟内同一地点不重复播报)
        val lastTime = announcedPoiCache[poi.name]
        if (lastTime != null && (now - lastTime < duplicateExpireMillis)) {
            return false
        }

        // 3. 构造自然流畅的骑行语音文案
        val speechText = buildSpeechText(poi)

        // 4. 执行播报
        if (isInitialized && tts != null) {
            tts?.speak(speechText, TextToSpeech.QUEUE_ADD, null, poi.id)
            lastAnnounceTime = now
            announcedPoiCache[poi.name] = now
            return true
        }
        return false
    }

    private fun buildSpeechText(poi: PoiItem): String {
        val categoryText = if (poi.type.contains("美食") || poi.type.contains("餐饮")) {
            "特色美食"
        } else {
            "热门景点"
        }
        return "前方约${poi.distance}米路过${categoryText}：${poi.name}，评分${poi.rating}分。"
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

package com.rideradar.app.radar

data class PoiItem(
    val id: String,
    val name: String,
    val type: String,          // 美食 / 景点 / 其他
    val rating: String,        // 评分，如 "4.8"
    val distance: Int,         // 距离当前米数
    val latitude: Double,      // 纬度
    val longitude: Double,     // 经度
    val address: String,       // 详细地址
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getDisplayText(): String {
        return "$name (${rating}★, $distance 米)"
    }
}

package com.rideradar.app.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rideradar.app.radar.PoiItem

object AmapNaviHelper {

    /**
     * 调起手机端高德地图，进入骑行/步行导航
     */
    fun startRideNavi(context: Context, poi: PoiItem) {
        val lat = poi.latitude
        val lon = poi.longitude
        val name = Uri.encode(poi.name)

        // 优先使用高德地图骑行路径规划协议 (t=3 代表骑行)
        val amapUri = "amapuri://route/plan/?dlat=$lat&dlon=$lon&dname=$name&dev=0&t=3"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(amapUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage("com.autonavi.minimap")
        }

        try {
            if (isPackageInstalled(context, "com.autonavi.minimap")) {
                context.startActivity(intent)
            } else {
                // 如果未安装高德地图 App，降级为打开高德网页版导航
                val webUri = "https://uri.amap.com/navigation?to=$lon,$lat,$name&mode=ride&policy=1&coordinate=gaode&callnative=1"
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Toast.makeText(context, "未检测到高德地图客户端，已为您打开高德网页导航", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "唤醒高德地图失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}

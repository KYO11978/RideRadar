package com.rideradar.app.radar

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class PoiSearchManager(private val amapKey: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 执行周边兴趣点检索
     * @param lat 当前纬度
     * @param lon 当前经度
     * @param radius 探测半径 (米)
     * @param includeFood 是否包含美食
     * @param includeScenic 是否包含景点
     */
    suspend fun searchAround(
        lat: Double,
        lon: Double,
        radius: Int,
        includeFood: Boolean = true,
        includeScenic: Boolean = true
    ): List<PoiItem> = withContext(Dispatchers.IO) {
        // 如果未填 Key，进入贴心的体验模拟模式
        if (amapKey.isBlank()) {
            return@withContext generateMockPois(lat, lon, radius, includeFood, includeScenic)
        }

        val typeCodes = mutableListOf<String>()
        if (includeFood) typeCodes.add("050000")   // 餐饮服务
        if (includeScenic) typeCodes.add("110000") // 风景名胜
        if (typeCodes.isEmpty()) return@withContext emptyList()

        val typesParam = typeCodes.joinToString("|")
        val url = "https://restapi.amap.com/v3/place/around" +
                "?key=$amapKey" +
                "&location=$lon,$lat" +
                "&radius=$radius" +
                "&types=$typesParam" +
                "&sortrule=distance" +
                "&output=json"

        try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("PoiSearchManager", "高德API请求失败: ${response.code}")
                    return@withContext emptyList()
                }

                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val rootJson = gson.fromJson(bodyStr, JsonObject::class.java)

                val status = rootJson.get("status")?.asString
                if (status != "1") {
                    Log.w("PoiSearchManager", "高德接口返回异常: ${rootJson.get("info")?.asString}")
                    return@withContext emptyList()
                }

                val poisJson = rootJson.getAsJsonArray("pois") ?: JsonArray()
                val resultList = mutableListOf<PoiItem>()

                for (element in poisJson) {
                    val item = element.asJsonObject
                    val id = item.get("id")?.asString ?: ""
                    val name = item.get("name")?.asString ?: "未知地点"
                    val type = item.get("type")?.asString ?: "生活服务"
                    val distance = item.get("distance")?.asString?.toIntOrNull() ?: radius
                    val locationStr = item.get("location")?.asString ?: "$lon,$lat"
                    val parts = locationStr.split(",")
                    val poiLon = parts.getOrNull(0)?.toDoubleOrNull() ?: lon
                    val poiLat = parts.getOrNull(1)?.toDoubleOrNull() ?: lat
                    val address = item.get("address")?.asString ?: ""

                    // 解析高德 biz_ext.rating 评分
                    val bizExt = item.getAsJsonObject("biz_ext")
                    val rating = bizExt?.get("rating")?.asString?.takeIf { it.isNotBlank() && it != "[]" } ?: "4.6"

                    resultList.add(
                        PoiItem(
                            id = id,
                            name = name,
                            type = if (type.contains("餐饮")) "美食" else "景点",
                            rating = rating,
                            distance = distance,
                            latitude = poiLat,
                            longitude = poiLon,
                            address = address
                        )
                    )
                }

                return@withContext resultList
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    /**
     * 智能模拟生成器（免配Key或室内测试时使用）
     */
    fun generateMockPois(
        lat: Double,
        lon: Double,
        radius: Int,
        includeFood: Boolean,
        includeScenic: Boolean
    ): List<PoiItem> {
        val mockData = mutableListOf<PoiItem>()
        val foodNames = listOf("老上海生煎馆", "阿婆手打葱油饼", "古法牛肉拉面", "弄堂小馄饨", "现烤手工面包屋")
        val scenicNames = listOf("历史文化保护街区", "滨江骑行绿道驿站", "近代历史建筑群", "城市街角口袋公园")

        if (includeFood) {
            val name = foodNames[Random.nextInt(foodNames.size)]
            val dist = Random.nextInt(15, radius.coerceAtLeast(20))
            mockData.add(
                PoiItem(
                    id = "mock_food_${System.currentTimeMillis()}",
                    name = name,
                    type = "美食",
                    rating = String.format("%.1f", Random.nextDouble(4.5, 4.9)),
                    distance = dist,
                    latitude = lat + 0.0002,
                    longitude = lon + 0.0002,
                    address = "骑行沿途道路右侧"
                )
            )
        }

        if (includeScenic && Random.nextBoolean()) {
            val name = scenicNames[Random.nextInt(scenicNames.size)]
            val dist = Random.nextInt(25, radius.coerceAtLeast(30))
            mockData.add(
                PoiItem(
                    id = "mock_scenic_${System.currentTimeMillis()}",
                    name = name,
                    type = "景点",
                    rating = String.format("%.1f", Random.nextDouble(4.6, 5.0)),
                    distance = dist,
                    latitude = lat + 0.0004,
                    longitude = lon + 0.0003,
                    address = "骑行绿道旁"
                )
            )
        }

        return mockData
    }
}

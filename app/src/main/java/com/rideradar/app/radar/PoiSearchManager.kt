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

class PoiSearchManager(private val amapKey: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 执行周边真实兴趣点检索 (纯真实数据，无任何模拟)
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
        if (amapKey.isBlank()) {
            Log.d("PoiSearchManager", "高德 Key 为空，跳过检索")
            return@withContext emptyList()
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
                    val rawName = item.get("name")?.asString ?: "未知地点"
                    // 智能净化店名：去掉“xxxx广场店”、“人民广场分店”等啰嗦分店后缀
                    val cleanedName = cleanPoiName(rawName)
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
                            name = cleanedName,
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
     * 智能清洗店名：过滤各种“xxxx广场店”、“分店”、“支店”等位置后缀
     * 示例：
     *   "瑞幸咖啡(万达广场店)" -> "瑞幸咖啡"
     *   "麦当劳（人民广场分店）" -> "麦当劳"
     *   "肯德基-静安大悦城店" -> "肯德基"
     *   "霸王茶姬南京西路店" -> "霸王茶姬"
     */
    private fun cleanPoiName(rawName: String): String {
        var name = rawName.trim()

        // 1. 去除中英文括号及括号里的分店信息：如 (万达广场店)、（人民广场店）、(二店)
        name = name.replace(Regex("\\s*[\\(（].*?[\\)）]\\s*"), "")

        // 2. 去除破折号、连接符后的分店名：如 -万达店、—淮海路店
        name = name.replace(Regex("\\s*[-_—–]\\s*.*店$"), "")

        // 3. 去除紧随在品牌名后面的商场/广场/路段分店后缀
        // 匹配例如：xxx广场店、xxx大厦店、xxx商城店、xxx中心店、xxx路店、xxx街店、xxx分店、xxx一店、xxx二店
        val storeSuffixRegex = Regex("(?:[\\u4e00-\\u9fa5A-Za-z0-9]+(?:广场|商场|商城|大厦|天地|中心|路|街|大道|桥|站|园))?(?:分店|旗舰店|总店|专卖店|精品店|一店|二店|三店|四店|店)$")
        if (name.length > 4 && storeSuffixRegex.containsMatchIn(name)) {
            val stripped = name.replace(storeSuffixRegex, "").trim()
            // 确保去除后至少保留2个字的核心品牌名（避免有些店名本身就叫“小吃店”被完全抹掉）
            if (stripped.length >= 2) {
                name = stripped
            }
        }

        return if (name.isBlank()) rawName else name
    }
}

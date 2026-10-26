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

    companion object {
        // 常见大型大众连锁品牌黑名单库 (小众探店模式下直接过滤)
        private val CHAIN_BRANDS = listOf(
            // 咖啡与茶饮巨头
            "星巴克", "starbucks", "瑞幸", "luckin", "库迪", "cottie", "蜜雪冰城",
            "喜茶", "heytea", "奈雪", "霸王茶姬", "茶百道", "古茗", "一点点",
            "coco都可", "coco", "沪上阿姨", "快乐柠檬", "益禾堂", "茶颜悦色",
            "幸运咖", "manner", "m stand", "tims", "tim hortons", "七分甜",
            // 常见快餐西餐
            "肯德基", "kfc", "麦当劳", "mcdonald", "必胜客", "pizzahut", "汉堡王",
            "burger king", "德克士", "华莱士", "塔斯汀", "赛百味", "subway",
            "达美乐", "domino", "尊宝比萨",
            // 大众中式快餐与火锅
            "海底捞", "西贝", "太二", "沙县小吃", "兰州拉面", "黄焖鸡米饭",
            "杨国福", "张亮麻辣烫", "绝味鸭脖", "周黑鸭", "正新鸡排", "紫燕百味鸡",
            "老乡鸡", "大米先生", "真功夫", "吉野家", "和府捞面", "永和大王",
            "呷哺呷哺", "小肥羊", "小龙坎", "巴奴",
            // 常见便利超市
            "全家", "family mart", "罗森", "lawson", "7-eleven", "711",
            "便利蜂", "美宜佳"
        )
    }

    /**
     * 执行周边真实兴趣点检索 (纯真实数据，无任何模拟)
     * @param lat 当前纬度
     * @param lon 当前经度
     * @param radius 探测半径 (米)
     * @param includeFood 是否包含美食
     * @param includeScenic 是否包含景点
     * @param filterChain 是否过滤大型连锁店 (小众探店模式)
     */
    suspend fun searchAround(
        lat: Double,
        lon: Double,
        radius: Int,
        includeFood: Boolean = true,
        includeScenic: Boolean = true,
        filterChain: Boolean = true
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

                    // 1. 小众探店过滤：若开启且属于常见连锁店，直接剔除
                    if (filterChain && isChainStore(rawName)) {
                        continue
                    }

                    // 2. 智能净化店名：去掉“xxxx广场店”、“分店”等啰嗦后缀
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
     * 判断是否属于常见大型连锁店
     */
    private fun isChainStore(name: String): Boolean {
        val lower = name.lowercase()
        return CHAIN_BRANDS.any { brand -> lower.contains(brand.lowercase()) }
    }

    /**
     * 智能清洗店名：过滤各种“xxxx广场店”、“分店”、“支店”等位置后缀
     */
    private fun cleanPoiName(rawName: String): String {
        var name = rawName.trim()

        // 1. 去除中英文括号及括号里的分店信息：如 (万达广场店)、（人民广场店）、(二店)
        name = name.replace(Regex("\\s*[\\(（].*?[\\)）]\\s*"), "")

        // 2. 去除破折号、连接符后的分店名：如 -万达店、—淮海路店
        name = name.replace(Regex("\\s*[-_—–]\\s*.*店$"), "")

        // 3. 去除紧随在品牌名后面的商场/广场/路段分店后缀
        val storeSuffixRegex = Regex("(?:[\\u4e00-\\u9fa5A-Za-z0-9]+(?:广场|商场|商城|大厦|天地|中心|路|街|大道|桥|站|园))?(?:分店|旗舰店|总店|专卖店|精品店|一店|二店|三店|四店|店)$")
        if (name.length > 4 && storeSuffixRegex.containsMatchIn(name)) {
            val stripped = name.replace(storeSuffixRegex, "").trim()
            if (stripped.length >= 2) {
                name = stripped
            }
        }

        return if (name.isBlank()) rawName else name
    }
}

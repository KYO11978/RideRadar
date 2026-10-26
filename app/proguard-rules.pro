# ProGuard rules for RideRadar

# OkHttp & Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 保留 POI 数据模型
-keep class com.rideradar.app.radar.PoiItem { *; }

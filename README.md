# 🚴 骑行伴游雷达 (RideRadar for Android)

> **专为骑行爱好者打造的沿途美食与景点雷达 · 完美配合高德地图使用**

[![Build Android APK](https://github.com/your-username/RideRadar/actions/workflows/build-apk.yml/badge.svg)](https://github.com/your-username/RideRadar/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%208.0%2B-blue.svg)](https://android.com)

---

## 💡 为什么需要骑行雷达？

在户外骑车时，大家通常把手机架在车把上使用**高德地图**进行导航。  
但高德地图本身主要用于指引转弯路线，无法在骑行途中**主动像电子导游一样**告诉你：
* “路边 35 米其实藏着一家老字号葱油饼！”
* “右侧 50 米就是网红历史建筑/打卡绿道驿站！”

**骑行伴游雷达 (RideRadar)** 就是为了解决这个问题而诞生的独立 Android 应用：  
它可以在后台静默运行，浮在高德地图角落，通过蓝牙耳机自动语音播报路边的精彩地点；当你心动想去时，轻轻一点悬浮窗，就能**直接在高德地图里开启带路导航**！

---

## ✨ 核心特性

- **🎈 悬浮雷达胶囊**：
  - 采用系统悬浮窗技术，悬浮在高德地图屏幕上方，不挡导航路线。
  - 支持随手拖拽边缘停靠，路过美食景点时小胶囊自动弹显名称与距离。
- **🎧 智能耳机语音播报 (TTS)**：
  - 手机系统原生离线语音发音，零流量消耗，声音清晰自然。
  - **防嘴碎机制**：同一地点 30 分钟内不重复播报；每次播报后自动冷却 20 秒，绝不连珠炮式打扰骑行。
  - 自动适配蓝牙耳机，与高德导航语音平滑融合。
- **⚡ 速度自适应前探算法**：
  - 静止或慢速骑行（< 15 km/h）：精准探测身边 30~50 米范围；
  - 快速骑行（> 15 km/h）：自动将雷达前探至 80~100 米，留出充足的减速和看路时间。
- **🗺️ 高德地图深度联动**：
  - 点击悬浮窗或历史卡片的【高德导航】按钮，一键调起手机内已安装的高德地图客户端并自动开启骑行规划。
- **☁️ GitHub Actions 自动化云打包**：
  - 本项目已预置 CI/CD 流水线，代码推送到 GitHub 后**自动在云端编译出 APK 安装包**，无需在本地电脑配置繁重的 Android Studio 环境！

---

## 📱 界面预览与交互说明

```text
       手机屏幕 (骑行支架)
+-------------------------------+
|  [高德地图全屏骑行导航中]      |
|  前方 200 米请右转...          |
|                               |
|   +-----------------------+   |  <--- 骑行伴游雷达悬浮胶囊
|   | 🟢 老上海生煎馆 · 35米 [去高德] |
|   +-----------------------+   |
|                               |
|                               |
+-------------------------------+
       🎧 耳机语音播报：
   “前方约35米路过特色美食【老上海生煎馆】，评分4.8分”
```

---

## 🚀 快速上手与使用步骤

### 1. 下载与安装 APK
* 进入本项目的 **[Releases]** 或 **[Actions]** 页面。
* 下载最新生成的 `RideRadar-release-apk.zip`，解压得到 `.apk` 文件。
* 发送到安卓手机点击安装即可。

### 2. 权限开启（首次启动）
* **定位权限**：选择“仅在使用中允许”或“始终允许”（推荐“始终允许”以支持后台息屏探测）。
* **悬浮窗权限**：点击开启“显示在其他应用上层”，以便浮在高德地图上方。

### 3. 开始骑行
1. 打开 **骑行伴游雷达**，按需调节探测半径（默认 50 米，支持 10~200 米）。
2. 点击 **【开启骑行伴游雷达】**。
3. 切换到你的 **高德地图** App，开启正常的路线导航。
4. 戴上耳机，开始骑行，沿途美食与景点将自动播报！

---

## 🔑 关于高德开放平台 Key (可选)

* **免配模式**：App 内置了智能模拟路过引擎，即使不填 Key 也能完整体验悬浮窗、语音播报与高德联动。
* **接入真实周边数据**：
  1. 打开 [高德开放平台官网](https://console.amap.com/) 注册并登录。
  2. 进入「应用管理」->「我的应用」->「创建新应用」。
  3. 添加 Key，服务平台选择 **Web服务**。
  4. 将生成的 Key 复制粘贴到 App 主界面的输入框中即可（个人免费额度每天高达数万次，个人骑行完全免费）。

---

## 🛠️ 项目技术栈

- **Language**：Kotlin (1.9.22)
- **Target SDK**：Android 14 (API 34) / Min SDK 26 (Android 8.0+)
- **Architecture**：Android Foreground Service + WindowManager Overlay + BroadcastReceiver
- **Location**：Android LocationManager (GPS + Network 双定位通道)
- **Voice TTS**：Android 原生 `TextToSpeech` (Usage: Navigation Guidance)
- **Network**：OkHttp 4 + Gson (对接高德 Web API `v3/place/around`)
- **CI/CD**：GitHub Actions (`.github/workflows/build-apk.yml`)

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。

# OmniTV 📺⚡

[![GitHub Release](https://img.shields.io/github/v/release/ismael6499/omnitv?color=blue&style=flat-square)](https://github.com/ismael6499/omnitv/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%20%7C%20Google%20TV-green?style=flat-square)](https://android.com/tv/)
[![API](https://img.shields.io/badge/API-24%2B-orange?style=flat-square)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-purple?style=flat-square)](LICENSE)

**OmniTV** is an all-in-one power-tool and accessibility suite engineered specifically for **Android TV** and **Google TV** (Chromecast with Google TV, Google TV Streamer, Xiaomi Mi Box, Nvidia Shield, smart TVs, and more).

Operating seamlessly as an Accessibility Service, OmniTV intercepts remote control buttons non-intrusively, bringing a fast Quick Menu overlay, customizable remote key remapping, eye-care screen filters, OLED burn-in protection, scheduled sleep timers, and on-device live subtitle translation.

---

## ✨ Features

### 🎮 Remote Button Remapper & Multi-Action Engine
- Intercept and remap remote buttons (Mute, YouTube, TV Input, Color keys, etc.).
- Supports **Single Click**, **Double Click**, **Triple Click**, **Long Press**, and **Key Combinations**.
- Assign actions: Toggle Quick Menu, Cycle Brightness, Screen Dimmer, Blue Light Filter, Sleep Timer, Take Screenshot, Mute/Unmute, Open Apps, or Translate Subtitles.

### 🪟 Non-Intrusive Quick Menu Overlay
- Built using `TYPE_ACCESSIBILITY_OVERLAY` (`FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`).
- Opens smoothly on top of active video players (**Netflix, Disney+, YouTube, Prime Video, HBO Max**) **without pausing playback or triggering DRM blackouts**.
- 100% remote-control friendly: D-pad navigation, smooth auto-scrolling, spatial alignment, and progressive hold acceleration.
- Zero brightness flashes: pre-calibrated internal filter views match background dimming from Frame 0.

### 🌙 Eye Care & Screen Filters
- **Software Dimmer**: Dims screen brightness beyond hardware limits (0% to 95% opacity).
- **Blue Light Filter**: Warm amber color overlay (0% to 80% intensity) for late-night strain-free viewing.
- **Grayscale Mode**: Native AOSP GPU Daltonizer toggle (`accessibility_display_daltonizer`).
- **Night Schedule**: Automate blue-light and dimmer activation based on your daily bedtime hours.
- **Cinema Mode (Modo Cine)**: One-click high-contrast viewing profile with auto-reset upon screen wake.

### 🛡️ OLED Screen Saver (Anti-Burn-In Protector)
- Protect OLED and high-brightness panels from static image burn-in.
- Activates automatically after customizable minutes of inactivity (Black Screen or 95% Dimmer).
- **Zero latency dismissal**: Wakes up immediately upon pressing any remote button.

### ⏰ Smart Sleep Timer & Scheduled Power-Off
- **Quick Timers**: Set sleep countdowns (15m, 30m, 45m, 60m, 90m, 120m).
- **Scheduled Bedtime Alarms**: Exact daily CPU-wakeup sleep alarms using `AlarmManager.setAlarmClock`.
- **Pre-Sleep Warning Prompt**: Configurable on-screen countdown with single-key dismissal.
- **Skip Next Alarm**: Temporarily skip tonight's alarm without disabling your daily schedule.
- **"¿Sigues viendo?" (Still Watching)**: Smart periodic inactivity prompt with loop prevention.

### 💡 On-Screen Brightness HUD (OSD)
- Displays current brightness levels cleanly when cycling or stepping.
- Fully customizable: on-screen position anchor, background color/opacity, text color/opacity, font size, padding, offsets, and display duration.

### 🌐 On-Device Subtitle & Screen Translator
- Intercepts and translates on-screen foreign subtitles in real time.
- Powered by Google ML Kit: runs **100% locally on your TV hardware** for zero latency and complete privacy (no cloud subscription or internet needed).

---

## 🚀 Installation & Setup

Because OmniTV interacts with system-level accessibility settings and display parameters silently, it requires the standard `WRITE_SECURE_SETTINGS` permission granted once via ADB.

### 1. Enable Developer Options on your TV
1. On your Android TV / Google TV, go to **Settings (gear icon)** > **System** > **About**.
2. Scroll down to **Android TV OS build** and press the **Center / OK** button 7 times until you see *"You are now a developer!"*.
3. Go back to **Settings** > **System** > **Developer options** and turn on **USB debugging** (or **Wireless debugging**).

### 2. Connect via ADB
From your computer terminal, connect to your TV's local IP address:
```bash
adb connect <YOUR_TV_IP>
```
*(Accept the prompt that appears on your TV screen checking "Always allow from this computer".)*

### 3. Install the APK
Download the latest `OmniTV-vX.X.X.apk` from the [Releases](https://github.com/ismael6499/omnitv/releases) page and run:
```bash
adb install -r OmniTV-v1.0.0.apk
```

### 4. Grant Required Permission & Enable Service
Grant the secure settings permission:
```bash
adb shell pm grant com.example.togglegrayscale android.permission.WRITE_SECURE_SETTINGS
```

Enable the Accessibility Service (or activate it manually in TV Settings > Accessibility):
```bash
adb shell settings put secure accessibility_enabled 1
adb shell settings put secure enabled_accessibility_services com.example.togglegrayscale/.ButtonMappingService
```

You are ready to go! Press your mapped remote key (or open the app) to launch the Quick Menu.

---

## 🛠️ Building from Source

### Prerequisites
- JDK 17 or JDK 21
- Android SDK (API 34)

### Build Commands
Clone the repository:
```bash
git clone https://github.com/ismael6499/omnitv.git
cd omnitv
```

Compile and assemble the debug APK:
```bash
# On Linux / macOS:
./gradlew assembleDebug

# On Windows:
.\gradlew.bat assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🤝 Contributing

Contributions, feature suggestions, and bug reports are welcome! Feel free to open an issue or submit a pull request.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'feat: Add AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See [LICENSE](LICENSE) for more information.

# SafeZone Sentinel • Personal Safety System & Command Dashboard

[![Live Web Admin Portal](https://img.shields.io/badge/Live%20Portal-GitHub%20Pages-rose?style=for-the-badge&logo=github)](https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/)
[![Download Android APK](https://img.shields.io/badge/Download-Android%20APK-emerald?style=for-the-badge&logo=android)](https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/app-debug.apk)

**SafeZone Sentinel** is a production-ready personal safety ecosystem consisting of a **Native Android Safety Application** and an **AI Command Center Web Portal**. It provides automated emergency alert dispatching, live continuous GPS tracking, motion shake detection, direct 112 calling, and automatic multi-part SMS dispatch to trusted contacts with live Google Maps coordinates.

---

## 🔗 Live Working Download & Web Portal Links

- 📲 **Direct Mobile APK Download (`SafeZone-debug.apk`)**:
  👉 **[Download SafeZone APK (Direct Link)](https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/app-debug.apk)**

- 🌐 **Live Web Admin Monitoring Portal**:
  👉 **[https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/](https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/)**

- 💻 **GitHub Code Repository**:
  👉 **[https://github.com/lokesh-comrade/SafeZone-Sentinel](https://github.com/lokesh-comrade/SafeZone-Sentinel)**

---

## 🌟 Key Features

### 📱 Android Application (`com.safezone.sentinel`)
- **Automated SMS Dispatch**: Sends multi-part SMS alerts with direct Google Maps location links (`https://maps.google.com/?q=lat,lng`) to all configured emergency contacts.
- **5-Shake Motion Watchdog**: Background service ([`SentinelService`](class://com.safezone.sentinel.SentinelService)) monitoring accelerometer delta values (>22 m/s²) to trigger SOS automatically on physical threat.
- **Interactive Swipe-to-Trigger SOS Slider**: Custom draggable thumb component on main screen requiring Intentional ≥85% horizontal gesture.
- **Live Continuous Location Tracking**: High-accuracy `FusedLocationProviderClient` updates every 2–4 seconds.
- **Direct Emergency Calling**: Instant dialing to 112 and primary contact via `Intent.ACTION_CALL`.
- **Firebase Firestore Live Telemetry**: Encrypted real-time synchronization of active alerts, battery status, threat level, and GPS coordinates.
- **Custom App Branding**: Redesigned vector launcher icons (`ic_launcher` / `ic_launcher_round`) with primary rose theme.

### 🌐 Admin Command Portal (`web_admin/index.html`)
- **Interactive Labeled Map Radar & Place Resolver**: High-resolution OpenStreetMap & Esri maps rendering complete street names, landmarks, cities, and place names with real-time reverse geocoding address resolution.
- **Live Video Stream Player**: Real-time HUD overlay video monitor with camera stream status.
- **AI Threat Risk Score Engine**: Visual score breakdown (0–100) assessing threat levels (`CRITICAL THREAT` / `HIGH ALERT`) and recommended emergency response protocols.
- **Remote Alert Disarm Tool**: Allows authorized operators to acknowledge and disarm active mobile alerts remotely.
- **Audio Alarm Siren**: Built-in command room siren toggle.

---

## 🚀 Commands to Push Updates to GitHub

Run these commands in PowerShell inside `C:\Users\admin\Desktop\SafeZone-final`:

```powershell
# 1. Add all tracked files (including the APK inside /apk and /web_admin)
git add .

# 2. Commit files
git commit -m "Fix APK download paths for GitHub Pages and repository"

# 3. Rename branch to main
git branch -M main

# 4. Connect remote origin
git remote add origin https://github.com/lokesh-comrade/SafeZone-Sentinel.git

# 5. Push code to GitHub
git push -u origin main
```

### Enable GitHub Pages:
1. Go to **[https://github.com/lokesh-comrade/SafeZone-Sentinel](https://github.com/lokesh-comrade/SafeZone-Sentinel)**.
2. Click **Settings** ➔ **Pages** (in the left menu).
3. Under **Build and deployment**:
   - **Source**: Select `Deploy from a branch`
   - **Branch**: Choose `main` and directory `/ (root)`
   - Click **Save**.
4. Your Web Admin Portal will be published live at:
   👉 **[https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/](https://lokesh-comrade.github.io/SafeZone-Sentinel/web_admin/)**

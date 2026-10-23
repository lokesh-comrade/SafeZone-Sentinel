# SafeZone Sentinel • Personal Safety System & Command Dashboard

**SafeZone Sentinel** is a production-ready personal safety ecosystem consisting of a **Native Android Safety Application** and an **AI Command Center Web Portal**. It provides automated emergency alert dispatching, live continuous GPS tracking, motion shake detection, direct 112 calling, and automatic multi-part SMS dispatch to trusted contacts with live Google Maps coordinates.

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
- **Interactive Labeled Map Radar**: High-resolution CartoDB & Esri maps rendering complete street names, landmarks, cities, and place names.
- **Live Video Stream Player**: Real-time HUD overlay video monitor with camera stream status.
- **AI Threat Risk Score Engine**: Visual score breakdown (0–100) assessing threat levels (`CRITICAL THREAT` / `HIGH ALERT`) and recommended emergency response protocols.
- **Remote Alert Disarm Tool**: Allows authorized operators to acknowledge and disarm active mobile alerts remotely.
- **Audio Alarm Siren**: Built-in command room siren toggle.

---

## 📦 Mobile APK Download & Installation

The debug APK is built and ready at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installation Steps on Mobile Device:
1. Copy `app-debug.apk` to your Android phone (via USB, WhatsApp, or Google Drive).
2. Tap `app-debug.apk` on your device to install (allow "Install from Unknown Sources" if prompted).
3. Open **SafeZone**, complete the 3-step onboarding, and grant Location, SMS, Call, and Camera permissions.

---

## 🚀 GitHub & GitHub Pages Deployment Guide

### 1. Initialize Git Repository & Commit Code
Open PowerShell or Git Bash in the project root directory (`SafeZone-final`):
```bash
git init
git add .
git commit -m "Initial commit: Complete SafeZone Sentinel Android App & Web Admin Portal"
```

### 2. Push to GitHub
1. Go to [github.com/new](https://github.com/new) and create a new repository named `SafeZone-Sentinel`.
2. Connect and push your code:
```bash
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/SafeZone-Sentinel.git
git branch -M main
git push -u origin main
```

### 3. Enable Live Web Portal on GitHub Pages
1. Go to your repository on GitHub: `https://github.com/<YOUR_GITHUB_USERNAME>/SafeZone-Sentinel`.
2. Click **Settings** → **Pages** (in the left menu).
3. Under **Build and deployment**:
   - **Source**: Select `Deploy from a branch`.
   - **Branch**: Choose `main` and directory `/ (root)` or `/web_admin`.
   - Click **Save**.
4. Your Admin Portal will be published live at:
   `https://<YOUR_GITHUB_USERNAME>.github.io/SafeZone-Sentinel/web_admin/`

---

## ⚙️ Tech Stack & Architecture

- **Language & Framework**: Java 8, Android SDK (API 26 to 36), Material Components 3
- **Location & Sensors**: Google Play Services Location (`21.2.0`), Accelerometer (`SensorEventListener`)
- **Backend & Cloud Database**: Firebase Cloud Firestore (BOM `32.8.1`), Firebase Google Services Plugin (`4.4.1`)
- **Web Portal**: HTML5, Tailwind CSS, Leaflet JS, CartoDB Voyager Map Tiles, Firebase Web JS SDK v10

# Mobile Memory Monitor 📱⚡

**Mobile Memory Monitor** is a production-grade, privacy-focused Android System & Memory Monitoring application built with 100% real device data using Jetpack Compose, Material 3, Coroutines, StateFlow, Room Database, and official Android System APIs.

---

## ✨ Features Overview

### 1. 🧠 Real-Time RAM & Memory Monitoring
* **Authoritative Source**: Reads system RAM metrics directly from `ActivityManager.MemoryInfo` (`totalMem`, `availMem`, `usedMem = totalMem - availMem`, `usedPercent`, `threshold`).
* **Live RAM Usage Graph**: Smooth Bezier line graph sampled every 3 seconds while active, maintaining a 60-sample rolling in-memory window (~3 minutes).
* **Lifecycle-Aware Polling**: Automatically starts polling when the screen enters `ON_RESUME` and cancels polling when `ON_PAUSE` / `ON_STOP` to conserve battery.
* **RAM Optimization**: Garbage collection & background memory boost action.

### 2. ⚡ CPU & Multi-Core Telemetry
* **System CPU Usage (`CpuSource.SYSTEM`)**: Reads `/proc/stat` counter deltas to calculate true system-wide CPU utilization and per-core breakdown on supported devices.
* **Process CPU Fallback (`CpuSource.APP`)**: On non-root Android 8.0+ devices where SELinux restricts `/proc/stat`, measures real process CPU time consumed by the app using `Process.getElapsedCpuTime()` and `SystemClock.elapsedRealtime()`. Clearly labeled as *"App CPU Usage"*.
* **Live CPU Graph & Arc Gauge**: Real-time line chart sampled every 1.5s.
* **Multi-Core Breakdown**: Dynamically detects core count (`Runtime.getRuntime().availableProcessors()`) and renders per-core load indicators.
* **Genuine CPU Thermal Query**: Queries Linux thermal zones (`/sys/class/thermal`) or `HardwarePropertiesManager`. Displays *"CPU temp unavailable"* if restricted, never confusing battery temperature with CPU temperature.

### 3. 🔋 Battery Telemetry
* **Live Battery Metrics**: Reads sticky intent `ACTION_BATTERY_CHANGED` and `BatteryManager`.
* **Telemetry Details**: Real battery level %, status (`Charging`, `Discharging`, `Full`), charging source (`AC`, `USB`, `Wireless`), battery temperature (°C), voltage (`V`), current (`mA`), and health status.

### 4. 💾 Storage Analytics & Instant StatFs
* **Instant Gauge**: Derived directly from `StatFs` on `Environment.getDataDirectory()` in less than 1 millisecond.
* **Live Storage Usage Graph**: Instant 0ms rendering on launch with a rolling 60-sample window updated every 2.5s.
* **Category Breakdown**: Categorizes disk usage across Apps, Photos, Videos, Audio, Documents, and System OS.

### 5. 🧹 Smart Cleanup
* **Real File Scanning**: Scans accessible Cache, Residual, Temp files, Empty Folders, and Old APKs on `Dispatchers.IO`.
* **Honest Permission Statuses**:
  * **Cache Files**: Displays local cache bytes or indicates *"Other app cache requires Usage Access permission"* if restricted (`ScanStatus.RESTRICTED`), never showing fake 0.0 MB numbers.
  * **Empty Folders**: Separates folder count (*"2 empty directories detected"*) from byte size (*"0 B payload"*), excluding empty folders from reclaimable byte totals.
* **Dynamic Recalculation**: Sums selected categories in real time as checkboxes are toggled.
* **Actual File Deletion**: Deletes files on disk via `File.delete()`, tracks actual freed bytes, and executes an immediate fresh rescan from the filesystem.

### 6. 📦 App Manager
* **Package Discovery**: Enumerates user and system applications using `PackageManager`.
* **Storage Breakdown**: Queries exact code (`appBytes`), data (`dataBytes`), and cache (`cacheBytes`) sizes via `StorageStatsManager` (API 26+).
* **Flexible Sorting**: 6 sort options including *Largest Size*, *Largest Cache*, *Smallest Size*, *Name (A to Z)*, *Name (Z to A)*, *Recently Installed*, and *Recently Used*.
* **Usage Access Permission**: Integrated permission flow for `PACKAGE_USAGE_STATS`.

### 7. 📁 Large File Manager
* **MediaStore Scanner**: Queries `MediaStore` (Videos, Images, Audio) and public directories (`Downloads`, `Documents`, etc.) for files exceeding threshold sizes.
* **Bulk Operations**: Multi-selection filter by category and bulk deletion with instant rescan.

### 8. ⚙️ Settings & Telemetry History
* **Room Database**: Persists long-term snapshot logs (`MemoryRecord`) every 5 minutes in Room database table `memory_history`.
* **Preferences**: Dark Mode switch, RAM Usage Alert threshold slider (70%–95%), Notification permissions (`POST_NOTIFICATIONS`), and Clear Memory History confirmation dialog.

### 9. 🛡️ Security & Privacy
* **Screen Protection**: Enforces `FLAG_SECURE` to prevent unauthorized screenshots on sensitive telemetry.
* **Root Detection**: Integrated root check and user notification dialog.
* **Session Manager**: User activity and session management.

---

## 🏗️ Architecture & Technology Stack

* **UI Framework**: Jetpack Compose with Material 3 Design System
* **Architecture Pattern**: MVVM (Model-View-ViewModel) + Repository Pattern
* **Concurrency**: Kotlin Coroutines & `StateFlow` / `SharedFlow`
* **Local Database**: Room DB (`AppDatabase`, `MemoryDao`, `MemoryRecord`)
* **System APIs**:
  * `ActivityManager.MemoryInfo`
  * `StatFs`
  * `BatteryManager`
  * `PackageManager` & `StorageStatsManager`
  * `UsageStatsManager`
  * `MediaStore` & `ContentResolver`
  * `Choreographer` (FPS Monitor)
* **Testing**: JUnit 4 & Kotlin Unit Tests

---

## 🛠️ Build & Run Instructions

### Prerequisites
* **Android Studio**: Jellyfish (2023.3.1) or newer
* **JDK**: JDK 17
* **Android SDK**: `minSdk 26` (Android 8.0) | `targetSdk 34` (Android 14) / `35`

### 1. Build Debug APK
```bash
./gradlew :app:assembleDebug
```

### 2. Run Unit Tests
```bash
./gradlew :app:testDebugUnitTest
```

---

## 📂 Project Structure

```
com.example/
├── data/
│   ├── db/                 # Room Database, DAO, and MemoryRecord entity
│   ├── model/              # Domain models (AppInfo, CpuSnapshot, BatterySnapshot, etc.)
│   └── repository/         # MemoryRepository & System APIs
├── ui/
│   ├── components/         # Reusable M3 composables (Progress rings, cards, buttons)
│   ├── screens/            # Jetpack Compose Screens (Dashboard, Storage, Cleanup, Apps, Settings, LargeFiles)
│   ├── theme/              # Color palette, Material 3 Typography & Theme
│   └── viewmodel/          # MemoryViewModel driving StateFlow telemetry
├── util/                   # MemoryFormatter, NotificationHelper, RootDetection, ScreenProtection
└── MainActivity.kt          # Main Activity & Navigation Scaffold
```

---

## 🔒 Security & Privacy Commitments

* **100% Real Data**: Zero fake values, zero simulated temperatures, and zero mock data.
* **Honest Error & Permission Reporting**: If a system metric is restricted by OS permissions or SELinux policies, the app explicitly displays *"Unavailable"* or *"Restricted"* instead of fabricating values.
* **Scoped Storage Compliant**: Respects Android Scoped Storage and SAF security models.

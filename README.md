# Mobile Memory Monitor

**An Android System Resource and Memory Monitoring Application**

## 1. Overview

Mobile Memory Monitor is an Android application designed to provide a unified interface for monitoring device resources, including RAM, CPU activity, battery status, internal storage, installed applications, and storage usage.

The application is developed using Kotlin, Java, Jetpack Compose, Material 3, Kotlin Coroutines, StateFlow, and Room Database. It integrates Android system APIs to retrieve device information, display resource utilization, maintain historical records, and provide storage management features.

The application follows the Model-View-ViewModel (MVVM) architecture and Repository Pattern to separate the user interface, application logic, and data-access operations.

A key design principle is **data accuracy and transparent permission handling**. When a system metric cannot be accessed because of Android restrictions, device limitations, or insufficient permissions, the application is designed to indicate that the information is unavailable or restricted rather than presenting an unsupported value as verified data.

## 2. Objectives

The primary objectives of Mobile Memory Monitor are to:

- Monitor RAM utilization and present system memory statistics in real time.
- Measure CPU activity and display per-core information where supported.
- Retrieve battery status and available battery telemetry.
- Analyze internal storage capacity and categorize accessible files.
- Identify eligible cleanup candidates and perform supported file-deletion operations.
- Display installed applications and their available storage statistics.
- Locate large files and support category-based file management.
- Maintain historical resource measurements using a local database.
- Provide configurable RAM usage alerts and notification controls.
- Respect Android security, privacy, permission, and storage restrictions.

## 3. Features and Functional Modules

### 3.1 RAM and Memory Monitoring

The RAM monitoring module retrieves system memory information through Android's `ActivityManager.MemoryInfo` API.

The application uses the following values:

- `totalMem`: Total physical RAM reported by Android.
- `availMem`: Memory currently available to the system.
- `usedMem`: Calculated as total memory minus available memory.
- `usedPercent`: Calculated percentage of memory utilization.
- `threshold`: Android's low-memory threshold.

The dashboard presents memory statistics through numerical indicators and a live usage graph. The graph maintains a rolling window of up to 60 samples, collected approximately every three seconds while monitoring is active.

Lifecycle-aware polling is intended to start when the relevant screen becomes active and stop when it is no longer active, reducing unnecessary background work.

The application also provides a RAM optimization action involving garbage collection and background memory-management logic. The effectiveness of such an action depends on Android's memory-management policies and should not be interpreted as a guarantee of increased available RAM.

### 3.2 CPU Monitoring and Multi-Core Telemetry

The CPU monitoring module supports two measurement approaches.

**System-wide CPU monitoring**

Where accessible, the application reads CPU counters from `/proc/stat`. CPU utilization is calculated from changes in CPU counters between consecutive readings. The implementation can also determine per-core utilization when the required counters are available.

**Application-process CPU monitoring**

On devices where system-wide CPU information is restricted, the application can estimate its own CPU utilization using `Process.getElapsedCpuTime()` and `SystemClock.elapsedRealtime()`.

This measurement represents CPU time consumed by the application's process, not total device CPU utilization. The interface identifies this mode as application CPU usage.

The module also provides:

- A live CPU usage graph.
- An arc-style CPU utilization gauge.
- Dynamic processor-core detection using `Runtime.getRuntime().availableProcessors()`.
- CPU temperature retrieval attempts through supported thermal interfaces.

CPU temperature may be unavailable because access to thermal zones or hardware telemetry is restricted by the operating system or device manufacturer. In such cases, the application reports the limitation rather than substituting battery temperature for CPU temperature.

### 3.3 Battery Monitoring

The battery monitoring module retrieves battery information through Android's battery-status broadcast and `BatteryManager` APIs.

Depending on the values exposed by the device, the module can display:

- Battery percentage.
- Charging, discharging, and full status.
- Charging source, including AC, USB, or wireless charging where available.
- Battery temperature.
- Battery voltage.
- Battery current, where supported.
- Battery health status.

The availability and accuracy of individual measurements depend on the device hardware and Android implementation.

### 3.4 Storage Analytics

The storage analytics module uses Android's `StatFs` API with `Environment.getDataDirectory()` to retrieve internal-storage statistics.

The application calculates:

- Total internal-storage capacity.
- Available storage.
- Used storage.
- Percentage of storage used.

The storage dashboard presents these values using visual indicators and a live usage graph. The graph maintains a rolling window of up to 60 samples, with updates approximately every 2.5 seconds while active.

The application also categorizes accessible storage information into groups such as:

- Applications.
- Photos.
- Videos.
- Audio.
- Documents.
- System and other storage.

Category totals depend on the data sources and filesystem information available to the application. They should not be interpreted as a complete inventory of every protected filesystem location.

### 3.5 Smart Cleanup

The Smart Cleanup module identifies eligible cleanup candidates within locations the application is permitted to access.

Supported categories include:

- Accessible cache files.
- Residual files.
- Temporary files.
- Empty directories.
- Older APK files.

Scanning operations are performed on an I/O dispatcher to avoid blocking the main user interface thread.

The module provides category selection and dynamically recalculates the selected cleanup total when the user changes selections.

**File deletion and reporting**

For files that the application is authorized to modify, the cleanup process uses filesystem deletion operations, including `File.delete()`. It can track the size of successfully deleted files and perform a fresh scan to update the results.

Empty directories are counted separately from reclaimable file bytes because an empty directory may contain no file payload to reclaim.

**Permission-aware cache reporting**

Android restricts access to other applications' cache information and private storage. When the application cannot retrieve the required information, the module should report a restricted or unavailable status rather than treating the missing data as zero.

Directly clearing another application's private cache is generally not available to ordinary third-party applications. Where required, the application directs the user to the relevant Android settings screen.

### 3.6 Installed Application Manager

The application manager enumerates installed packages through Android's `PackageManager` API.

For applications whose information is accessible, it presents:

- Application name.
- Package identifier.
- Application category or system-app status.
- Application code size.
- Application data size.
- Cache size.
- Combined storage size.

The application uses `StorageStatsManager`, available from Android 8.0 (API level 26), to query package storage statistics where supported and permitted.

Access to usage statistics is handled through `UsageStatsManager` and the appropriate Usage Access permission workflow.

The application manager supports search, filtering, and sorting options, including:

- Largest total size.
- Largest cache.
- Smallest total size.
- Name in ascending order.
- Name in descending order.
- Recently installed.
- Recently used.

The final two options are available only when the underlying installation or usage information can be retrieved.

### 3.7 Large File Manager

The Large File Manager identifies large files within accessible storage locations.

It uses Android's `MediaStore` APIs to query supported media categories, including images, videos, and audio. It can also examine accessible public directories, such as Downloads and Documents, subject to Android's storage-access rules.

The module provides:

- File-size threshold filtering.
- Category-based filtering.
- Multi-file selection.
- Bulk deletion for files the application is authorized to delete.
- Rescanning after deletion to refresh the displayed results.

Access to user-selected directories can be supported through Android's Storage Access Framework and document-tree permissions.

### 3.8 Settings and Telemetry History

The Settings module provides controls for application preferences and monitoring behavior.

Available settings include:

- Dark mode.
- RAM usage alert enablement.
- RAM threshold configuration, with a stated range of 70% to 95%.
- Notification permission handling.
- Memory-history clearing with confirmation.

The application uses Room Database for local persistence of resource measurements. The `MemoryRecord` entity maps to the `memory_history` table, which stores timestamped RAM and storage measurements.

The stated history-recording interval is approximately five minutes. Historical records can be retrieved through the corresponding data-access object (DAO).

RAM-threshold events are stored separately in the `ram_trigger_events` table.

### 3.9 RAM Monitoring Service and Notifications

A monitoring service supports periodic RAM sampling and threshold evaluation while monitoring is enabled.

The service is designed to:

- Poll system memory information.
- Evaluate configured RAM usage thresholds.
- Record relevant threshold events.
- Update a foreground notification.
- Communicate monitoring status to the user.

Foreground-service execution and notification behavior are subject to Android's background-execution restrictions, service declarations, notification permissions, and platform-version requirements.

### 3.10 Security and Privacy

The application includes security-related components for screen protection, root detection, and session management.

**Screen protection**

The application includes a screen-protection utility based on Android's `WindowManager.LayoutParams.FLAG_SECURE`. When enabled, this flag requests restrictions on screenshots and screen capture for the protected window.

**Root detection**

The application includes root-detection checks that examine common indicators of rooted or development-oriented Android environments. These checks are heuristic and cannot conclusively establish whether a device is rooted or whether privileged access is available.

**Session management**

The session manager maintains user-activity timestamps and session-related state using application preferences. Session enforcement depends on the actual timeout and locking logic implemented by the application.

**Privacy principles**

- Retrieve device information through supported Android APIs.
- Respect application sandbox boundaries.
- Request permissions only where required by implemented functionality.
- Report restricted or unavailable information transparently.
- Avoid presenting estimates or missing values as verified measurements.
- Use Android's supported user-controlled workflows for restricted operations.

## 4. Architecture and Technology Stack

The application follows an MVVM architecture combined with the Repository Pattern.

### 4.1 Architecture Layers

**Presentation layer**

Implemented using Jetpack Compose and Material 3. It contains the dashboard, storage, cleanup, application manager, settings, and large-file screens.

**ViewModel layer**

`MemoryViewModel` coordinates UI state, asynchronous operations, and repository calls. Kotlin `StateFlow` and related observable streams expose data to the presentation layer.

**Data layer**

The repository layer retrieves system information through Android APIs, processes resource measurements, and provides application data to the ViewModel.

**Persistence layer**

Room Database stores historical memory and storage records and RAM-threshold events in a local SQLite database.

**Utility and service layer**

Utility classes provide formatting, notifications, root detection, screen protection, and session management. The RAM monitoring service supports ongoing monitoring where permitted by Android.

### 4.2 Technology Stack

| Category | Technologies |
|---|---|
| Primary languages | Kotlin and Java |
| UI framework | Jetpack Compose |
| Design system | Material 3 |
| Architecture | MVVM and Repository Pattern |
| Asynchronous processing | Kotlin Coroutines |
| Observable state | StateFlow and SharedFlow |
| Local persistence | Room Database and SQLite |
| Memory monitoring | `ActivityManager.MemoryInfo` |
| CPU monitoring | `/proc/stat`, `Process`, `SystemClock` |
| Battery monitoring | `BatteryManager`, `ACTION_BATTERY_CHANGED` |
| Storage monitoring | `StatFs`, `Environment` |
| Application discovery | `PackageManager` |
| Package storage statistics | `StorageStatsManager` |
| Usage statistics | `UsageStatsManager` |
| Media and file access | `MediaStore`, `ContentResolver`, Android Storage Access Framework |
| Rendering metrics | `Choreographer` |
| Testing | JUnit 4 and Kotlin unit tests |
| Build system | Gradle |

Additional libraries are included in the project dependencies. Their use in specific runtime features should be determined from the corresponding source files and dependency configuration.

## 5. Project Structure

The following is the logical organization of the application source code:

```text
com.example.mobilememorymonitor/
├── data/
│   ├── db/
│   │   ├── AppDatabase
│   │   ├── MemoryDao
│   │   └── MemoryRecord and related entities
│   ├── model/
│   │   ├── AppInfo
│   │   ├── CpuSnapshot
│   │   ├── BatterySnapshot
│   │   └── Other resource models
│   └── repository/
│       └── MemoryRepository
├── ui/
│   ├── components/
│   ├── screens/
│   ├── theme/
│   └── viewmodel/
│       └── MemoryViewModel
├── util/
│   ├── MemoryFormatter
│   ├── NotificationHelper
│   ├── RootDetection
│   └── ScreenProtection
└── MainActivity.kt
```

The structure above illustrates the logical organization. Exact directory names and filenames should correspond to the checked-out repository.

## 6. Prerequisites

The following development environment is specified for building the project:

- **Android Studio:** Jellyfish (2023.3.1) or a compatible newer version.
- **JDK:** JDK 17.
- **Android SDK:** The SDK platform and build tools required by the Gradle configuration.
- **Android device or emulator:** A compatible environment for installation and testing.
- **Gradle:** The version supported by the project's Gradle wrapper.

The project description specifies a minimum SDK of API 26 (Android 8.0) and a target SDK of API 34 or 35. The actual `minSdk` and `targetSdk` values configured in the repository's Gradle files are authoritative if they differ from these stated requirements.

## 7. Build and Run Instructions

### 7.1 Clone the Repository

```bash
git clone https://github.com/aryankumarjha2006-oss/Mobile-Memory-Monitor.git
cd Mobile-Memory-Monitor
```

Open the project in Android Studio and allow Gradle synchronization to complete.

### 7.2 Build the Debug APK

On Windows, use the Gradle wrapper:

```powershell
.\gradlew.bat :app:assembleDebug
```

On Linux or macOS:

```bash
./gradlew :app:assembleDebug
```

If the build succeeds, the debug APK is normally generated under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 7.3 Run Unit Tests

On Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

On Linux or macOS:

```bash
./gradlew :app:testDebugUnitTest
```

The test task executes the unit tests configured for the debug variant.

### 7.4 Install and Execute

1. Connect a compatible Android device with USB debugging enabled, or start an Android emulator.
2. Open the project in Android Studio.
3. Allow Gradle synchronization to finish.
4. Select the application run configuration.
5. Build and run the application.
6. Grant requested permissions only when needed for the corresponding features.
7. Verify resource readings and permission-dependent behavior on the selected device.

## 8. Permissions and Android Platform Limitations

Some features depend on Android permissions, API levels, device hardware, and manufacturer-specific restrictions.

| Feature | Relevant dependency or limitation |
|---|---|
| RAM monitoring | `ActivityManager.MemoryInfo` exposes system memory information. |
| System-wide CPU usage | Access to `/proc/stat` may be restricted; application-process CPU usage is a different metric. |
| CPU temperature | Thermal interfaces may be restricted or unavailable. |
| Battery metrics | The availability of individual telemetry values varies by device. |
| Per-app storage statistics | Requires supported APIs and any applicable access permissions. |
| Usage information | Requires the appropriate Usage Access grant. |
| Media and large-file scanning | Restricted by Android storage permissions and scoped-storage rules. |
| File deletion | Only files the application is authorized to modify can be deleted directly. |
| Other applications' private cache | Cannot generally be cleared directly by an ordinary third-party application. |
| Notifications | Notification permission and foreground-service requirements depend on Android version and implementation. |
| Root detection | Heuristic indicators can produce false positives or false negatives. |

The application should distinguish system-wide readings, application-process measurements, estimates, and unavailable information so users can interpret the results correctly.

## 9. Testing

The project includes JUnit 4 and Kotlin unit-testing support.

Testing should cover:

- RAM calculations and percentage boundaries.
- CPU utilization calculations from successive counter samples.
- Battery-value conversion and unavailable fields.
- Storage calculations from filesystem statistics.
- Cleanup selection totals and deletion results.
- App filtering, sorting, and selection behavior.
- Memory-history insertion, retrieval, and clearing.
- RAM threshold comparisons and event recording.
- Permission-denied and restricted-data scenarios.
- Lifecycle handling of monitoring operations.
- UI behavior when readings are unavailable.

Actual test coverage and successful execution should be established from the repository's test source files and test results.

## 10. Data Accuracy and Reporting

Mobile Memory Monitor is designed to use actual device information wherever supported Android APIs or accessible system interfaces provide it.

However, different measurements have different meanings:

- **System RAM utilization** describes device-level memory availability and usage.
- **Application-process CPU utilization** describes the CPU time consumed by the application's process and is not interchangeable with system-wide CPU utilization.
- **App cache size** describes storage associated with an application and does not represent RAM usage.
- **Storage category totals** depend on accessible data sources and may not account for all protected filesystem locations.
- **Unavailable telemetry** indicates that the application could not obtain a value under the current device, permission, or platform conditions.

Any calculated metric should be interpreted according to its data source and measurement method. The application does not gain unrestricted system access merely by using Android framework APIs.

## 11. Security and Privacy Considerations

The application is designed around Android's application sandbox and permission model.

Its security considerations include:

- Respecting Android's access restrictions for other applications' data.
- Using user-controlled settings screens for operations that cannot be performed directly.
- Handling permission denial and unavailable system metrics.
- Applying screen-capture protection when the relevant utility is enabled.
- Maintaining session-related state through the session-management component.
- Avoiding claims of privileged access when the application is running without root privileges.

Root detection is an indicator-based check, not a substitute for a comprehensive security assessment. Likewise, `FLAG_SECURE` restricts supported capture mechanisms but does not guarantee that all possible forms of information capture are prevented.

## 12. Future Scope

Potential areas for future development include:

- Broader compatibility testing across Android versions and device manufacturers.
- Improved diagnostics for restricted CPU, thermal, and per-app storage information.
- Exportable historical resource reports.
- Expanded automated tests for resource calculations and permission handling.
- Additional accessibility and usability improvements.
- More detailed trend visualization using historical Room records.
- Further validation of monitoring performance and battery consumption during extended sessions.

These items represent potential extensions rather than claims about functionality already implemented.

## 13. Conclusion

Mobile Memory Monitor brings RAM, CPU, battery, storage, installed-application, cleanup, and historical monitoring features into a unified Android application.

By combining Kotlin and Java, Jetpack Compose, Material 3, Kotlin Coroutines, StateFlow, Room Database, and Android system APIs, the application demonstrates an integrated approach to resource monitoring and device-storage management.

Its permission-aware design recognizes the boundaries imposed by Android's sandbox, scoped storage, and telemetry access policies. By distinguishing accessible measurements from restricted information and guiding users through supported system workflows, the application aims to provide a useful and transparent resource-monitoring experience.

## 14. References

1. Android Developers. [Manage your app's memory](https://developer.android.com/topic/performance/memory/manage-app-memory).
2. Android Developers. [ActivityManager.MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo).
3. Android Developers. [PackageManager](https://developer.android.com/reference/android/content/pm/PackageManager).
4. Android Developers. [StorageStatsManager](https://developer.android.com/reference/android/app/usage/StorageStatsManager).
5. Android Developers. [Room persistence library](https://developer.android.com/training/data-storage/room).
6. Android Developers. [Jetpack Compose](https://developer.android.com/compose).

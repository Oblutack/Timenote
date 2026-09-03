<div align="center">
  <h1>Timenote</h1>
  <p><b>The ultimate local-first productivity and time-tracking ecosystem.</b></p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/iOS-000000?style=for-the-badge&logo=ios&logoColor=white" alt="iOS" />
    <img src="https://img.shields.io/badge/Compose_Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform" />
  </p>

  <p>
    <i>Built for precision execution, extensive tracking, and deep reflection. Engineered entirely with Kotlin Multiplatform and Compose Multiplatform, Timenote delivers an uncompromising, hardware-accelerated experience inside a unified, dark-theme-exclusive architecture.</i>
  </p>
  <p>
  <a href="https://play.google.com/store/apps/details?id=com.oblutack.timenote">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60"/>
  </a>
</p>

<br>

<img src="https://github.com/user-attachments/assets/b6a40089-9b73-425e-942a-9e3fcc25327b" alt="Timenote App Banner" width="100%" />
  
</div>
<img src="https://github.com/user-attachments/assets/47ca0038-b7b0-4c31-a608-f2f0688a805d" width="100%" alt="Timenote Showcase" />

  <br><br>
---

## Overview

Timenote is designed for focused work environments requiring rigorous time tracking and local-first data ownership. For the 2026 software landscape, it prioritizes a fully minimalist, tactile user interface with absolutely zero cloud dependency. Data is yours, processed locally, and visualized dynamically.

---

## Core Architecture

Timenote champions a clean architecture standard through strict MVVM methodologies. UI state relies inherently on StateFlow subscriptions hoisted properly up the ViewModels, avoiding lifecycle degradation across platform back-stack architectures. All SQL routines are mapped logically via DAOs, feeding asynchronous flows directly to the application layer.

> **Performance First:** Hardware-accelerated rendering and background timekeeping ensure maximum performance without battery drain or data drift.

---

## Features

- **Spatial Workflow Mapping**
  A dynamic, pannable node-graph built in pure Canvas that visualizes the hierarchical relationship between parent and child timer sessions. 
- **Resilient Timekeeping**
  A mathematically bulletproof background engine utilizing Android Foreground Services, absolute time calculations, and native OS Chronometers to prevent battery-throttling drift.
- **Rich Media Environment**
  A dedicated full-screen Markdown text editor with interactive inline checklists, paired with native KMP Voice Memos featuring OS-level audio ducking.
- **Advanced Analytics**
  A GitHub-style Flow-State Heatmap that tracks deep-work streaks and calculates total clustered time across branching workflows.
- **Tactile UX**
  Hardware-accelerated dynamic background blurring, morphing component animations, and a precision Haptic Feedback engine set a new standard for local-first applications.

---

## Tech Stack

The entire ecosystem exists in `commonMain`, with absolute minimal platform-specific code.

| Technology | Description |
| :--- | :--- |
| **Kotlin Multiplatform (KMP)** | Core business logic and shared data layer |
| **Compose Multiplatform** | 100% shared declarative UI |
| **KMP Room** | Native SQLite data persistence |
| **KMP DataStore** | Multiplatform preferences architecture |
| **kotlinx-datetime** | Standardized multiplatform chronometrics |
| **kotlinx-coroutines** | Asynchronous execution framework |

---

## Build Instructions

### Prerequisites
- **Android Studio** (Latest version recommended) or IntelliJ IDEA with KMP plugins.
- **Xcode** (If building for iOS).

### Android
1. Open the project inside Android Studio.
2. Select the `composeApp` run configuration.
3. Build and run onto your Android emulator or physical device.

*Alternatively, from the root terminal:*
- macOS/Linux: `./gradlew :composeApp:assembleDebug`
- Windows: `.\gradlew.bat :composeApp:assembleDebug`

### iOS
1. Open the project inside Android Studio and allow Gradle sync.
2. Run the `iosApp` run configuration targeted to an active iOS Simulator.

*Or to build natively via Xcode:*
1. Pre-build the shared Compose frameworks via Gradle.
2. Open the `/iosApp` directory natively into Xcode and execute the default configuration.

---

<div align="center">
  <i>Timenote - Built with Kotlin Multiplatform.</i>
</div>

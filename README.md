# Timenote

**The ultimate local-first productivity and time-tracking ecosystem.**

Built for precision execution, extensive tracking, and deep reflection. Engineered entirely with Kotlin Multiplatform and Compose Multiplatform, Timenote delivers an uncompromising, hardware-accelerated experience inside a unified, dark-theme-exclusive architecture.

---

## Overview

Timenote is designed for focused work environments requiring rigorous time tracking and local-first data ownership. For the 2026 software landscape, it prioritizes a fully minimalist, tactile user interface with absolutely zero cloud dependency. Data is yours, processed locally, and visualized dynamically.

---

## Core Architecture

Timenote champions a clean architecture standard through strict MVVM methodologies. UI state relies inherently on StateFlow subscriptions hoisted properly up the ViewModels, avoiding lifecycle degradation across platform back-stack architectures. All SQL routines are mapped logically via DAOs, feeding asynchronous flows directly to the application layer.

Hardware-accelerated rendering and background timekeeping ensure maximum performance without battery drain or data drift.

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

* **Kotlin Multiplatform (KMP)**
* **Compose Multiplatform**
* **KMP Room (Native SQLite)**
* **KMP DataStore (Preferences)**
* **kotlinx-datetime & kotlinx-coroutines**

---

## Build Instructions

### Prerequisites
- Android Studio (Latest version recommended) or IntelliJ IDEA with KMP plugins.
- Xcode (If building for iOS).

### Android
1. Open the project inside Android Studio.
2. Select the `composeApp` run configuration.
3. Build and run onto your Android emulator or physical device.

Alternatively, from the root terminal:
`./gradlew :composeApp:assembleDebug` (macOS/Linux)
`.\gradlew.bat :composeApp:assembleDebug` (Windows)

### iOS
1. Open the project inside Android Studio and allow Gradle sync.
2. Run the `iosApp` run configuration targeted to an active iOS Simulator.

**OR**
1. Pre-build the shared Compose frameworks via Gradle.
2. Open the `/iosApp` directory natively into Xcode and execute the default configuration.

---

*Timenote - Built with Kotlin Multiplatform.*

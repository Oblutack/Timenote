<div align="center">
  <h1>Timenote</h1>
  <p><b>The Obsidian of Timers.</b></p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/iOS-000000?style=for-the-badge&logo=ios&logoColor=white" alt="iOS" />
    <img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform" />
  </p>

  <p>
    <i>A premium productivity timer designed for focused execution, extensive tracking, and deep reflection. Built exclusively with Kotlin Multiplatform and Compose Multiplatform, it brings an uncompromising and elegant experience to both Android and iOS inside a single, unified codebase.</i>
  </p>
</div>

---

## Table of Contents
- [Features](#features)
- [Tech Stack Overview](#tech-stack-overview)
- [Architecture & UI/UX](#architecture--uiux-philosophy)
- [How to Build and Run](#how-to-build-and-run)

---

## Features

- **Premium Dark Mode GUI:** A meticulously designed aesthetic utilizing a monochromatic layout. Focus-centric contrast keeps distractions out of your workflow.
- **Live Animated Timeline:** Track your sessions with interactive waypoints that log timeline events (Starts, Pauses, Notes) natively on an animated vertical map.
- **Inline Description Editing:** Elegantly transition any static description into an editing environment with a zero-friction optimistic UI.
- **Custom History Calendar:** A purely native month-and-day matrix visualizing execution history, deeply integrated inside the session tracking lists.
- **Soft-Delete & Trash Bin:** Erased sessions and project folders are securely transferred to a dedicated Trash Bin for permanent deletion or instant restoration.
- **Customizable Accent Palette:** Build your productivity hierarchy. Assign custom hex color tags and filter projects directly within the ecosystem.

---

## ⚙Tech Stack Overview

- **Framework:** Kotlin Multiplatform (KMP)
- **UI Toolkit:** Compose Multiplatform (100% shared UI in `commonMain`)
- **Architecture:** MVVM (Model-View-ViewModel) with unidirectional data flow (StateFlow)
- **Data Persistence:** Room Database for KMP (Bundled SQLite with Soft-Delete functionality)
- **Preferences:** KMP DataStore (For Hex color caching and monochrome toggles)
- **Navigation:** Jetpack Compose Navigation for KMP (NavHost)
- **Date & Time:** `kotlinx-datetime` (Strictly multiplatform, dropping traditional `java.time` completely)

---

## 🏛Architecture & UI/UX Philosophy

### Architecture
Timenote champions a clean architecture standard through strict MVVM methodologies. UI state relies inherently on `StateFlow` subscriptions hoisted properly up the ViewModels, avoiding lifecycle degradation across platform back-stack architectures. All SQL routines are mapped logically via DAOs, feeding asynchronous flows directly to the application layer.

### UI/UX Design Language
- **Monochromatic Base:** Engineered entirely around absolute dark modes (`#121212` backgrounds mapping cleanly to `#1E1E1E` surface components).
- **Ghost Borders:** Embracing high-context structural mappings utilizing ultra-thin `1.dp` colored border highlights wrapped gracefully around core surface targets.
- **Modal Bottom Sheets:** Banishing clunky system alerts to the past, strictly substituting context menus and prompts into heavily customized, physics-driven Material 3 Bottom Sheets.

---

## How to Build and Run

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

<p align="center">
  <i>Timenote - Built with Kotlin Multiplatform.</i>
</p>

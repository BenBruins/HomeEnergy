# Projeceb een znwifi xt9 t Plan

An Android app that fetches data from a Zeversolar inverter local URL (http://192.168.50.173/home.cgi) every 15 minutes, saves the current wattage and today's kWh to a Google Spreadsheet, and displays interactive graphs for 'Now', 'Day', 'Week', 'Month', and 'Year'.

## Project Brief

# Project Brief: Zeversolar Inverter Logger & Visualizer

A specialized Android application designed to monitor Zeversolar solar inverters, providing automated cloud logging and multi-scale visual analytics of energy production.

## Features
*   **Automated Background Fetching**: Schedules precise 15-minute polling of the Zeversolar local endpoint (`home.cgi`) to capture real-time production data without manual intervention.
*   **Cloud Data Logging**: Automatically synchronizes current wattage (W) and daily energy yield (kWh) to a Google Spreadsheet, creating a permanent, accessible record of solar performance.
*   **Interactive Multi-Scale Graphing**: Features dynamic charts that allow users to toggle between 'Now' (real-time), 'Day', 'Week', 'Month', and 'Year' views to identify long-term trends and efficiency patterns.
*   **Live Production Dashboard**: An energetic, Material 3-based interface displaying the current "Now" wattage and total "Today" kWh at a glance with real-time connection status indicators.

## High-Level Technical Stack
*   **Kotlin**: The primary language for a type-safe and modern codebase.
*   **Jetpack Compose**: For building a responsive, energetic Material 3 UI.
*   **WorkManager**: Used to manage the background synchronization service every 15 minutes.
*   **Retrofit & Moshi (KSP)**: For efficient networking and parsing of the inverter's CGI responses.
*   **Google Sheets API**: Serves as the primary cloud persistence layer for logging data points.
*   **Kotlin Coroutines & Flow**: For managing asynchronous data streams between the network, background workers, and the UI.
*   **Vico**: A modern Compose-first graphing library to handle interactive solar data visualization.

## Implementation Steps

### Task_1_SetupNetworkingAndDeps: Add WorkManager and Vico charting library dependencies to libs.versions.toml and app/build.gradle.kts. Define data models for the Zeversolar inverter CGI response (parsing current wattage and today's kWh) and the Google Sheets API. Configure Retrofit interfaces for both services.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - WorkManager and Vico dependencies added to the project
  - Data models for Zeversolar and Google Sheets created
  - Retrofit services for both endpoints defined
  - Project builds successfully
- **StartTime:** 2026-07-22 18:49:22 CEST

### Task_2_BackgroundLoggingService: Implement a WorkManager PeriodicWorkRequest to poll http://192.168.50.173/home.cgi every 15 minutes. Append the retrieved wattage and daily kWh to a Google Spreadsheet using the Google Sheets API. Ensure API Key integration is properly handled.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Background worker fetches data from inverter every 15 minutes
  - Data points are successfully appended to the Google Spreadsheet
  - Google Sheets API integration is functional with API Key

### Task_3_DashboardAndMultiScaleGraphs: Build the Jetpack Compose UI with a vibrant Material 3 theme. Create a 'Now' dashboard showing real-time stats and implement interactive Vico graphs supporting 'Now', 'Day', 'Week', 'Month', and 'Year' production views.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Dashboard displays current inverter production and status
  - Interactive charts successfully toggle between different time scales
  - UI follows Material 3 design and uses energetic colors

### Task_4_FinalRefinementAndVerification: Implement full Edge-to-Edge display and create a custom adaptive app icon. Conduct a final 'Run and Verify' to ensure app stability, check for crashes, and confirm alignment with all Zeversolar monitoring requirements.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Vibrant M3 theme and Edge-to-Edge display active
  - Adaptive app icon implemented
  - Application runs without crashes and passes all requirements
  - Critic_agent verifies stability and UI consistency


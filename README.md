# MindFul Scrolling

**MindFul Scrolling** is a digital wellbeing and app locker application designed to help users break free from endless scrolling habits. By actively monitoring specific applications and gently nudging you with warnings or overlay locks when you exceed your self-imposed time limits, MindFul Scrolling encourages healthier digital habits.

## Features

- **App Monitoring**: Selectively choose which apps you want to monitor (e.g., social media apps that trigger mindless scrolling).
- **Time Limits & Warnings**: Configure custom warning intervals (e.g., every 5, 10, or 15 minutes) to receive non-intrusive notifications or overlays when you've been on a monitored app for too long.
- **Session Cooldowns**: Set a "Refresh Session After" period (e.g., 30 minutes, 1 hour). Minimizing an app for a few seconds won't bypass the warning timer—the app will resume tracking until the cooldown period has fully elapsed.
- **PIN Protection**: Secure your digital wellbeing settings behind a PIN. Once locked, the app settings cannot be altered without the PIN, ensuring you don't bypass your own rules in a moment of weakness.
- **Modern UI**: Features a beautiful, soothing pastel-themed user interface built entirely with Jetpack Compose.
- **Today's Stats**: Track exactly how much time you've spent on each monitored application directly on the dashboard.

## Technologies Used

- **Kotlin**
- **Jetpack Compose** (Material 3)
- **Coroutines & Flows** for asynchronous data handling
- **UsageStatsManager** for monitoring active app durations
- **Foreground Services** to ensure continuous and reliable monitoring
- **SharedPreferences** for lightweight local state persistence

## Requirements & Permissions

To function correctly, MindFul Scrolling requires several key system permissions:
- **Usage Access**: Allows the app to see which applications are currently running in the foreground.
- **Display Over Other Apps (Overlay)**: Required to draw warning screens over monitored applications.
- **Battery Optimization Exemptions**: Ensures the Android system does not kill the monitoring service running in the background.
- **Query All Packages**: Allows the app to retrieve proper names and icons of installed applications for the monitoring dashboard.

## Installation

1. Clone this repository.
2. Open the project in Android Studio.
3. Sync Gradle and run the app on an Android device (API 31+ recommended).
4. Follow the setup wizard to grant the necessary permissions.

## Customization

- **Warning Intervals**: Selectable from 1, 5, 10, 15, or 30 minutes.
- **Session Refresh**: Selectable from 10 minutes up to 12 hours.

---
*Developed to promote healthy digital habits and intentional smartphone usage.*

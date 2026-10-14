# Luma Launcher

A lightweight, minimalist Android home-screen launcher built with native Java views and no third-party runtime libraries.

## Features
- Dark green minimalist UI, clock/date, and app search
- Long-press app icons for Open, App info, Uninstall, and Open in split screen
- Refresh app list and shortcuts to Android Home/Display settings
- Responsive grid and focus highlighting for Android TV / D-pad navigation
- Android TV discovery through LEANBACK_LAUNCHER; touchscreen is optional
- No ads, analytics, account, or network requests
- Minimum Android version: Android 5.0 (API 21); Android 5.1.1 (API 22) is supported

## Platform limitations
- Split-screen launch is a request to Android and requires Android 7.0+ plus device/app support.
- Floating-window/freeform mode is controlled by the manufacturer and Android system; a regular launcher cannot force it on devices that do not support it.
- Uninstall opens Android's system confirmation screen; it does not silently remove apps.
- Android TV vendor launchers may restrict replacing the home screen. Choose Luma from the system's Home app settings if the device allows it.

## Build APK
Every push to `main` triggers the Android workflow. Open **Actions → Android APK → latest successful run → Artifacts → LumaLauncher-debug-apk** to download the debug APK.

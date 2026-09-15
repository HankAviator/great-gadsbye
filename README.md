# Great Gadsbye

An LSPosed/Xposed module that removes sponsored content from Google Android apps.

## Features

- **Gmail:** hides sponsored conversation rows in the Promotions and Social inboxes.
- **Google Maps:** hides sponsored offer cards in place pages.
- **Maps rotation:** prevents upside-down portrait while retaining normal portrait and landscape rotation.
- **Per-app controls:** a Material Design 3 settings app lets each remover be enabled independently.
- **System styling:** the settings app follows Android 12+ wallpaper-based dynamic color.

The module works on views already rendered by the target app. It does not block network requests,
read mail or location data, or modify messages and places.

## Build

Requirements: JDK 17+, Android SDK Platform 35, and Gradle 8.9.

```powershell
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install

1. Install the APK.
2. Enable **Great Gadsbye** in LSPosed.
3. Select the recommended scopes: Gmail and Google Maps.
4. Open Great Gadsbye and choose which removers to enable.
5. Force-stop and reopen the affected Google apps.


```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.google.android.gm
adb shell am force-stop com.google.android.apps.maps
```

Both removers default to enabled. LSPosed's read-only shared-preferences bridge exposes the two
booleans to the hooked processes; no sensitive Android platform permissions are requested.

## Compatibility

The Google Maps matcher was verified against Maps `26.37.05.977222275`, including rendered
`Sponsored, Agoda, …, Book now` cards and unlabeled place promotions ending in `View Offer`.
Google app updates can change their UI. If an ad stops being removed, capture an LSPosed log and a
UI hierarchy while it is visible.

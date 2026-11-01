# TV Key Mapper — Xiaomi Android TV 11 fork

**Unofficial fork of [TV Key Mapper by Kostya / kostyay](https://github.com/kostyay/xiaomi-tv-apps-button-bridge).**
Based on upstream [v2.0.0](https://github.com/kostyay/xiaomi-tv-apps-button-bridge/tree/v2.0.0), commit `8d1dd584670a1f7a1734b7f0b53bda2f49252784`.
Original Git history and MIT copyright are preserved. This fork is maintained by [coliosiko](https://github.com/coliosiko).

[Русский README](README.md) · [Changes](docs/CHANGES.md) · [Build and signing](docs/BUILDING.md) · [Test scope](docs/TESTING.md) · [Original README](docs/UPSTREAM-README.md)

## Android version matters

Tested hardware: **Xiaomi MiTV-MOOQ1, Android TV 11 / API 30**. Upstream targets a different tested setup: MiTV-AFMU0, Android TV 14.

| Setting | Value |
|---|---|
| Tested OS | Android TV 11 / API 30 |
| Minimum installation API | `minSdk 26` (Android 8.0); other devices are not guaranteed tested |
| Target API | `targetSdk 36` (Android 16), not the minimum OS |
| Compilation API | `compileSdk 37`, required on the build computer, not on the TV |
| JDK / Gradle / AGP / Kotlin | 17 / 9.3.1 / 9.1.1 / 2.3.20 |
| ADB library | Kadb 2.1.5 |
| Application ID | `com.kostyay.xiaomiappsbridge.diagnostic` |
| APK version / code | `2.0.2-mitv-diagnostic.2` / 4 |

## Additions

- Android 11-compatible receiver registration and more detailed ADB/RSA diagnostics.
- **Recent apps panel**: labels, icons, D-pad/OK/Back navigation; tasks obtained through local ADB `dumpsys activity recents`, deduplicated in recency order. Mapper and known launchers are excluded. This is recent history, not a guaranteed list of running processes. Selecting an entry launches its package.
- **Sleep timer**: 30/60/90/120 minutes and cancel; persisted elapsed-realtime deadline, AlarmManager scheduling and local ADB `input keyevent 223` at expiry. Timer clears on reboot; process-restart restoration is implemented but not separately device-tested.
- Existing mapping storage, default `KEY_CHAT → Projectivy apps panel`, application launching, custom intents and boot startup remain supported.

## Install

Root is not required. [Download the diagnostic APK](downloads/TV-Key-Mapper-2.0.2-mitv-diagnostic.2.apk) using Download raw file; [SHA-256](downloads/SHA256SUMS.txt). Enable network ADB on TCP port 5555, then install:

```sh
adb connect TV_IP:5555
adb install -r TV-Key-Mapper-2.0.2-mitv-diagnostic.2.apk
```

Open **TV Key Mapper Diagnostic**, authorize the app's own RSA key and wait for **ADB CONNECTED**. PC and app ADB identities are separate.
The app connects to `127.0.0.1:5555`; the PC can disconnect after setup.

In **Key mappings**, capture and assign:

- Xiaomi `KEY_WWW` → **Recent apps panel**
- IVI `KEY_LOGOFF` → **Sleep timer**

On this remote, OKKO is `KEY_COFFEE`, VK Video `KEY_SPREADSHEET`, RuTube `KEY_DATABASE`, KION `KEY_PVR`, Wink `KEY_VCR`.
Key names may differ on other TVs. Existing user mappings are not automatically replaced.

An in-place diagnostic.1 → diagnostic.2 update was tested with mapping JSON preserved. Do not uninstall before updating.
Updating requires the same package **and signing certificate**. The distributed APK uses a local diagnostic debug key; rebuilding on another machine or CI produces a different signature unless that key is deliberately supplied. Keys are not included in this repository.
If upstream is also installed, stop its bridge to avoid duplicate actions.

## Limits

The ADB listener observes input and does not suppress the original vendor action. Conflicts depend on the firmware; the fork does not automatically disable system packages.
Recent tasks may outlive processes. Sleep is standby, not mains power-off. ADB must still be available when the timer fires; an error is shown if it fails and no later automatic retry is made.
Force-stopping the application removes alarms until it is opened again. Closing the UI does not cancel the timer.

Device checks covered APK replacement, retained mappings, rendering, selecting a recent app, timer scheduling and cancellation.
Full timer expiry/sleep, process death recovery, reboot reset, and physical assignments of the two new actions remain unverified. See [TESTING.md](docs/TESTING.md).

## Build

Install JDK 17, Android SDK Platform 37 and Build Tools 36.0.0, then:

```sh
./gradlew ktlintCheck lintDebug testDebugUnitTest assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. See [BUILDING.md](docs/BUILDING.md) for signing and CI.

## Credits and licenses

Original application: **[Kostya / kostyay](https://github.com/kostyay/xiaomi-tv-apps-button-bridge)**, **MIT**; see [LICENSE](LICENSE).
ADB client: **[flyfishxu / Kadb](https://github.com/flyfishxu/Kadb)**, version **2.1.5**, **Apache-2.0**.
Compatible with Projectivy's `tv.projectivy.ALL_APPS` intent. Not affiliated with Xiaomi or Projectivy.
[Android KEYCODE_SLEEP documentation](https://developer.android.com/reference/android/view/KeyEvent#KEYCODE_SLEEP).

# FF BoostX
## v4.1 Touch Macro Recorder & Floating Replay Buttons

FF BoostX now includes an opt-in Android Accessibility macro engine. After the user enables **FF BoostX Macro Engine** in Android Accessibility settings, the floating panel can record and replay saved touch sequences.

- Records taps, long-presses and swipe paths on Android 14+ using the accessibility motion-event pipeline.
- Saves recordings locally on the device; recordings are not uploaded.
- Each recording becomes a named macro in the **Macros** tab.
- Each macro can have its own **floating replay button**, so a player can place the saved action in the in-game control panel.
- Playback uses `AccessibilityService.dispatchGesture()` and requires no root, ADB or Shizuku.
- Macros can be hidden from the floating panel without deleting the recording.
- Android/OEM accessibility behavior can vary. Recording is explicitly opt-in and should only be used where the game/service rules permit automation.

The feature is inspired by the workflow of gaming-space tools such as GG Game Space: record an action, save it, and expose a dedicated floating replay control. It does not claim to modify the game's internal touch driver or kernel.



A gaming utility for Free Fire that runs as an ordinary Android app. No root, no
Magisk, no SuperSU, no ADB, no wireless debugging, no Shizuku, no shell commands,
no `/sys` writes, no governor edits, no game file or APK modification, no memory
or process injection.

The design rule everywhere in this project: **real functionality over boosting
claims**. If Android does not let an app do something, FF BoostX implements the
closest legitimate alternative and says plainly what the limitation is. It never
prints a number it did not measure.

---

## Build

The repository builds with GitHub Actions. Push to `main`, or run the workflow
manually from the Actions tab, and it produces:

| Artifact | Notes |
|---|---|
| `FFBoostX-release-apk` | R8-shrunk release build. Signed with the debug key so you can sideload it. |
| `FFBoostX-debug-apk` | Unshrunk, separate `applicationId` so both can be installed side by side. |
| `lint-report` | Android Lint HTML report for the release variant. |

Locally: `./gradlew :app:assembleRelease` with JDK 17 and the Android SDK
(compileSdk 35). To sign with a real distribution key instead of the debug key,
pass `-PffKeystore=... -PffStorePassword=... -PffKeyAlias=... -PffKeyPassword=...`.

minSdk 26, targetSdk 35.

---

## What is real, and how

| Feature | How it actually works |
|---|---|
| Memory analysis | `ActivityManager.MemoryInfo`: total, available, threshold, and Android's own `lowMemory` flag. Reported, never "cleaned". |
| Thermal monitoring | `PowerManager.addThermalStatusListener` (a listener, not a poll) plus `getThermalHeadroom` where the HAL implements it, plus battery temperature from the battery broadcast. |
| Latency / jitter / loss | Repeated timed TCP handshakes to a public resolver. Jitter is the mean absolute difference between consecutive probes; loss is failed attempts over total. |
| DNS comparison | Hand-built type-A DNS queries sent over UDP to Cloudflare, Google and Quad9, with a random label so nothing answers from cache. |
| Refresh rate | Real modes from `Display.getSupportedModes`; a mode can be requested for this app's own window. |
| Own render rate | `Choreographer` frame callbacks measuring FF BoostX's own drawing. |
| GPU load | Best-effort read-only vendor metric from common Android GPU nodes; reports unavailable when the OEM hides it. |
| Pro Gamer Mode | Combines supported display-mode request, sustained-performance hint for FF BoostX's own window, and low-overhead monitoring. It does not overclock Free Fire. |
| Game library | Queries launcher activities and game-category apps where Android package visibility permits, with Free Fire/Free Fire MAX explicitly supported. |
| Per-title profiles | Persisted profile settings for performance, DND, brightness, network blocking, touch calibration, control layout and automation preferences. |
| Gaming Focus | Notification policy access; previous interruption filter saved and restored. |
| Brightness / timeout | `WRITE_SETTINGS`; previous values saved and restored. |
| HUD and crosshair | A foreground service with `TYPE_APPLICATION_OVERLAY` windows, both `NOT_FOCUSABLE` and `NOT_TOUCHABLE`, so taps pass straight through. |
| Game exit detection | Usage access, reading only which package is in the foreground. No accessibility service. |
| Background network blocker | A local `VpnService` that routes every app **except** the game into a tunnel nothing reads from, so their traffic is dropped on the device. |
| Session reports | Duration, latency samples, temperature, battery and memory at both ends, with unmeasured values printed as "not measured". |

## What is not possible, and is labelled as such

| Feature | Why |
|---|---|
| Game FPS | Another process's frame rate is not readable. The HUD prints `N/A` rather than echoing the refresh rate. |
| Killing background apps | Since Android 8, `killBackgroundProcesses` only affects the caller. Android controls background process management. |
| CPU / GPU governor, kernel tweaks, DPI override | Root or ADB only. The app does not read or write `/sys` at all. |
| Disabling thermal throttling | Enforced below the framework to protect the hardware. FF BoostX warns and advises instead. |
| Multi-network routing | Android does not expose splitting one game's packets across Wi-Fi and mobile, and a single flow cannot be bonded client-side. |
| Lowering ping | Set by your ISP, the route and the server. No app on the phone can shorten it. |
| Touch sampling rate | No public API. Touch hardware is managed by the device. The app stores a per-game calibration preference instead. |
| Macro execution | No synthetic touch injection or gameplay macro playback. Android requires privileged/accessibility automation and game rules may prohibit it; FF BoostX stores macro presets only. |
| Bullet notifications | Android exposes DND/notification policy but not a public API for a custom scrolling notification ticker over another app. The profile stores the user's preferred low-interruption mode. |
| Mistouch prevention | Android does not let ordinary apps disable another app's system-edge gestures. The profile stores the preference and documents the limitation. |
| Wi-Fi RSSI | Tied to location permission, which this app does not request. Transport type and link estimate are shown instead. |

The **Capabilities** tab builds this list at runtime by probing the device, so
two phones show different results from the same build.

## Boost sequence

Thirteen steps. Seven read the device; six change something, and only what you
enabled in the game profile. Every step reports one of **Applied / Limited /
Skipped / Unavailable / Failed** based on what the platform call returned.

Every change made during a boost is reverted when the session ends: DND,
brightness, screen timeout, keep-awake, the overlay and the network blocker.

## Added game-mode controls

Version 4 adds the requested control surface without making false root-level claims:

- Pro Gamer Mode
- Real-time CPU, RAM, battery, thermal and best-effort GPU monitoring
- Background-network blocking through the existing local VPN
- Per-game notification/call interruption profiles
- Brightness lock with automatic restoration
- Touch-sensitivity calibration preference and control-layout presets
- Macro profile storage without synthetic input injection
- Centralized launcher/game library discovery
- Per-title saved profiles that are selected before launch

These are deliberately implemented as ordinary Android functionality. Features that require root,
ADB, Shizuku, accessibility injection, OEM APIs or kernel access are shown as limited instead of
being simulated.

## Permissions

| Permission | Used for | Optional? |
|---|---|---|
| `INTERNET` | Latency and DNS probes | Required |
| `ACCESS_NETWORK_STATE` | Transport type, link estimate | Required |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`, `SYSTEM_EXEMPTED`) | Overlay and blocker services | Required |
| `POST_NOTIFICATIONS` | The ongoing notification those services must post | Required |
| `WRITE_SETTINGS` | Brightness, screen timeout | Optional, appop |
| `SYSTEM_ALERT_WINDOW` | HUD, crosshair | Optional, appop |
| `PACKAGE_USAGE_STATS` | Auto-hiding the overlay when the game closes | Optional, appop |

`QUERY_ALL_PACKAGES` is not requested; the two game packages are declared in a
`<queries>` block. There is no location permission, no analytics, no advertising
and no tracking library.

## Overlay notice

Some games restrict or prohibit overlays. Check the game's rules before using the
HUD or crosshair. FF BoostX makes no claim that any overlay is undetectable, and
the crosshair is a static drawing: an overlay can draw over the screen, but it
cannot read what is beneath it, so no aim assistance is possible or attempted.

## Project layout

```
core/       Capability registry, boost engine, network/thermal/frame engines,
            device stats, settings, game profiles, session history
overlay/    Foreground overlay service, HUD and crosshair views, VpnService
ui/         Compose theme, shared components, ten screens, boost popup
```

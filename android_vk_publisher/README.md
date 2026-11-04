# VK Publisher for Samsung S25 — safe prototype

Status: source prototype, NOT tested on device, NOT an APK and NOT a verified autonomous uploader.

## Design
- Queue and preparation can run in background.
- Never launch or interact with VK while user is active.
- Do not unlock device, bypass permission screens, tap payment/consent prompts, or submit a video without explicit test approval.
- Accessibility service is an optional UI observer; sensitive Android 16+ screens can block automation.
- This prototype opens a prepared MP4 through Android Sharesheet for manual confirmation. It does NOT claim to publish automatically.
- Next stage requires real VK Video UI inspection and on-device tests.

## Build
Open this folder as a Gradle project in Android Studio. Android SDK 35 and Java 17 required.
Gradle wrapper not included. Build with locally installed Gradle 8.9+.
No APK was compiled or verified in this environment.

## Security
No VK password, cookies or API tokens stored in this application.

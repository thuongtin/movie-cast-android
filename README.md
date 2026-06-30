# Movie Cast Android

Native Android app for browsing movie pages, detecting direct media URLs, and casting only the video stream to a Chromecast or Google Cast TV.

Movie Cast Android is the phone companion for the desktop Movie Cast Browser. It does not mirror the device screen. It sends the selected media URL to the TV so the receiver streams the video directly.

## Stack

- Kotlin
- Jetpack Compose
- Android WebView
- Google Cast Framework
- Media3 preview player

## Features

- Loads the last opened web page on startup.
- Detects media links from WebView network requests and DOM/page data scans.
- Detects standalone subtitle tracks from `<track>`, HLS subtitle groups, and common subtitle URLs.
- Filters likely ad media URLs and shows one recommended movie link by default.
- Shows an inline preview for the selected media URL.
- Uses the Cast SDK route picker to choose a TV and sends the direct media URL, not screen mirroring.
- Includes TV controls: play, pause, stop, seek, volume, mute, and disconnect.
- Shows duration and resolution when the media exposes metadata.
- Remembers the app audio mute setting.
- Keeps local browsing history with clear/open actions.

## Requirements

- Android Studio or Android SDK command line tools.
- JDK 17.
- A Chromecast or Chromecast built in TV on the same local network.
- A network that allows mDNS so devices can be discovered.

## Build And Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew testDebugUnitTest assembleDebug
```

Install on a connected device or running emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.thuongtin.moviecast/.MainActivity
```

## Technical Limits

- The app only casts direct media URLs that the TV can fetch on its own.
- DRM protected streams and streams that require cookies, short lived tokens, or custom headers usually cannot be cast through the Default Media Receiver.
- SRT subtitles are converted through a temporary local WebVTT proxy only when the app can fetch the subtitle URL directly.
- TV and phone must be on the same LAN, and the network must allow Cast discovery.

## Responsible Use

This project is a general purpose media casting tool. It does not host, index, decrypt, or distribute any content, and it includes no mechanism to bypass DRM or other technical protection measures.

Use it only with content you own or are legally allowed to access, and respect the terms of service of any website you visit and the copyright laws of your country.

## License

[MIT](LICENSE)

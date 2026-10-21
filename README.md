# Spectrum 85

Android full-screen landscape spectrum analyser. Black background, 10/20/31 segmented frequency bars, Technics/Pioneer/amber themes, peak hold, decay, sensitivity. Tap top-right to open settings.

## Build without installing Android Studio
1. Create a private GitHub repository and upload the contents of this folder (including `.github`).
2. Open **Actions** > **Build Android APK** > **Run workflow** (or push a commit).
3. Open the completed workflow and download the **Spectrum85-debug-apk** artifact.
4. Unzip the downloaded artifact, copy `app-debug.apk` to your Android phone, and install it. Android may require permission to install from that source.

## Important limitations
- No APK is included because this environment has no Android SDK or Gradle installed; compilation and device testing are not yet verified.
- Microphone is the dependable fallback. Android's device-output Visualizer (session 0) is experimental and may not capture Spotify or may be blocked by Android/audio policies.
- Not an Android Auto app; SYNC 3 display support is not included.
- The current microphone analyser uses FFT frequency bins and is real audio analysis, but audio calibration and perceived response require real-device testing.
- The app stops recording when backgrounded; microphone mode works while the visualiser is in the foreground.

# Screen + Camera Recorder (Android Studio project)

This is a native Android source project intended to be opened in Android Studio.

## Included
- Android MediaProjection permission flow for whole-device screen capture
- Microphone audio mixed into an MP4 screen recording
- Front/back camera selection
- Floating CameraX preview overlay while recording
- Stop button and persistent notification stop action
- Copies finished MP4 files to `Movies/ScreenCameraRecorder` in the phone's media gallery

## Build
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Extract this ZIP.
3. Open the `ScreenCameraRecorder` folder in Android Studio.
4. Let Gradle sync and install the app on a real Android phone.
5. Grant microphone/camera permissions when requested.
6. If using the camera bubble, allow **Display over other apps**.
7. Press Start recording and accept Android's system screen-capture prompt.
8. Stop using the app button or the ongoing notification.

## Important limitations
- This ZIP contains Android Studio source code, not a prebuilt APK.
- Android always requires a user-approved MediaProjection prompt; apps cannot silently record the screen.
- Some protected apps (banking apps, DRM video, secure screens) may appear black or refuse capture.
- Internal/device audio capture is not implemented here; the recorder captures the microphone. Android's internal audio capture support depends on app policy, Android version and capture restrictions.
- Android/OEM camera and overlay policies vary. On some phones the camera service may stop when the app is backgrounded, and some system UI/overlays may not appear in the captured video.
- This is a starter project and has not been tested on every device. If Android Studio reports build errors, resolve them against the installed SDK/Gradle versions.
- Camera bubble position is fixed near the upper-right; draggable positioning is not included.
- Some phones may not support the selected screen size/frame rate/encoding combination. If recording fails, the device's MediaRecorder/encoder may not support it.


## Build an APK using only your phone (Termux + GitHub Actions)
This workflow lets GitHub's hosted build machine compile the project, avoiding a large Android SDK install inside Termux.

1. Install Termux from its official F-Droid/GitHub source, then run:
   `pkg update && pkg upgrade`
   `pkg install git unzip gh`
2. Download the project ZIP into Android's Download folder.
3. In Termux, use `termux-setup-storage` and allow storage access.
4. Extract the ZIP into a working folder, initialize git, and create a GitHub repo:
   `cd ~/storage/downloads`
   `unzip ScreenCameraRecorder_AndroidStudio_Project.zip -d ScreenCameraRecorder`
   `cd ScreenCameraRecorder`
   `git init -b main`
   `git add .`
   `git commit -m "Add Android screen recorder project"`
   `gh auth login`
   `gh repo create ScreenCameraRecorder --private --source=. --remote=origin --push`
5. Open the repository on GitHub in your phone browser, go to Actions, choose "Build Android APK", and press Run workflow if it did not start automatically.
6. Wait for the workflow to finish successfully. Open the completed run, scroll to Artifacts, and download `ScreenCameraRecorder-debug-apk`.
7. Extract that artifact ZIP. Tap `app-debug.apk` to install it; allow installation from that source when Android asks.

Keep the repository private because it contains your project source. GitHub may ask you to verify your account.

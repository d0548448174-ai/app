# VYBE — AI Chorus Music Feed

VYBE scans the audio library stored on the Android device and builds a vertical, Reels-style feed of detected chorus sections.

## What's included
- Local Android MediaStore library scan: song title, artist, album, duration and album cover.
- A pretrained LinkSeg 7-class neural model that labels music sections including verse, chorus, intro, bridge and outro.
- Beat-time estimation as input to the LinkSeg music-structure model.
- A vertical music feed, favorites, local search and full-song playback.
- MP3 export of the detected chorus segment only, saved to Music/VYBE.
- Optional first-run background library analysis. It displays an ongoing Android notification and continues while the app is not on screen.
- Audio files are read and analyzed on-device. They are not uploaded to a server.

## AI engine and offline use
The build downloads the approximately 1.5 MB LinkSeg ONNX model from the project's Hugging Face model repository and packages it inside the APK. The installed app loads the model from its own assets and executes it locally with ONNX Runtime Web (WASM). The model itself does not require an AI API key or an internet connection after installation.

LinkSeg uses a pretrained music-structure model. It requires beat times, so VYBE supplies locally estimated beat times before inference. This is real model-based section classification, not a fixed timestamp guess. No automatic detector is perfect: some tracks may have no clearly detected chorus or may be labeled incorrectly. The app leaves those tracks without a generated chorus instead of inventing one.

## Android permissions
- Music/audio access: to list and read local audio files.
- Notifications and foreground data-sync service: to show progress while background analysis is running.
- Legacy write permission on Android 9 and earlier: to save exported MP3 files in the shared Music folder.

Background analysis starts only after the user chooses **התחל סריקת AI ברקע**. Android shows an ongoing notification. The analysis service uses a time-limited partial wake lock while it works; it is not intended to run indefinitely.

## Build APK with GitHub Actions
1. Open the repository's **Actions** tab.
2. Select **Build VYBE AI Chorus APK**.
3. Open the latest successful run.
4. Download the artifact named **VYBE-AI-Chorus-APK** and install the APK on Android.

The workflow installs the JavaScript dependencies, downloads the LinkSeg model for packaging, bundles the local WebView player and ONNX WASM runtime, then runs assembleDebug.

## Model attribution and licenses
**LinkSeg model attribution (CC-BY 4.0):** M. Buisson, B. McFee, S. Essid, “Using Pairwise Link Prediction and Graph Attention Networks for Music Structure Analysis,” ISMIR 2024. Upstream model: https://github.com/morgan76/LinkSeg. ONNX port and model card: https://github.com/elicwhite/LinkSeg-web and https://huggingface.co/elicwhite/linkseg-7c-onnx.

The LinkSeg-web port/package code is MIT-licensed; the trained LinkSeg model weights remain CC-BY 4.0 and require the attribution above. LAMEJS is included for local MP3 encoding under its own license.

- Android application ID: com.vybe.musicfeed
- Minimum Android: 6.0 (API 23)
- Target Android: API 35

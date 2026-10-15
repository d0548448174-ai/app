# VYBE — Music Feed for Android

VYBE is a local-first, TikTok-inspired music feed for Android. It scans the device's audio library (after permission is granted) and turns tracks into a vertical, swipeable listening feed.

## Features
- Reads audio tracks and album IDs from Android MediaStore; requests `READ_MEDIA_AUDIO` on Android 13+ and legacy storage permission on older versions.
- Vertical swipe feed, automatic short previews, next/previous track navigation, pause/play, and full-song playback.
- Search by title, artist, or album; browse the local library; save favorites.
- Save a preferred preview start per track with **סמן פזמון**. Move the timeline to the start of the chorus/section first, then save it.
- Album-art backdrop where available, with a dark neon interface.
- Audio files remain on the device; the app does not upload them.

## About chorus selection
Audio files usually do not include a standard marker identifying the chorus. For tracks without a saved marker, VYBE starts a preview at an estimated position (about 36% into the track). It is a starting guess, not guaranteed chorus detection. Use the timeline to find the chorus and **סמן פזמון** to remember that segment for future listens.

## Build the APK
Open **Actions → Build VYBE Music Feed APK → Run workflow** in this repository. When the run succeeds, download the `VYBE-Music-Feed-APK` artifact from that workflow run and install the APK on Android.

- Project: `shorts-music-android/`
- Package: `com.vybe.musicfeed`
- Min Android: 6.0 (API 23)

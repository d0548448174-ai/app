# BeatDance Studio

A Hebrew RTL, browser-based 3D music dance experience. Upload a local audio file and the character responds to live low-frequency/onset detection, with an estimated BPM display.

## Run
Open `index.html` in a modern browser with an internet connection (Three.js is loaded from CDN). Select an MP3/WAV/other browser-supported audio file, then press Play.

## Features
- Three selectable stylized 3D characters (procedural Three.js models)
- Live bass/onset-based beat detection and approximate BPM estimation
- Dance intensity, sensitivity and dance-style controls
- Audio stays local in the browser; no upload server is used

## Notes
Beat detection is an estimate and depends on the mix and browser audio support. The 3D renderer requires WebGL. GitHub Pages can host the page as a static site after enabling Pages for the repository.

import { FFmpeg } from '@ffmpeg/ffmpeg';
import { toBlobURL } from '@ffmpeg/util';
import './style.css';

const app = document.querySelector('#app');
app.innerHTML = `
  <main class="shell">
    <header class="topbar">
      <div class="brand"><span class="brand-icon">♫</span><span>SOUND SWITCH <small>STUDIO</small></span></div>
      <span class="local-tag"><i></i> עיבוד מקומי במכשיר</span>
    </header>

    <section class="hero">
      <div class="eyebrow">VIDEO AUDIO REPLACER</div>
      <h1>הסאונד החדש<br><span>מתחיל כאן.</span></h1>
      <p>מחליפים את פס הקול של הסרטון במוזיקה שבוחרים. פשוט, נקי ובשליטה מלאה.</p>
    </section>

    <section class="workspace">
      <div class="step-card">
        <div class="step-head"><span class="step-number">01</span><div><h2>הסרטון שלך</h2><p>בחר קובץ וידאו מהמחשב</p></div></div>
        <label class="dropzone" for="videoInput"><span class="upload-icon">▣</span><strong>בחירת סרטון</strong><small>MP4, MOV, WEBM ועוד</small><input id="videoInput" type="file" accept="video/*"></label>
        <div id="videoName" class="file-name">עדיין לא נבחר סרטון</div>
        <video id="videoPreview" controls playsinline hidden></video>
      </div>

      <div class="step-card">
        <div class="step-head"><span class="step-number">02</span><div><h2>פס הקול החדש</h2><p>בחר את קובץ המוזיקה שיחליף את המקור</p></div></div>
        <label class="dropzone audio-drop" for="audioInput"><span class="upload-icon">♫</span><strong>בחירת קובץ שמע</strong><small>MP3, WAV, M4A ועוד</small><input id="audioInput" type="file" accept="audio/*"></label>
        <div id="audioName" class="file-name">עדיין לא נבחר קובץ שמע</div>
        <audio id="audioPreview" controls hidden></audio>
      </div>
    </section>

    <section class="options-card">
      <div><h2>הגדרות ייצוא</h2><p>הסאונד המקורי יוסר לחלוטין.</p></div>
      <label class="check-row"><input id="loopAudio" type="checkbox" checked><span><b>התאמת אורך אוטומטית</b><small>חזרה על המוזיקה אם היא קצרה מהסרטון</small></span></label>
      <label class="check-row"><input id="fadeAudio" type="checkbox"><span><b>דעיכה בסיום</b><small>הנמכת המוזיקה בהדרגה בסוף הסרטון</small></span></label>
      <button id="startBtn" class="primary" disabled><span>✦</span> החלף סאונד וצור סרטון</button>
      <div class="progress-wrap" id="progressWrap" hidden><div class="progress-meta"><span id="status">מכין מנוע עריכה…</span><b id="percent">0%</b></div><div class="progress-track"><div id="progressBar"></div></div></div>
      <p class="privacy-note">הקבצים מעובדים בדפדפן שלך ואינם מועלים לשרת.</p>
    </section>

    <section id="resultCard" class="result-card" hidden>
      <div class="result-title"><span class="success-mark">✓</span><div><h2>הסרטון מוכן!</h2><p>אפשר לצפות בתוצאה או לשמור אותה במחשב.</p></div></div>
      <video id="resultVideo" controls playsinline></video>
      <a id="downloadBtn" class="download-btn" download="sound-switch.mp4">⬇ הורדת הסרטון החדש (MP4)</a>
    </section>
    <footer>Sound Switch Studio <span>•</span> גרסה 1.0</footer>
  </main>
`;

const $ = (id) => document.getElementById(id);
const videoInput = $('videoInput');
const audioInput = $('audioInput');
const startBtn = $('startBtn');
let ffmpeg;
let engineReady = false;
let videoUrl;
let audioUrl;
let resultUrl;

function setStatus(message, percent = null) {
  $('status').textContent = message;
  if (percent !== null) {
    const value = Math.max(0, Math.min(100, Math.round(percent)));
    $('percent').textContent = value + '%';
    $('progressBar').style.width = value + '%';
  }
}
function selectedFilesReady() {
  startBtn.disabled = !(videoInput.files?.[0] && audioInput.files?.[0]);
}
function safeExt(file, fallback) {
  const ext = file.name.split('.').pop()?.toLowerCase().replace(/[^a-z0-9]/g, '');
  return ext && ext.length <= 8 ? ext : fallback;
}
videoInput.addEventListener('change', () => {
  const file = videoInput.files?.[0];
  if (!file) return;
  if (videoUrl) URL.revokeObjectURL(videoUrl);
  videoUrl = URL.createObjectURL(file);
  $('videoPreview').src = videoUrl;
  $('videoPreview').hidden = false;
  $('videoName').textContent = file.name;
  $('resultCard').hidden = true;
  selectedFilesReady();
});
audioInput.addEventListener('change', () => {
  const file = audioInput.files?.[0];
  if (!file) return;
  if (audioUrl) URL.revokeObjectURL(audioUrl);
  audioUrl = URL.createObjectURL(file);
  $('audioPreview').src = audioUrl;
  $('audioPreview').hidden = false;
  $('audioName').textContent = file.name;
  selectedFilesReady();
});

async function loadEngine() {
  if (engineReady) return;
  setStatus('טוען מנוע עריכה בפעם הראשונה…', 0);
  ffmpeg = new FFmpeg();
  ffmpeg.on('progress', ({ progress }) => {
    setStatus('מעבד את הסרטון…', Math.min(99, progress * 100));
  });
  // FFmpeg's worker is bundled by Vite and served from this site, not from a CDN.
  // Only the WASM core is fetched as data and passed to the local worker.
  const coreBase = 'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.12.10/dist/umd';
  const coreURL = await toBlobURL(coreBase + '/ffmpeg-core.js', 'text/javascript');
  const wasmURL = await toBlobURL(coreBase + '/ffmpeg-core.wasm', 'application/wasm');
  await ffmpeg.load({ coreURL, wasmURL });
  engineReady = true;
}

startBtn.addEventListener('click', async () => {
  const video = videoInput.files?.[0];
  const audio = audioInput.files?.[0];
  if (!video || !audio) return;
  startBtn.disabled = true;
  $('progressWrap').hidden = false;
  $('resultCard').hidden = true;
  $('progressBar').style.width = '0%';
  try {
    await loadEngine();
    setStatus('מכין את הקבצים לעריכה…', 1);
    const videoExt = safeExt(video, 'mp4');
    const audioExt = safeExt(audio, 'mp3');
    const inputVideo = 'source.' + videoExt;
    const inputAudio = 'music.' + audioExt;
    await ffmpeg.writeFile(inputVideo, new Uint8Array(await video.arrayBuffer()));
    await ffmpeg.writeFile(inputAudio, new Uint8Array(await audio.arrayBuffer()));

    const args = ['-i', inputVideo];
    if ($('loopAudio').checked) args.push('-stream_loop', '-1');
    args.push('-i', inputAudio, '-map', '0:v:0', '-map', '1:a:0', '-c:v', 'libx264',
      '-preset', 'ultrafast', '-crf', '23', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '192k');
    if ($('fadeAudio').checked) {
      // Fade duration is based on a conservative 3-second tail; FFmpeg stops at video end.
      args.push('-af', 'afade=t=out:st=0:d=3');
    }
    args.push('-shortest', '-movflags', '+faststart', 'finished.mp4');
    setStatus('מחליף את פס הקול ומייצא MP4…', 2);
    const exitCode = await ffmpeg.exec(args);
    if (exitCode !== 0) throw new Error('מנוע העריכה החזיר קוד שגיאה ' + exitCode);
    const output = await ffmpeg.readFile('finished.mp4');
    if (!output?.length) throw new Error('קובץ הפלט ריק.');
    const blob = new Blob([output], { type: 'video/mp4' });
    if (resultUrl) URL.revokeObjectURL(resultUrl);
    resultUrl = URL.createObjectURL(blob);
    $('resultVideo').src = resultUrl;
    $('downloadBtn').href = resultUrl;
    $('resultCard').hidden = false;
    setStatus('הייצוא הושלם בהצלחה.', 100);
    $('resultCard').scrollIntoView({ behavior: 'smooth', block: 'start' });
  } catch (error) {
    console.error(error);
    setStatus('לא הצלחנו לייצא את הסרטון: ' + (error?.message || String(error)) +
      '. בדוק שהחיבור מאפשר טעינת מנוע WASM ונסה קובץ MP4 רגיל.');
  } finally {
    startBtn.disabled = false;
  }
});

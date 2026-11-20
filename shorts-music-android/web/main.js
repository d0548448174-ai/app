import { LinkSeg } from 'linkseg-web';
import * as ort from 'onnxruntime-web';
import lamejs from 'lamejs';
import { estimateBeats } from './beats.js';
import './styles.css';

const $ = (id) => document.getElementById(id);
const bridge = window.Android;
const audio = $('audio');
let tracks = [];
let current = null;
let currentIndex = -1;
let currentBytes = null;
let currentBuffer = null;
let currentObjectUrl = null;
let model = null;
let chorusMode = true;
let favoritesOnly = false;
let touchStartY = 0;
let toastTimer = null;
let busy = false;
let backgroundMode = new URLSearchParams(location.search).get('background') === '1';
const ANALYSIS_VERSION = 2;
const audioContext = new (window.AudioContext || window.webkitAudioContext)();

ort.env.wasm.wasmPaths = new URL('./', import.meta.url).href;
ort.env.wasm.numThreads = 1;

function toast(message) {
  const node = $('statusToast');
  node.textContent = message;
  node.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => node.classList.remove('show'), 2600);
}
function safeParse(text, fallback) {
  try { return JSON.parse(text); } catch (_) { return fallback; }
}
function formatTime(seconds) {
  const s = Math.max(0, Math.floor(seconds || 0));
  return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
}
function setModalProgress(title, message, percent) {
  $('progressTitle').textContent = title;
  $('progressText').textContent = message;
  $('modelProgress').style.width = Math.max(0, Math.min(100, percent || 0)) + '%';
}
function updateCounts() {
  const done = tracks.filter(t => t.analysis && t.analysis.chorus).length;
  $('trackCount').textContent = tracks.length + ' שירים';
  $('librarySubtitle').textContent = done + ' פזמונים זוהו · ' + tracks.length + ' שירים בספרייה';
}
function normalizeTrack(raw) {
  const analysis = safeParse(raw.analysis, null);
  return {
    id: String(raw.id),
    title: raw.title || 'ללא שם',
    artist: raw.artist || 'אמן לא ידוע',
    album: raw.album || 'אלבום לא ידוע',
    duration: Number(raw.duration || 0) / 1000,
    albumId: String(raw.albumId || '0'),
    favorite: !!raw.favorite,
    analysis: analysis && analysis.version === ANALYSIS_VERSION ? analysis : null
  };
}
function refreshTracks() {
  if (!bridge) return [];
  const data = safeParse(bridge.getTracksJson(), []);
  tracks = data.map(normalizeTrack);
  updateCounts();
  return tracks;
}
function readBytes(track) {
  if (current && current.id === track.id && currentBytes) return currentBytes;
  const encoded = bridge.readAudioBase64(track.id);
  if (!encoded || encoded.startsWith('ERR:')) throw new Error(encoded || 'לא ניתן לקרוא את השיר');
  const binary = atob(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
function bytesToArrayBuffer(bytes) {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
}
async function decodeTrack(track, keepBytes) {
  const bytes = readBytes(track);
  if (keepBytes && current && current.id === track.id) currentBytes = bytes;
  const buffer = await audioContext.decodeAudioData(bytesToArrayBuffer(bytes));
  return { bytes, buffer };
}
function toMono(buffer) {
  const length = buffer.length;
  const channels = buffer.numberOfChannels;
  const mono = new Float32Array(length);
  for (let c = 0; c < channels; c++) {
    const samples = buffer.getChannelData(c);
    for (let i = 0; i < length; i++) mono[i] += samples[i] / channels;
  }
  return mono;
}
async function ensureModel(showUi) {
  if (model) return model;
  if (showUi) {
    $('progressModal').classList.remove('hidden');
    setModalProgress('מוריד את מנוע הזיהוי', 'בפעם הראשונה מורידים מודל AI קטן ושומרים אותו במכשיר.', 0);
  }
  try {
    model = await LinkSeg.load({
      backend: 'wasm',
      modelUrl: new URL('./linkseg_7c.onnx', location.href).href,
      onProgress: (event) => {
        const pct = event.total ? Math.round(event.loaded * 100 / event.total) : 0;
        if (showUi) {
          const mb = (event.loaded / 1000000).toFixed(1);
          const total = event.total ? ' / ' + (event.total / 1000000).toFixed(1) + ' MB' : '';
          setModalProgress('מכין את מנוע הזיהוי', 'הורדה: ' + mb + total, pct);
        }
        if (backgroundMode && bridge) {
          bridge.updateBackgroundProgress('מוריד מודל AI · ' + pct + '%', 0, 0);
        }
      }
    });
    if (showUi) setModalProgress('מנוע הזיהוי מוכן', 'המודל מוכן לניתוח מקומי.', 100);
    return model;
  } catch (error) {
    if (showUi) $('progressModal').classList.add('hidden');
    throw new Error('לא הצלחתי להוריד את מודל ה-AI. בדוק חיבור לאינטרנט ונסה שוב. ' + (error.message || ''));
  }
}
function selectChorus(sections, duration) {
  const candidates = sections
    .filter(s => String(s.label).toLowerCase() === 'chorus' && Number(s.end) > Number(s.start))
    .map(s => ({ start: Math.max(0, Number(s.start)), end: Math.min(duration, Number(s.end)) }))
    .filter(s => s.end - s.start >= 8 && s.end - s.start <= 75);
  if (!candidates.length) return null;
  const preferred = candidates.filter(s => (s.end - s.start) >= 12 && (s.end - s.start) <= 45);
  const pool = preferred.length ? preferred : candidates;
  // Prefer a representative-length chorus rather than a tiny transition or unusually long section.
  const mean = pool.reduce((sum, s) => sum + (s.end - s.start), 0) / pool.length;
  pool.sort((a, b) => {
    const scoreA = Math.abs((a.end - a.start) - mean) + (a.start < 12 ? 5 : 0);
    const scoreB = Math.abs((b.end - b.start) - mean) + (b.start < 12 ? 5 : 0);
    return scoreA - scoreB || a.start - b.start;
  });
  return pool[0];
}
async function analyzeTrack(track, options = {}) {
  if (!track) throw new Error('לא נבחר שיר');
  const showUi = !!options.showUi;
  if (showUi) {
    $('analysisBadge').textContent = 'מנתח עכשיו';
    $('chorusLabel').textContent = 'ה-AI מקשיב לשיר…';
    $('analyzeButton').disabled = true;
  }
  const start = performance.now();
  const decoded = await decodeTrack(track, false);
  const mono = toMono(decoded.buffer);
  const tempo = estimateBeats(mono, decoded.buffer.sampleRate);
  if (!tempo.beats || tempo.beats.length < 4) throw new Error('לא זוהו מספיק פעימות כדי לנתח את השיר');
  const engine = await ensureModel(showUi);
  if (showUi) setModalProgress('מנתח את מבנה השיר', 'בודק פעימות, מעברים וחזרות מוזיקליות…', 100);
  const sections = await engine.analyze({
    audio: mono,
    sampleRate: decoded.buffer.sampleRate,
    beats: tempo.beats,
    peakWindowSeconds: 8,
    tau: 0
  });
  const chorus = selectChorus(sections, decoded.buffer.duration);
  if (current && current.id === track.id) currentBuffer = decoded.buffer;
  const result = {
    version: ANALYSIS_VERSION,
    engine: 'LinkSeg 7-class',
    analyzedAt: Date.now(),
    duration: decoded.buffer.duration,
    bpmEstimate: Math.round(tempo.bpm),
    sections: sections.map(s => ({ start: Number(s.start), end: Number(s.end), label: String(s.label) })),
    chorus
  };
  bridge.saveAnalysis(track.id, JSON.stringify(result));
  track.analysis = result;
  if (showUi) {
    $('progressModal').classList.add('hidden');
    $('analyzeButton').disabled = false;
    $('analysisBadge').textContent = chorus ? 'פזמון זוהה' : 'לא זוהה פזמון';
    if (current && current.id === track.id) renderCurrent();
    const elapsed = ((performance.now() - start) / 1000).toFixed(1);
    toast(chorus ? 'הפזמון זוהה ונשמר · ' + elapsed + ' שניות' : 'המודל לא זיהה פזמון ברור בשיר הזה');
  }
  return result;
}
function renderWaveform() {
  const root = $('waveform');
  root.innerHTML = '';
  const heights = [9,14,19,12,24,16,11,20,26,14,10,22,17,25,13,19,27,15,11,22,18,26,13,20,28,16,11,22,17,24,13,19,25,14,10,20,16,25,12,18,27,15,10,21,17,23,12,19,27,15,10,22,16,24,13,19,26,14,10,22,17,25,12,18,27,14,10,20,16,24,12,18,26,14,10,21,16,25,12,19];
  for (const value of heights) {
    const bar = document.createElement('span');
    bar.style.height = value + 'px';
    root.appendChild(bar);
  }
}
function renderCurrent() {
  if (!current) return;
  $('songTitle').textContent = current.title;
  $('songArtist').textContent = current.artist + ' · ' + current.album;
  $('coverTitle').textContent = current.title;
  $('coverArtist').textContent = current.artist;
  $('favoriteButton').classList.toggle('active', current.favorite);
  $('favoriteButton').textContent = current.favorite ? '♥' : '♡';
  const cover = bridge.getCoverBase64(current.albumId);
  $('cover').src = cover || '';
  $('cover').style.opacity = cover ? '0.9' : '0';
  if (!cover) $('cover').removeAttribute('src');
  const analysis = current.analysis;
  const chorus = analysis && analysis.chorus;
  if (chorus) {
    $('analysisBadge').textContent = 'פזמון זוהה';
    $('chorusLabel').textContent = 'פזמון · ' + Math.round(chorus.end - chorus.start) + ' שניות';
    $('chorusTime').textContent = formatTime(chorus.start) + ' – ' + formatTime(chorus.end);
    $('downloadButton').disabled = false;
    $('analyzeButton').textContent = 'נתח שוב';
  } else {
    $('analysisBadge').textContent = analysis ? 'לא זוהה פזמון' : 'ממתין לניתוח';
    $('chorusLabel').textContent = analysis ? 'לא נמצא פזמון ברור' : 'השיר עדיין לא נותח';
    $('chorusTime').textContent = '--:--';
    $('downloadButton').disabled = true;
    $('analyzeButton').textContent = 'נתח שיר';
  }
  audio.pause();
  audio.currentTime = 0;
  chorusMode = !!chorus;
  $('playButton').textContent = '▶';
  $('progressLine').style.width = '0%';
}
function getFeedTracks() {
  const source = favoritesOnly ? tracks.filter(t => t.favorite) : tracks;
  const withChoruses = source.filter(t => t.analysis && t.analysis.chorus);
  return withChoruses.length ? withChoruses : source;
}
async function loadCurrentAudio() {
  if (!current) return;
  if (currentBytes && currentObjectUrl) return;
  const bytes = readBytes(current);
  currentBytes = bytes;
  const mime = 'audio/mpeg';
  const blob = new Blob([bytes], { type: mime });
  if (currentObjectUrl) URL.revokeObjectURL(currentObjectUrl);
  currentObjectUrl = URL.createObjectURL(blob);
  audio.src = currentObjectUrl;
  audio.load();
}
async function playChorus() {
  if (!current) return;
  if (!current.analysis || !current.analysis.chorus) {
    toast('השיר עדיין לא נותח. לחץ על „נתח שיר”.');
    return;
  }
  try {
    await loadCurrentAudio();
    chorusMode = true;
    audio.currentTime = current.analysis.chorus.start;
    await audio.play();
    $('playButton').textContent = 'Ⅱ';
  } catch (error) {
    toast('לא הצלחתי להפעיל את השיר: ' + (error.message || ''));
  }
}
async function selectTrack(index, autoplay) {
  const list = getFeedTracks();
  if (!list.length) {
    toast('אין שירים בספרייה. אשר גישה למוזיקה ונסה שוב.');
    return;
  }
  const next = list[(index + list.length) % list.length];
  if (current && current.id !== next.id) {
    audio.pause();
    currentBytes = null;
    currentBuffer = null;
    if (currentObjectUrl) URL.revokeObjectURL(currentObjectUrl);
    currentObjectUrl = null;
  }
  current = next;
  currentIndex = tracks.findIndex(t => t.id === next.id);
  renderCurrent();
  if (autoplay && next.analysis && next.analysis.chorus) await playChorus();
  else if (autoplay) {
    try {
      await loadCurrentAudio();
      audio.currentTime = 0;
      chorusMode = false;
      await audio.play();
      $('playButton').textContent = 'Ⅱ';
    } catch (_) { }
  }
}
function nextTrack(direction) {
  const list = getFeedTracks();
  if (!list.length) return;
  let position = current ? list.findIndex(t => t.id === current.id) : -1;
  if (position < 0) position = direction > 0 ? -1 : 0;
  selectTrack(position + direction, true);
}
async function togglePlay() {
  if (!current) return;
  if (audio.paused) {
    if (chorusMode && current.analysis && current.analysis.chorus) {
      if (audio.currentTime < current.analysis.chorus.start || audio.currentTime >= current.analysis.chorus.end) {
        audio.currentTime = current.analysis.chorus.start;
      }
    }
    try {
      await loadCurrentAudio();
      await audio.play();
      $('playButton').textContent = 'Ⅱ';
    } catch (error) {
      toast('לא ניתן לנגן את השיר הזה');
    }
  } else {
    audio.pause();
    $('playButton').textContent = '▶';
  }
}
async function playFullSong() {
  if (!current) return;
  try {
    await loadCurrentAudio();
    chorusMode = false;
    audio.currentTime = 0;
    await audio.play();
    $('playButton').textContent = 'Ⅱ';
    toast('מנגן את השיר המלא');
  } catch (_) {
    toast('לא ניתן לנגן את השיר המלא');
  }
}
function renderLibrary() {
  const list = $('trackList');
  list.innerHTML = '';
  const query = $('searchInput').value.trim().toLowerCase();
  const source = favoritesOnly ? tracks.filter(t => t.favorite) : tracks;
  const shown = source.filter(t => (t.title + ' ' + t.artist + ' ' + t.album).toLowerCase().includes(query));
  for (const track of shown) {
    const row = document.createElement('button');
    row.className = 'track-row';
    const image = document.createElement('img');
    image.className = 'track-cover';
    image.alt = '';
    const art = bridge.getCoverBase64(track.albumId);
    if (art) image.src = art;
    const info = document.createElement('span');
    info.className = 'track-info';
    const title = document.createElement('strong');
    title.textContent = track.title;
    const artist = document.createElement('span');
    artist.textContent = track.artist + ' · ' + track.album;
    info.append(title, artist);
    const status = document.createElement('span');
    status.className = 'track-status';
    status.textContent = track.analysis && track.analysis.chorus ? 'פזמון זוהה' : track.analysis ? 'לא זוהה' : 'לא נותח';
    row.append(image, info, status);
    row.addEventListener('click', () => {
      $('libraryView').classList.add('hidden');
      $('feedView').classList.remove('hidden');
      $('feedTab').classList.add('selected');
      $('libraryTab').classList.remove('selected');
      const idx = getFeedTracks().findIndex(t => t.id === track.id);
      if (idx >= 0) selectTrack(idx, true);
      else {
        current = track;
        renderCurrent();
        toast('לשיר הזה עדיין אין פזמון זוהה');
      }
    });
    list.appendChild(row);
  }
  if (!shown.length) {
    const empty = document.createElement('p');
    empty.style.cssText = 'text-align:center;color:#a5adbf;padding:24px 8px;font-size:13px';
    empty.textContent = tracks.length ? 'לא נמצאו שירים בחיפוש' : 'לא נמצאו קובצי מוזיקה במכשיר';
    list.appendChild(empty);
  }
}
function showLibrary() {
  $('feedView').classList.add('hidden');
  $('libraryView').classList.remove('hidden');
  $('libraryTab').classList.add('selected');
  $('feedTab').classList.remove('selected');
  renderLibrary();
}
function hideLibrary() {
  $('libraryView').classList.add('hidden');
  $('feedView').classList.remove('hidden');
  $('libraryTab').classList.remove('selected');
  $('feedTab').classList.add('selected');
}
async function backgroundScan() {
  if (!bridge || busy) return;
  busy = true;
  const all = refreshTracks();
  const pending = all.filter(t => !t.analysis);
  const total = pending.length;
  if (!total) {
    bridge.updateBackgroundProgress('כל השירים כבר נותחו', all.length, all.length);
    bridge.finishBackground('הספרייה כבר נותחה · ' + all.length + ' שירים');
    busy = false;
    return;
  }
  let completed = 0;
  let failed = 0;
  try {
    await ensureModel(false);
    for (const track of pending) {
      try {
        bridge.updateBackgroundProgress('מנתח: ' + track.title, completed, total);
        await analyzeTrack(track, { showUi: false });
      } catch (error) {
        failed++;
        bridge.updateBackgroundProgress('דילוג על ' + track.title, completed, total);
      }
      completed++;
      bridge.updateBackgroundProgress('נותרו ' + (total - completed) + ' שירים', completed, total);
      // Let Android/WebView process notification updates and avoid monopolizing the UI thread.
      await new Promise(resolve => setTimeout(resolve, 50));
    }
    refreshTracks();
    bridge.finishBackground(failed ? 'הניתוח הסתיים · ' + (completed - failed) + ' הצליחו, ' + failed + ' דולגו' : 'ניתוח כל ' + completed + ' השירים הושלם');
  } catch (error) {
    bridge.finishBackground('הניתוח נעצר: ' + (error.message || 'שגיאה'));
  } finally {
    busy = false;
  }
}
async function downloadChorus() {
  if (!current || !current.analysis || !current.analysis.chorus) return;
  const start = current.analysis.chorus.start;
  const end = current.analysis.chorus.end;
  $('downloadButton').disabled = true;
  $('downloadButton').textContent = 'מכין MP3…';
  try {
    let buffer = currentBuffer;
    if (!buffer) {
      const decoded = await decodeTrack(current, true);
      buffer = decoded.buffer;
      currentBuffer = buffer;
    }
    const sampleRate = buffer.sampleRate;
    const left = Math.max(0, Math.floor(start * sampleRate));
    const right = Math.min(buffer.length, Math.floor(end * sampleRate));
    if (right - left < sampleRate * 5) throw new Error('הפזמון קצר מדי לייצוא');
    const channelData = buffer.getChannelData(0);
    const other = buffer.numberOfChannels > 1 ? buffer.getChannelData(1) : null;
    const mono = new Float32Array(right - left);
    for (let i = left; i < right; i++) mono[i - left] = other ? (channelData[i] + other[i]) / 2 : channelData[i];
    const Encoder = lamejs.Mp3Encoder || (lamejs.default && lamejs.default.Mp3Encoder);
    if (!Encoder) throw new Error('מנוע MP3 לא נטען');
    const encoder = new Encoder(1, sampleRate, 192);
    const chunks = [];
    const frameSize = 1152;
    const pcm = new Int16Array(mono.length);
    for (let i = 0; i < mono.length; i++) {
      const sample = Math.max(-1, Math.min(1, mono[i]));
      pcm[i] = sample < 0 ? sample * 32768 : sample * 32767;
    }
    for (let i = 0; i < pcm.length; i += frameSize) {
      const part = pcm.subarray(i, Math.min(i + frameSize, pcm.length));
      const encoded = encoder.encodeBuffer(part);
      if (encoded && encoded.length) chunks.push(new Uint8Array(encoded));
    }
    const tail = encoder.flush();
    if (tail && tail.length) chunks.push(new Uint8Array(tail));
    let size = chunks.reduce((sum, part) => sum + part.length, 0);
    const mp3 = new Uint8Array(size);
    let offset = 0;
    for (const part of chunks) { mp3.set(part, offset); offset += part.length; }
    let binary = '';
    const chunkSize = 0x6000;
    for (let i = 0; i < mp3.length; i += chunkSize) {
      binary += String.fromCharCode.apply(null, mp3.subarray(i, Math.min(i + chunkSize, mp3.length)));
    }
    const encoded = btoa(binary);
    const fileName = current.title.replace(/[\\/:*?"<>|]/g, '_') + '_chorus.mp3';
    const result = bridge.saveMp3Base64(fileName, encoded);
    if (result === 'OK') toast('הפזמון נשמר בתיקיית Music/VYBE');
    else if (result === 'ERR:PERMISSION') toast('אשר הרשאת אחסון ואז לחץ שוב על ההורדה');
    else toast('שמירת MP3 נכשלה: ' + result);
  } catch (error) {
    toast('לא הצלחתי ליצור MP3: ' + (error.message || ''));
  } finally {
    $('downloadButton').disabled = false;
    $('downloadButton').innerHTML = '<span>↓</span> הורדת הפזמון · MP3';
  }
}
async function scanThisSong() {
  if (!current) return;
  if (busy) { toast('סריקה ברקע כבר פועלת'); return; }
  try {
    busy = true;
    await analyzeTrack(current, { showUi: true });
  } catch (error) {
    $('progressModal').classList.add('hidden');
    $('analyzeButton').disabled = false;
    $('analysisBadge').textContent = 'שגיאת ניתוח';
    toast(error.message || 'הניתוח נכשל');
  } finally {
    busy = false;
  }
}
function bindEvents() {
  $('playButton').addEventListener('click', togglePlay);
  $('previousButton').addEventListener('click', () => nextTrack(-1));
  $('nextButton').addEventListener('click', () => nextTrack(1));
  $('fullButton').addEventListener('click', playFullSong);
  $('downloadButton').addEventListener('click', downloadChorus);
  $('analyzeButton').addEventListener('click', scanThisSong);
  $('scanButton').addEventListener('click', () => {
    if (!bridge) return;
    bridge.startBackgroundAnalysis();
    toast('סריקת AI החלה. אפשר להמשיך להשתמש באפליקציה.');
  });
  $('libraryButton').addEventListener('click', showLibrary);
  $('libraryTab').addEventListener('click', showLibrary);
  $('backToFeed').addEventListener('click', hideLibrary);
  $('feedTab').addEventListener('click', hideLibrary);
  $('favoritesTab').addEventListener('click', () => {
    favoritesOnly = !favoritesOnly;
    $('favoritesTab').classList.toggle('selected', favoritesOnly);
    if (favoritesOnly) toast('מציג את השירים שאהבת');
    else toast('חזרה לכל השירים');
    if (current) {
      const list = getFeedTracks();
      const next = list.findIndex(t => t.id === current.id);
      if (next >= 0) renderCurrent();
      else if (list.length) selectTrack(0, true);
    }
    renderLibrary();
  });
  $('favoriteButton').addEventListener('click', () => {
    if (!current) return;
    current.favorite = !current.favorite;
    bridge.setFavorite(current.id, current.favorite);
    renderCurrent();
  });
  $('searchInput').addEventListener('input', renderLibrary);
  $('startScan').addEventListener('click', () => {
    $('welcomeModal').classList.add('hidden');
    localStorage.setItem('vybe_welcome_done', '1');
    bridge.startBackgroundAnalysis();
    toast('הסריקה החלה ברקע. אפשר להמשיך להאזין.');
  });
  $('skipScan').addEventListener('click', () => {
    $('welcomeModal').classList.add('hidden');
    localStorage.setItem('vybe_welcome_done', '1');
  });
  $('closeProgress').addEventListener('click', () => $('progressModal').classList.add('hidden'));
  audio.addEventListener('timeupdate', () => {
    if (!current || !audio.duration) return;
    $('progressLine').style.width = (audio.currentTime / audio.duration * 100) + '%';
    if (chorusMode && current.analysis && current.analysis.chorus && audio.currentTime >= current.analysis.chorus.end) {
      audio.pause();
      $('playButton').textContent = '▶';
    }
  });
  audio.addEventListener('ended', () => { $('playButton').textContent = '▶'; });
  $('songCard').addEventListener('touchstart', (event) => { touchStartY = event.changedTouches[0].screenY; }, { passive: true });
  $('songCard').addEventListener('touchend', (event) => {
    const diff = touchStartY - event.changedTouches[0].screenY;
    if (Math.abs(diff) > 65) nextTrack(diff > 0 ? 1 : -1);
  }, { passive: true });
}
function startup() {
  if (!bridge) {
    toast('צריך לפתוח את האפליקציה המותקנת כדי לגשת למוזיקה');
    return;
  }
  refreshTracks();
  bindEvents();
  renderWaveform();
  if (backgroundMode) {
    document.body.classList.add('background-worker');
    setTimeout(backgroundScan, 700);
    return;
  }
  if (tracks.length) {
    const ready = tracks.findIndex(t => t.analysis && t.analysis.chorus);
    selectTrack(ready >= 0 ? ready : 0, false);
  } else {
    $('songTitle').textContent = 'ספריית המוזיקה ריקה';
    $('songArtist').textContent = 'בדוק שקובצי שמע שמורים במכשיר';
    $('analysisBadge').textContent = 'ממתין למוזיקה';
  }
  const background = safeParse(bridge.getBackgroundState(), { running: false });
  if (background.running) toast('סריקת AI עדיין פועלת ברקע');
  if (!localStorage.getItem('vybe_welcome_done') && tracks.length) {
    $('welcomeModal').classList.remove('hidden');
  }
}
window.vybeNativeReady = function() {
  if (!window.__vybeStarted) {
    window.__vybeStarted = true;
    startup();
  }
};
window.startBackgroundScan = backgroundScan;
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => window.vybeNativeReady());
} else {
  window.vybeNativeReady();
}

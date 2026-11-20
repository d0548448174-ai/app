// Lightweight onset-based tempo and beat-phase estimate used to feed LinkSeg.
export function estimateBeats(mono, sampleRate) {
  const fps = 100;
  const hop = Math.max(1, Math.round(sampleRate / fps));
  const win = hop * 2;
  const frameCount = Math.floor((mono.length - win) / hop);
  if (frameCount < 100) return { beats: [], bpm: 0 };
  const env = new Float32Array(frameCount);
  for (let f = 0; f < frameCount; f++) {
    let energy = 0;
    const start = f * hop;
    for (let i = 0; i < win; i++) {
      const value = mono[start + i];
      energy += value * value;
    }
    env[f] = Math.log1p(energy);
  }
  const novelty = new Float32Array(frameCount);
  for (let i = 1; i < frameCount; i++) {
    const delta = env[i] - env[i - 1];
    novelty[i] = delta > 0 ? delta : 0;
  }
  const minLag = Math.round(60 * fps / 200);
  const maxLag = Math.round(60 * fps / 60);
  let bestLag = minLag;
  let bestScore = -Infinity;
  for (let lag = minLag; lag <= maxLag; lag++) {
    let correlation = 0;
    for (let i = lag; i < novelty.length; i++) correlation += novelty[i] * novelty[i - lag];
    const bpm = 60 * fps / lag;
    const logRatio = Math.log(bpm / 120);
    const prior = Math.exp(-0.5 * Math.pow(logRatio / 0.5, 2));
    const score = correlation * prior;
    if (score > bestScore) {
      bestScore = score;
      bestLag = lag;
    }
  }
  let bestOffset = 0;
  let bestPhase = -Infinity;
  for (let offset = 0; offset < bestLag; offset++) {
    let sum = 0;
    for (let i = offset; i < novelty.length; i += bestLag) sum += novelty[i];
    if (sum > bestPhase) {
      bestPhase = sum;
      bestOffset = offset;
    }
  }
  const beats = [];
  for (let i = bestOffset; i < novelty.length; i += bestLag) beats.push(i / fps);
  return { beats, bpm: 60 * fps / bestLag };
}

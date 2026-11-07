import { defineConfig } from 'vite';
import { mkdir, copyFile } from 'node:fs/promises';
import { resolve } from 'node:path';

export default defineConfig({
  base: './',
  build: { target: 'es2022', assetsInlineLimit: 0 },
  worker: { format: 'es' },
  plugins: [{
    name: 'copy-ffmpeg-core',
    async configResolved(config) {
      // Copy the pinned FFmpeg WebAssembly runtime into the site before Vite
      // copies its public directory into dist.
      const source = resolve(config.root, 'node_modules/@ffmpeg/core/dist/umd');
      const target = resolve(config.root, 'public/ffmpeg');
      await mkdir(target, { recursive: true });
      await copyFile(resolve(source, 'ffmpeg-core.js'), resolve(target, 'ffmpeg-core.js'));
      await copyFile(resolve(source, 'ffmpeg-core.wasm'), resolve(target, 'ffmpeg-core.wasm'));
    }
  }]
});

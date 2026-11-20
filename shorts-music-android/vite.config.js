import { defineConfig } from 'vite';
import { cpSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectDir = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  root: resolve(projectDir, 'web'),
  base: './',
  build: {
    outDir: resolve(projectDir, 'app/src/main/assets'),
    emptyOutDir: true,
    sourcemap: false,
    target: 'es2020'
  },
  plugins: [{
    name: 'copy-onnx-wasm-runtime',
    closeBundle() {
      const source = resolve(projectDir, 'node_modules/onnxruntime-web/dist');
      const target = resolve(projectDir, 'app/src/main/assets');
      if (!existsSync(source)) throw new Error('onnxruntime-web distribution was not installed');
      mkdirSync(target, { recursive: true });
      for (const file of [
        'ort-wasm-simd-threaded.wasm',
        'ort-wasm-simd.wasm',
        'ort-wasm.wasm',
        'ort-wasm-simd-threaded.jsep.wasm',
        'ort-wasm-simd-threaded.asyncify.wasm'
      ]) {
        const from = resolve(source, file);
        if (existsSync(from)) cpSync(from, resolve(target, file));
      }
    }
  }]
});

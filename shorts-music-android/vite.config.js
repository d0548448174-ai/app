import { defineConfig } from 'vite';
import { cpSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
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
      const runtimeFiles = readdirSync(source).filter(name =>
        name.startsWith('ort-wasm') && (name.endsWith('.wasm') || name.endsWith('.mjs'))
      );
      if (!runtimeFiles.some(name => name.endsWith('.wasm'))) {
        throw new Error('ONNX Runtime WebAssembly files were not found');
      }
      for (const file of runtimeFiles) cpSync(resolve(source, file), resolve(target, file));
    }
  }]
});

/**
 * copy-wasm.js — runs automatically after `npm install` (postinstall).
 *
 * Copies WASM runtime files from node_modules into public/ so they can be
 * served as static assets and cached by the Service Worker for offline use.
 *
 * Files copied:
 *   node_modules/onnxruntime-web/dist/*.wasm   → public/
 *   node_modules/@mediapipe/tasks-vision/wasm/* → public/mediapipe/
 */

const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')

function copyDir(src, destDir) {
  if (!fs.existsSync(src)) {
    console.warn(`[postinstall] WARNING: source not found, skipping: ${src}`)
    return
  }
  fs.mkdirSync(destDir, { recursive: true })
  for (const file of fs.readdirSync(src)) {
    const srcFile = path.join(src, file)
    if (fs.statSync(srcFile).isFile()) {
      fs.copyFileSync(srcFile, path.join(destDir, file))
      console.log(`  copied ${file}`)
    }
  }
}

function copyGlob(src, pattern, destDir) {
  if (!fs.existsSync(src)) {
    console.warn(`[postinstall] WARNING: source not found, skipping: ${src}`)
    return
  }
  fs.mkdirSync(destDir, { recursive: true })
  for (const file of fs.readdirSync(src).filter((f) => pattern.test(f))) {
    fs.copyFileSync(path.join(src, file), path.join(destDir, file))
    console.log(`  copied ${file}`)
  }
}

let warnings = 0

console.log('[postinstall] Copying ONNX Runtime WASM files...')
try {
  copyGlob(
    path.join(root, 'node_modules/onnxruntime-web/dist'),
    /ort-wasm-simd-threaded.*\.(wasm|mjs)$/,
    path.join(root, 'public'),
  )
} catch (err) {
  console.warn('[postinstall] WARNING: could not copy ORT WASM files:', err.message)
  warnings++
}

console.log('[postinstall] Copying MediaPipe Vision WASM files...')
try {
  copyDir(
    path.join(root, 'node_modules/@mediapipe/tasks-vision/wasm'),
    path.join(root, 'public/mediapipe'),
  )
} catch (err) {
  console.warn('[postinstall] WARNING: could not copy MediaPipe WASM files:', err.message)
  warnings++
}

if (warnings > 0) {
  console.warn(`[postinstall] Completed with ${warnings} warning(s). Run 'npm install' again if WASM files are missing at runtime.`)
} else {
  console.log('[postinstall] Done.')
}

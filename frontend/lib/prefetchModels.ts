/**
 * prefetchModels — silently warms the Service Worker cache with all face
 * recognition assets immediately after the manager logs in.
 *
 * Called once from ManagerLayout when auth is confirmed (ready = true).
 * All fetches run at low priority in the background; errors are swallowed
 * so this never blocks the UI or breaks anything when offline.
 *
 * The Service Worker CacheFirst handler intercepts each fetch:
 *   - Already cached → returns instantly, no network request
 *   - Not cached    → fetches from network, stores in cache
 */

const PREFETCH_FILES = [
  // MediaPipe Vision WASM (all variants — device picks the right one at runtime)
  '/mediapipe/vision_wasm_internal.js',
  '/mediapipe/vision_wasm_internal.wasm',
  '/mediapipe/vision_wasm_module_internal.js',
  '/mediapipe/vision_wasm_module_internal.wasm',
  '/mediapipe/vision_wasm_nosimd_internal.js',
  '/mediapipe/vision_wasm_nosimd_internal.wasm',

  // ONNX Runtime WASM + JS glue (only variants actually loaded by ORT wasm provider)
  '/ort-wasm-simd-threaded.wasm',
  '/ort-wasm-simd-threaded.mjs',
  '/ort-wasm-simd-threaded.jsep.wasm',
  '/ort-wasm-simd-threaded.jsep.mjs',

  // Face recognition models
  '/models/face_landmarker.task',
  '/models/mobilefacenet.onnx',
]

export function prefetchModels(): void {
  if (typeof window === 'undefined') return
  if (!('serviceWorker' in navigator)) return

  // Fire-and-forget: fetch all files in parallel at background priority.
  // Each individual failure (file missing, offline) is silently ignored.
  for (const url of PREFETCH_FILES) {
    fetch(url).catch(() => {})
  }
}

/**
 * Face alignment: transforms a raw video frame into a normalized
 * 112×112 face crop suitable for MobileFaceNet/ArcFace inference.
 *
 * Uses eye landmark indices from MediaPipe FaceLandmarker (478-point model):
 *   Left iris centre  → landmark 468
 *   Right iris centre → landmark 473
 *
 * Standard ArcFace canonical eye positions (for 112×112 output):
 *   Left eye  → (38.2946, 51.6963)
 *   Right eye → (73.5318, 51.5014)
 */

import type { NormalizedLandmark } from '@mediapipe/tasks-vision'

const OUT_SIZE = 112

// Canonical ArcFace eye positions in the 112×112 output space
const CANONICAL_LEFT_EYE: [number, number] = [38.2946, 51.6963]
const CANONICAL_RIGHT_EYE: [number, number] = [73.5318, 51.5014]

// MediaPipe 478-landmark iris centre indices
const LEFT_IRIS_CENTER = 468
const RIGHT_IRIS_CENTER = 473

// Reused across frames to avoid per-frame allocation overhead
let _canvas: HTMLCanvasElement | null = null
let _ctx: CanvasRenderingContext2D | null = null

function getCanvas(): CanvasRenderingContext2D | null {
  if (!_ctx) {
    _canvas = document.createElement('canvas')
    _canvas.width = OUT_SIZE
    _canvas.height = OUT_SIZE
    _ctx = _canvas.getContext('2d')
  }
  return _ctx
}

/** Called by useFaceApi when the pipeline is destroyed to release the shared canvas. */
export function resetAlignmentCanvas(): void {
  _canvas = null
  _ctx = null
}

export function alignFace(
  source: HTMLVideoElement | ImageBitmap,
  landmarks: NormalizedLandmark[],
  sourceWidth: number,
  sourceHeight: number,
): ImageData | null {
  if (landmarks.length < 478 || sourceWidth === 0 || sourceHeight === 0) return null

  const ctx = getCanvas()
  if (!ctx) return null

  // Pixel coordinates of iris centres
  const lx = landmarks[LEFT_IRIS_CENTER].x * sourceWidth
  const ly = landmarks[LEFT_IRIS_CENTER].y * sourceHeight
  const rx = landmarks[RIGHT_IRIS_CENTER].x * sourceWidth
  const ry = landmarks[RIGHT_IRIS_CENTER].y * sourceHeight

  // Compute the similarity transform (scale + rotation + translation)
  // that maps [lx,ly] → CANONICAL_LEFT_EYE and [rx,ry] → CANONICAL_RIGHT_EYE
  const [a, b, tx, ty] = similarityTransform(
    lx, ly, rx, ry,
    CANONICAL_LEFT_EYE[0], CANONICAL_LEFT_EYE[1],
    CANONICAL_RIGHT_EYE[0], CANONICAL_RIGHT_EYE[1],
  )

  ctx.setTransform(1, 0, 0, 1, 0, 0)
  ctx.clearRect(0, 0, OUT_SIZE, OUT_SIZE)
  ctx.setTransform(a, b, -b, a, tx, ty)
  ctx.drawImage(source, 0, 0)
  ctx.setTransform(1, 0, 0, 1, 0, 0)

  return ctx.getImageData(0, 0, OUT_SIZE, OUT_SIZE)
}

/**
 * Returns [a, b, tx, ty] for the 2-D similarity transform:
 *   x' =  a*x - b*y + tx
 *   y' =  b*x + a*y + ty
 * that maps (x1,y1)→(cx1,cy1) and (x2,y2)→(cx2,cy2).
 */
function similarityTransform(
  x1: number, y1: number,
  x2: number, y2: number,
  cx1: number, cy1: number,
  cx2: number, cy2: number,
): [number, number, number, number] {
  const dx = x2 - x1
  const dy = y2 - y1
  const cdx = cx2 - cx1
  const cdy = cy2 - cy1

  const denom = dx * dx + dy * dy
  if (denom === 0) return [1, 0, 0, 0]

  const a = (dx * cdx + dy * cdy) / denom
  const b = (dx * cdy - dy * cdx) / denom

  const tx = cx1 - a * x1 + b * y1
  const ty = cy1 - b * x1 - a * y1

  return [a, b, tx, ty]
}

/**
 * Converts a 112×112 ImageData into a Float32Array suitable for
 * MobileFaceNet / ArcFace ONNX input (shape [1, 3, 112, 112], CHW, range [-1, 1]).
 */
export function imageDataToTensor(imageData: ImageData): Float32Array {
  const { data } = imageData // RGBA, Uint8ClampedArray
  const size = OUT_SIZE * OUT_SIZE
  const tensor = new Float32Array(3 * size)

  for (let i = 0; i < size; i++) {
    const r = data[i * 4]
    const g = data[i * 4 + 1]
    const b = data[i * 4 + 2]
    // Normalise to [-1, 1]
    tensor[i] = (r - 127.5) / 128
    tensor[size + i] = (g - 127.5) / 128
    tensor[2 * size + i] = (b - 127.5) / 128
  }

  return tensor
}

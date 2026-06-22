/**
 * useFaceApi — MediaPipe FaceLandmarker + MobileFaceNet ONNX
 *
 * Drop-in replacement for the old face-api.js hook.
 * Public interface: { state, detectDescriptor, detectWithMesh, buildMatcher }
 *
 * Pipeline:
 *   1. MediaPipe FaceLandmarker  → detects face + 478 landmarks
 *   2. faceAlignment.alignFace   → affine-warps face to 112×112 canonical crop
 *   3. MobileFaceNet ONNX        → produces Float32Array[512] embedding
 *   4. FaceMatcher (cosine sim)  → 1:N matching against enrolled workers
 */

import { useCallback, useEffect, useState } from 'react'
import * as ort from 'onnxruntime-web'
import type { NormalizedLandmark } from '@mediapipe/tasks-vision'
import type { WorkerRecord } from '@/lib/db'
import { alignFace, imageDataToTensor, resetAlignmentCanvas } from '@/lib/faceAlignment'
import { FaceMatcher } from '@/lib/faceMatcher'

export type { NormalizedLandmark }

type FaceLandmarkerType = import('@mediapipe/tasks-vision').FaceLandmarker

export type FaceApiState = 'idle' | 'loading' | 'ready' | 'error'

export interface DetectionWithMesh {
  descriptor: Float32Array | null
  landmarks: NormalizedLandmark[] | null
}

interface MeshConnections {
  tesselation: ReadonlyArray<{ start: number; end: number }>
  rightEye: ReadonlyArray<{ start: number; end: number }>
  leftEye: ReadonlyArray<{ start: number; end: number }>
  rightEyebrow: ReadonlyArray<{ start: number; end: number }>
  leftEyebrow: ReadonlyArray<{ start: number; end: number }>
  faceOval: ReadonlyArray<{ start: number; end: number }>
  lips: ReadonlyArray<{ start: number; end: number }>
}

const MODEL_BASE = '/models'
const LANDMARKER_MODEL = `${MODEL_BASE}/face_landmarker.task`
const RECOGNITION_MODEL = `${MODEL_BASE}/mobilefacenet.onnx`
const MEDIAPIPE_WASM_PATH = '/mediapipe/'

// ── Module-level singletons ───────────────────────────────────────────────────
// Shared across all hook instances. Only destroyed when the last consumer unmounts.

let landmarker: FaceLandmarkerType | null = null
let ortSession: ort.InferenceSession | null = null
let loadPromise: Promise<void> | null = null
let meshConnections: MeshConnections | null = null

// Reference count — only close GPU resources when last consumer unmounts
let consumerCount = 0

/** Returns MediaPipe face mesh connection arrays (available after models load). */
export function getMeshConnections(): MeshConnections | null {
  return meshConnections
}

async function loadModels(): Promise<void> {
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    ort.env.wasm.wasmPaths = '/'
    ort.env.wasm.numThreads = 1

    const { FaceLandmarker, FilesetResolver } = await import('@mediapipe/tasks-vision')
    const filesetResolver = await FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_PATH)

    // Capture static connection arrays for face mesh drawing
    meshConnections = {
      tesselation: FaceLandmarker.FACE_LANDMARKS_TESSELATION,
      rightEye: FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE,
      leftEye: FaceLandmarker.FACE_LANDMARKS_LEFT_EYE,
      rightEyebrow: FaceLandmarker.FACE_LANDMARKS_RIGHT_EYEBROW,
      leftEyebrow: FaceLandmarker.FACE_LANDMARKS_LEFT_EYEBROW,
      faceOval: FaceLandmarker.FACE_LANDMARKS_FACE_OVAL,
      lips: FaceLandmarker.FACE_LANDMARKS_LIPS,
    }

    // Try GPU delegate first, fall back to CPU
    let newLandmarker: FaceLandmarkerType
    try {
      newLandmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
        baseOptions: { modelAssetPath: LANDMARKER_MODEL, delegate: 'GPU' },
        runningMode: 'VIDEO',
        numFaces: 1,
        outputFaceBlendshapes: false,
        outputFacialTransformationMatrixes: false,
      })
    } catch {
      newLandmarker = await FaceLandmarker.createFromOptions(filesetResolver, {
        baseOptions: { modelAssetPath: LANDMARKER_MODEL, delegate: 'CPU' },
        runningMode: 'VIDEO',
        numFaces: 1,
        outputFaceBlendshapes: false,
        outputFacialTransformationMatrixes: false,
      })
    }

    // If ONNX fails, close the already-created landmarker before rethrowing
    let newSession: ort.InferenceSession
    try {
      newSession = await ort.InferenceSession.create(RECOGNITION_MODEL, {
        executionProviders: ['wasm'],
        graphOptimizationLevel: 'all',
      })
    } catch (err) {
      newLandmarker.close()
      throw err
    }

    landmarker = newLandmarker
    ortSession = newSession
  })()

  loadPromise.catch(() => { loadPromise = null })
  return loadPromise
}

function destroyModels() {
  // Only reset loadPromise when models are fully loaded.
  // If landmarker is null, a load is still in-flight — resetting loadPromise
  // would cause the next mount to start a parallel load and leak GPU resources.
  if (landmarker) {
    landmarker.close()
    landmarker = null
    ortSession = null
    loadPromise = null
    meshConnections = null
    resetAlignmentCanvas()
  }
}

// ── Shared detection logic ────────────────────────────────────────────────────

async function _runDetection(video: HTMLVideoElement): Promise<DetectionWithMesh> {
  if (!landmarker || !ortSession) return { descriptor: null, landmarks: null }
  if (video.readyState < 2 || video.videoWidth === 0 || video.videoHeight === 0) {
    return { descriptor: null, landmarks: null }
  }

  const result = landmarker.detectForVideo(video, performance.now())
  if (!result.faceLandmarks || result.faceLandmarks.length === 0) {
    return { descriptor: null, landmarks: null }
  }

  const rawLandmarks = result.faceLandmarks[0]

  const imageData = alignFace(video, rawLandmarks, video.videoWidth, video.videoHeight)
  if (!imageData) return { descriptor: null, landmarks: rawLandmarks }

  const inputTensorData = imageDataToTensor(imageData)
  const inputName = ortSession.inputNames[0]
  const outputName = ortSession.outputNames[0]

  const inputTensor = new ort.Tensor('float32', inputTensorData, [1, 3, 112, 112])
  let results: Awaited<ReturnType<typeof ortSession.run>>
  try {
    results = await ortSession.run({ [inputName]: inputTensor })
  } finally {
    inputTensor.dispose()
  }

  const outputTensor = results[outputName]
  // Copy output data to JS heap before disposing the WASM-backed tensor.
  // Without the copy, the returned Float32Array would be a view into WASM
  // memory that may be invalidated on the next inference call.
  const embedding = new Float32Array(outputTensor.data as Float32Array)

  // Dispose all output tensors to release WASM memory.
  for (const tensor of Object.values(results)) {
    tensor.dispose()
  }

  return { descriptor: embedding, landmarks: rawLandmarks }
}

// ── Hook ─────────────────────────────────────────────────────────────────────

export function useFaceApi() {
  const [state, setState] = useState<FaceApiState>('idle')

  useEffect(() => {
    let cancelled = false
    consumerCount++

    if (landmarker && ortSession) {
      setState('ready')
    } else {
      setState('loading')
      loadModels()
        .then(() => { if (!cancelled) setState('ready') })
        .catch(() => { if (!cancelled) setState('error') })
    }

    return () => {
      cancelled = true
      consumerCount = Math.max(0, consumerCount - 1)
      if (consumerCount === 0) {
        destroyModels()
      }
    }
  }, [])

  const detectWithMesh = useCallback(async (
    video: HTMLVideoElement
  ): Promise<DetectionWithMesh> => {
    if (state !== 'ready') return { descriptor: null, landmarks: null }
    return _runDetection(video)
  }, [state])

  const detectDescriptor = useCallback(async (
    video: HTMLVideoElement
  ): Promise<Float32Array | null> => {
    if (state !== 'ready') return null
    const { descriptor } = await _runDetection(video)
    return descriptor
  }, [state])

  const buildMatcher = useCallback((workers: WorkerRecord[]) => {
    if (state !== 'ready') return null

    const entries = workers
      .filter((w) => w.descriptor !== null && w.descriptor.length === 512)
      .map((w) => ({
        id: w.id,
        descriptor: new Float32Array(w.descriptor!),
      }))

    if (entries.length === 0) return null
    return new FaceMatcher(entries, 0.45)
  }, [state])

  return { state, detectDescriptor, detectWithMesh, buildMatcher }
}

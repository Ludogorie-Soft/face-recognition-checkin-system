import { useCallback, useEffect, useRef, useState } from 'react'
import type { WorkerRecord } from '@/lib/db'

let faceapi: typeof import('face-api.js') | null = null

export type FaceApiState = 'idle' | 'loading' | 'ready' | 'error'

const MODEL_URL = '/models'

export function useFaceApi() {
  const [state, setState] = useState<FaceApiState>('idle')
  const loaded = useRef(false)

  useEffect(() => {
    if (loaded.current) return
    setState('loading')
    import('face-api.js').then(async (mod) => {
      faceapi = mod
      await Promise.all([
        faceapi.nets.ssdMobilenetv1.loadFromUri(MODEL_URL),
        faceapi.nets.faceLandmark68Net.loadFromUri(MODEL_URL),
        faceapi.nets.faceRecognitionNet.loadFromUri(MODEL_URL),
      ])
      loaded.current = true
      setState('ready')
    }).catch(() => setState('error'))
  }, [])

  // Detect a single face descriptor from a video element
  const detectDescriptor = useCallback(async (
    video: HTMLVideoElement
  ): Promise<Float32Array | null> => {
    if (!faceapi || state !== 'ready') return null
    const detection = await faceapi
      .detectSingleFace(video, new faceapi.SsdMobilenetv1Options({ minConfidence: 0.5 }))
      .withFaceLandmarks()
      .withFaceDescriptor()
    return detection ? detection.descriptor : null
  }, [state])

  // Build a 1:N FaceMatcher from cached workers
  const buildMatcher = useCallback((workers: WorkerRecord[]) => {
    if (!faceapi || state !== 'ready') return null
    const labeled = workers
      .filter((w) => w.descriptor !== null && w.descriptor.length === 128)
      .map((w) => new faceapi!.LabeledFaceDescriptors(
        w.id,
        [new Float32Array(w.descriptor!)]
      ))
    if (labeled.length === 0) return null
    return new faceapi!.FaceMatcher(labeled, 0.6)
  }, [state])

  return { state, detectDescriptor, buildMatcher }
}

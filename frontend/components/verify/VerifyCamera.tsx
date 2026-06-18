'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslations } from 'next-intl'
import {
  Camera, CheckCircle2, XCircle, MapPin,
  Loader2, UserX, RefreshCw,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useFaceApi } from '@/hooks/useFaceApi'
import { useGeoLocation } from '@/hooks/useGeoLocation'
import { ManualOverrideModal } from './ManualOverrideModal'
import type { SiteInfo, WorkerRecord } from '@/lib/db'

interface DetectionResult {
  workerId: string
  workerName: string
  confidence: number
}

interface Props {
  site: SiteInfo
  workers: WorkerRecord[]
  sessionLog: Map<string, 'CHECK_IN' | 'CHECK_OUT'>
  onRecord: (params: {
    workerId: string
    workerName: string
    type: 'CHECK_IN' | 'CHECK_OUT'
    lat: number
    lng: number
    locationValid: boolean
    faceConfidence: number | null
    manualOverride: boolean
  }) => Promise<void>
}

export function VerifyCamera({ site, workers, sessionLog, onRecord }: Props) {
  const t = useTranslations('verify')

  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const detectingRef = useRef(false)
  const missCountRef = useRef(0)
  const MISS_THRESHOLD = 3

  const [cameraActive, setCameraActive] = useState(false)
  const [detected, setDetected] = useState<DetectionResult | null>(null)
  const [faceVisible, setFaceVisible] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [manualOpen, setManualOpen] = useState(false)
  const [cameraError, setCameraError] = useState<string | null>(null)

  const { state: faceState, detectDescriptor, buildMatcher } = useFaceApi()
  const geo = useGeoLocation(site)

  const matcher = useMemo(
    () => buildMatcher(workers),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [workers, faceState]
  )

  // ── Camera ──────────────────────────────────────────────────────────────────

  const startCamera = useCallback(async () => {
    setCameraError(null)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
        await videoRef.current.play()
      }
      setCameraActive(true)
    } catch {
      setCameraError('Камерата не може да бъде стартирана')
    }
  }, [])

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    if (videoRef.current) videoRef.current.srcObject = null
    setCameraActive(false)
    setDetected(null)
    setFaceVisible(false)
    missCountRef.current = 0
  }, [])

  useEffect(() => () => stopCamera(), [stopCamera])

  // ── Detection loop ───────────────────────────────────────────────────────────

  useEffect(() => {
    if (!cameraActive || faceState !== 'ready' || confirming) return

    let active = true

    const runDetection = async () => {
      while (active && !confirming) {
        if (!videoRef.current || detectingRef.current) {
          await sleep(200)
          continue
        }

        detectingRef.current = true
        try {
          const descriptor = await detectDescriptor(videoRef.current)
          if (!active) break

          if (!descriptor) {
            missCountRef.current++
            if (missCountRef.current >= MISS_THRESHOLD) {
              setFaceVisible(false)
              setDetected(null)
            }
          } else if (matcher) {
            const best = matcher.findBestMatch(descriptor)
            if (best.label !== 'unknown') {
              const worker = workers.find((w) => w.id === best.label)
              if (worker) {
                missCountRef.current = 0
                setFaceVisible(true)
                setDetected({
                  workerId: worker.id,
                  workerName: worker.name,
                  confidence: Math.round((1 - best.distance) * 100),
                })
              }
            } else {
              missCountRef.current++
              setFaceVisible(descriptor !== null)
              if (missCountRef.current >= MISS_THRESHOLD) {
                setDetected(null)
              }
            }
          } else {
            missCountRef.current++
            if (missCountRef.current >= MISS_THRESHOLD) {
              setFaceVisible(false)
              setDetected(null)
            }
          }
        } finally {
          detectingRef.current = false
        }

        await sleep(500)
      }
    }

    runDetection()
    return () => { active = false }
  }, [cameraActive, faceState, confirming, detectDescriptor, matcher, workers])

  // ── Confirm ──────────────────────────────────────────────────────────────────

  const handleConfirm = useCallback(async (type: 'CHECK_IN' | 'CHECK_OUT') => {
    if (!detected) return
    setConfirming(true)
    try {
      await onRecord({
        workerId: detected.workerId,
        workerName: detected.workerName,
        type,
        lat: geo.lat,
        lng: geo.lng,
        locationValid: geo.locationValid,
        faceConfidence: detected.confidence,
        manualOverride: false,
      })
      setDetected(null)
      setFaceVisible(false)
      missCountRef.current = 0
    } finally {
      setConfirming(false)
    }
  }, [detected, geo, onRecord])

  const handleManualRecord = useCallback(async (workerId: string, type: 'CHECK_IN' | 'CHECK_OUT') => {
    const worker = workers.find((w) => w.id === workerId)
    if (!worker) return
    setConfirming(true)
    try {
      await onRecord({
        workerId,
        workerName: worker.name,
        type,
        lat: geo.lat,
        lng: geo.lng,
        locationValid: geo.locationValid,
        faceConfidence: null,
        manualOverride: true,
      })
    } finally {
      setConfirming(false)
    }
  }, [workers, geo, onRecord])

  // ── Derived ──────────────────────────────────────────────────────────────────

  const workersWithFace = workers.filter((w) => w.descriptor !== null)
  const lastAction = detected ? sessionLog.get(detected.workerId) : undefined

  const ovalStroke = detected
    ? '#4ade80'
    : faceVisible
    ? '#facc15'
    : 'rgba(255,255,255,0.65)'

  // ── Render ───────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col h-full">

      {/* ── Camera area ── */}
      <div className="relative flex-1 bg-black overflow-hidden min-h-0">
        <video
          ref={videoRef}
          playsInline
          muted
          className="w-full h-full object-cover scale-x-[-1]"
        />

        {/* Camera off state */}
        {!cameraActive && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-6 bg-background">
            {faceState === 'loading' && (
              <div className="flex flex-col items-center gap-3 text-muted-foreground">
                <Loader2 size={40} className="animate-spin text-primary" />
                <p className="text-sm">{t('modelsLoading')}</p>
              </div>
            )}
            {faceState === 'error' && (
              <p className="text-sm text-destructive">{t('modelsError')}</p>
            )}
            {(faceState === 'ready' || faceState === 'idle') && (
              <>
                <Camera size={64} className="text-muted-foreground opacity-30" />
                {cameraError && (
                  <p className="text-sm text-destructive px-4 text-center">{cameraError}</p>
                )}
                <Button
                  onClick={startCamera}
                  size="lg"
                  className="gap-2"
                  disabled={faceState !== 'ready'}
                >
                  <Camera size={18} />
                  {t('startCamera')}
                </Button>
                {workersWithFace.length === 0 && (
                  <p className="text-xs text-muted-foreground">{t('noDescriptors')}</p>
                )}
              </>
            )}
          </div>
        )}

        {/* Face guide oval — purely visual */}
        {cameraActive && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <svg viewBox="0 0 200 240" className="w-[45%] max-w-[220px]" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <mask id="oval-mask">
                  <rect width="200" height="240" fill="white" />
                  <ellipse cx="100" cy="120" rx="82" ry="108" fill="black" />
                </mask>
              </defs>
              <rect width="200" height="240" fill="black" fillOpacity="0.35" mask="url(#oval-mask)" />
              <ellipse cx="100" cy="120" rx="82" ry="108" fill="none" stroke={ovalStroke} strokeWidth="2.5" />
            </svg>
          </div>
        )}

        {/* Top bar: GPS + close */}
        {cameraActive && (
          <div className="absolute top-0 inset-x-0 flex items-center justify-between px-4 py-3 bg-gradient-to-b from-black/70 to-transparent">
            <div className={`flex items-center gap-1.5 text-xs font-medium ${
              geo.loading ? 'text-white/70'
              : geo.locationValid ? 'text-green-400'
              : 'text-amber-400'
            }`}>
              <MapPin size={13} />
              {geo.loading ? t('locationLoading') : geo.locationValid ? t('locationValid') : t('locationInvalid')}
            </div>
            <button onClick={stopCamera} className="text-white/70 hover:text-white transition-colors">
              <XCircle size={22} />
            </button>
          </div>
        )}
      </div>

      {/* ── Bottom panel ── */}
      {cameraActive && (
        <div className="shrink-0 bg-card border-t border-border px-4 pt-4 pb-safe-4 flex flex-col gap-3">
          {confirming ? (
            <div className="flex items-center justify-center py-4">
              <Loader2 size={24} className="animate-spin text-primary" />
            </div>
          ) : detected ? (
            <>
              {/* Worker info */}
              <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-muted/60">
                <CheckCircle2 size={22} className="text-green-500 shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="font-semibold text-foreground truncate">{detected.workerName}</p>
                  <p className="text-xs text-muted-foreground">{t('confidence', { value: detected.confidence })}</p>
                </div>
                {lastAction === 'CHECK_IN' && (
                  <span className="shrink-0 text-xs font-medium px-2 py-1 rounded-full bg-green-500/15 text-green-600 dark:text-green-400">
                    {t('alreadyCheckedIn')}
                  </span>
                )}
                {lastAction === 'CHECK_OUT' && (
                  <span className="shrink-0 text-xs font-medium px-2 py-1 rounded-full bg-red-500/15 text-red-600 dark:text-red-400">
                    {t('alreadyCheckedOut')}
                  </span>
                )}
              </div>

              {/* Smart action buttons */}
              {lastAction === 'CHECK_IN' ? (
                <Button
                  className="h-14 text-base font-bold w-full bg-red-600 hover:bg-red-700"
                  onClick={() => handleConfirm('CHECK_OUT')}
                >
                  {t('checkOut')}
                </Button>
              ) : lastAction === 'CHECK_OUT' ? (
                <Button
                  className="h-14 text-base font-bold w-full bg-green-600 hover:bg-green-700"
                  onClick={() => handleConfirm('CHECK_IN')}
                >
                  {t('checkIn')}
                </Button>
              ) : (
                <div className="grid grid-cols-2 gap-3">
                  <Button
                    className="h-14 text-base font-bold bg-green-600 hover:bg-green-700"
                    onClick={() => handleConfirm('CHECK_IN')}
                  >
                    {t('checkIn')}
                  </Button>
                  <Button
                    className="h-14 text-base font-bold bg-red-600 hover:bg-red-700"
                    onClick={() => handleConfirm('CHECK_OUT')}
                  >
                    {t('checkOut')}
                  </Button>
                </div>
              )}
            </>
          ) : faceVisible ? (
            <div className="flex items-center justify-center gap-2 py-3 text-amber-500">
              <UserX size={18} />
              <span className="text-sm font-medium">{t('unknownFace')}</span>
            </div>
          ) : (
            <div className="flex items-center justify-center gap-2 py-3 text-muted-foreground">
              <RefreshCw size={14} className="animate-spin" />
              <span className="text-sm">{t('scanning')}</span>
            </div>
          )}

          {/* Manual override */}
          <Button
            variant="ghost"
            size="sm"
            className="text-muted-foreground gap-2 w-full"
            onClick={() => setManualOpen(true)}
            disabled={confirming}
          >
            <UserX size={15} />
            {t('manualConfirm')}
          </Button>
        </div>
      )}

      <ManualOverrideModal
        open={manualOpen}
        onClose={() => setManualOpen(false)}
        workers={workers}
        onConfirm={handleManualRecord}
      />
    </div>
  )
}

function sleep(ms: number) {
  return new Promise<void>((r) => setTimeout(r, ms))
}

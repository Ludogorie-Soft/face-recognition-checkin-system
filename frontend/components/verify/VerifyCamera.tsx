'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslations } from 'next-intl'
import {
  Camera, CheckCircle2, XCircle, MapPin, MapPinOff,
  Loader2, UserX, RefreshCw, Users,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useFaceApi } from '@/hooks/useFaceApi'
import { useGeoLocation } from '@/hooks/useGeoLocation'
import { distanceToZoneMeters } from '@/lib/geo'
import { ManualOverrideModal } from './ManualOverrideModal'
import type { SiteInfo, WorkerRecord } from '@/lib/db'

interface DetectionResult {
  workerId: string
  workerName: string
  confidence: number
}

// A worker's last known status, plus whether it was confirmed by the server
// (authoritative) or is only a local guess.
export interface SessionEntry {
  type: 'CHECK_IN' | 'CHECK_OUT'
  serverConfirmed: boolean
}

interface Props {
  sites: Map<string, SiteInfo>
  workers: WorkerRecord[]
  sessionLog: Map<string, SessionEntry>
  /** Called when a new worker is recognised, so the page can refresh the server status. */
  onWorkerDetected?: (workerId: string) => void
  onRecord: (params: {
    workerId: string
    workerName: string
    siteId: string
    type: 'CHECK_IN' | 'CHECK_OUT'
    lat: number
    lng: number
    accuracyMeters: number
    locationValid: boolean
    faceConfidence: number | null
    manualOverride: boolean
  }) => Promise<void>
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Mirrors attendance.geo-accuracy-cap-meters on the server. */
const ACCURACY_CAP_METERS = 50

// For a detected worker (who may be assigned to multiple sites), work out which site the device is
// standing in. This is only a hint — the server re-resolves it from the coordinates — but it decides
// what the operator sees, so it has to be the nearest site rather than an arbitrary one.
//
// It used to fall back to the worker's FIRST assignment whenever no zone matched, which filed scans
// at sites 76 and 163 km away and showed them as "outside zone" until someone fixed each by hand.
function resolveWorkerSite(
  workerId: string,
  workers: WorkerRecord[],
  sites: Map<string, SiteInfo>,
  lat: number,
  lng: number,
  accuracy: number,
): { siteId: string; locationValid: boolean } | null {
  const entries = workers.filter((w) => w.id === workerId)
  if (entries.length === 0) return null

  // No fix yet — useGeoLocation reports 0/0 until the first position arrives. Measuring from there
  // would rank the sites by their distance to the Gulf of Guinea.
  if (lat === 0 && lng === 0) {
    return { siteId: entries[0].siteId, locationValid: false }
  }

  // Zones here go down to 10 m while a phone fix is routinely ±10-20 m, so a zone is widened by the
  // fix's own error margin — capped, or a bad reading would validate the next town over. Mirrors
  // attendance.geo-accuracy-cap-meters on the server, which has the final say anyway.
  const tolerance = Math.min(Math.max(accuracy, 0), ACCURACY_CAP_METERS)

  let best: { siteId: string; distance: number } | null = null
  for (const entry of entries) {
    const site = sites.get(entry.siteId)
    if (!site) continue
    const distance = distanceToZoneMeters(lat, lng, site) - tolerance
    if (!best || distance < best.distance) best = { siteId: entry.siteId, distance }
  }

  if (!best) return { siteId: entries[0].siteId, locationValid: false }
  return { siteId: best.siteId, locationValid: best.distance <= 0 }
}

// ── Component ──────────────────────────────────────────────────────────────────

export function VerifyCamera({ sites, workers, sessionLog, onRecord, onWorkerDetected }: Props) {
  const t = useTranslations('verify')

  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const detectingRef = useRef(false)
  const missCountRef = useRef(0)
  const notifiedWorkerRef = useRef<string | null>(null)
  // Hard lock: setConfirming is async, so a fast double-tap could otherwise enter
  // handleConfirm twice and record the same action twice.
  const submittingRef = useRef(false)
  const onWorkerDetectedRef = useRef(onWorkerDetected)
  useEffect(() => { onWorkerDetectedRef.current = onWorkerDetected }, [onWorkerDetected])
  const MISS_THRESHOLD = 3

  const [cameraActive, setCameraActive] = useState(false)
  const [ovalSize, setOvalSize] = useState(45)  // % of camera width
  const [detected, setDetected] = useState<DetectionResult | null>(null)
  const [faceVisible, setFaceVisible] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [manualOpen, setManualOpen] = useState(false)
  const [cameraError, setCameraError] = useState<string | null>(null)

  const { state: faceState, detectWithMesh, buildMatcher } = useFaceApi()
  const geo = useGeoLocation()

  // Workers deduplicated by id — for ManualOverrideModal which shows a flat list
  const uniqueWorkers = useMemo(() => {
    const seen = new Set<string>()
    return workers.filter((w) => {
      if (seen.has(w.id)) return false
      seen.add(w.id)
      return true
    })
  }, [workers])

  const matcher = useMemo(
    () => buildMatcher(uniqueWorkers),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [uniqueWorkers, faceState],
  )

  // Geo info for the currently detected worker: which site are they at + in-zone?
  // We always compute this (even while GPS is loading) so the confirm button is
  // never blocked by a slow GPS fix — locationValid will simply be false until
  // a real position arrives.
  const detectedWorkerGeo = useMemo(() => {
    if (!detected) return null
    return resolveWorkerSite(detected.workerId, workers, sites, geo.lat, geo.lng, geo.accuracy)
  }, [detected, geo.lat, geo.lng, geo.accuracy, workers, sites])

  // ── Camera ───────────────────────────────────────────────────────────────────

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
      setCameraError(t('cameraError'))
    }
  }, [t])

  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    if (videoRef.current) videoRef.current.srcObject = null
    setCameraActive(false)
    setDetected(null)
    setFaceVisible(false)
    missCountRef.current = 0
  }, [])

  useEffect(() => () => stopCamera(), [stopCamera])

  // ── Detection loop ────────────────────────────────────────────────────────────

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
          const { descriptor } = await detectWithMesh(videoRef.current)
          if (!active) break

          if (!descriptor) {
            missCountRef.current++
            if (missCountRef.current >= MISS_THRESHOLD) {
              setFaceVisible(false)
              setDetected(null)
              notifiedWorkerRef.current = null
            }
          } else if (matcher) {
            const best = matcher.findBestMatch(descriptor)
            if (best.label !== 'unknown') {
              const worker = uniqueWorkers.find((w) => w.id === best.label)
              if (worker) {
                missCountRef.current = 0
                setFaceVisible(true)
                setDetected({
                  workerId: worker.id,
                  workerName: worker.name,
                  confidence: Math.round((1 - best.distance) * 100),
                })
                // Ask the page to refresh this worker's server status so the button
                // direction reflects what the server actually knows.
                if (notifiedWorkerRef.current !== worker.id) {
                  notifiedWorkerRef.current = worker.id
                  onWorkerDetectedRef.current?.(worker.id)
                }
              }
            } else {
              missCountRef.current++
              setFaceVisible(true)
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
        } catch {
          // detection error — continue loop
        } finally {
          detectingRef.current = false
        }

        await sleep(500)
      }
    }

    runDetection()
    return () => { active = false }
  }, [cameraActive, faceState, confirming, detectWithMesh, matcher, uniqueWorkers])

  // ── Confirm ───────────────────────────────────────────────────────────────────

  const handleConfirm = useCallback(
    async (type: 'CHECK_IN' | 'CHECK_OUT') => {
      if (!detected || !detectedWorkerGeo) return
      if (submittingRef.current) return
      submittingRef.current = true
      setConfirming(true)
      try {
        await onRecord({
          workerId: detected.workerId,
          workerName: detected.workerName,
          siteId: detectedWorkerGeo.siteId,
          type,
          lat: geo.lat,
          lng: geo.lng,
          accuracyMeters: geo.accuracy,
          locationValid: detectedWorkerGeo.locationValid,
          faceConfidence: detected.confidence,
          manualOverride: false,
        })
        setDetected(null)
        setFaceVisible(false)
        missCountRef.current = 0
      } finally {
        submittingRef.current = false
        setConfirming(false)
      }
    },
    [detected, detectedWorkerGeo, geo.lat, geo.lng, geo.accuracy, onRecord],
  )

  const handleManualRecord = useCallback(
    async (workerId: string, type: 'CHECK_IN' | 'CHECK_OUT') => {
      const geoInfo = resolveWorkerSite(workerId, workers, sites, geo.lat, geo.lng, geo.accuracy)
      if (!geoInfo) return
      const workerRecord = workers.find((w) => w.id === workerId)
      if (!workerRecord) return
      if (submittingRef.current) return
      submittingRef.current = true
      setConfirming(true)
      try {
        await onRecord({
          workerId,
          workerName: workerRecord.name,
          siteId: geoInfo.siteId,
          type,
          lat: geo.lat,
          lng: geo.lng,
          accuracyMeters: geo.accuracy,
          locationValid: geoInfo.locationValid,
          faceConfidence: null,
          manualOverride: true,
        })
      } finally {
        submittingRef.current = false
        setConfirming(false)
      }
    },
    [workers, sites, geo.lat, geo.lng, geo.accuracy, onRecord],
  )

  // ── Derived ───────────────────────────────────────────────────────────────────

  const workersWithFace = workers.some((w) => w.descriptor !== null)
  const detectedEntry = detected && detectedWorkerGeo
    ? sessionLog.get(detected.workerId)
    : undefined
  const lastAction = detectedEntry?.type

  const ovalStroke = detected
    ? '#4ade80'
    : faceVisible
    ? '#facc15'
    : 'rgba(255,255,255,0.65)'

  // Top bar geo status
  const geoBarColor = geo.permissionDenied
    ? 'text-red-400'
    : geo.loading
    ? 'text-white/70'
    : geo.error
    ? 'text-red-400'
    : detectedWorkerGeo?.locationValid
    ? 'text-green-400'
    : detected && detectedWorkerGeo && !detectedWorkerGeo.locationValid
    ? 'text-amber-400'
    : 'text-white/70'

  const accuracyLabel = geo.accuracy > 0 ? ` ±${Math.round(geo.accuracy)}m` : ''

  const geoBarLabel = geo.permissionDenied
    ? t('locationError')
    : geo.loading
    ? t('locationLoading')
    : geo.error
    ? t('locationError')
    : detectedWorkerGeo?.locationValid
    ? `${t('locationValid')}${accuracyLabel}`
    : detected && detectedWorkerGeo && !detectedWorkerGeo.locationValid
    ? `${t('locationInvalid')}${accuracyLabel}`
    : `${t('locationActive')}${accuracyLabel}`

  // ── Render ────────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col h-full relative">

      {/* ── Location permission blocked overlay ── */}
      {geo.permissionDenied && (
        <div className="absolute inset-0 z-50 flex flex-col items-center justify-center gap-6 bg-background px-8 text-center">
          <div className="rounded-2xl bg-destructive/10 p-6">
            <MapPinOff size={48} className="text-destructive mx-auto" />
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="text-lg font-bold text-foreground">
              {t('locationPermissionTitle')}
            </h3>
            <p className="text-sm text-muted-foreground">
              {t('locationPermissionBody')}
            </p>
          </div>

          <ol className="flex flex-col gap-2 text-left w-full max-w-xs">
            {([
              t('locationPermissionStep1'),
              t('locationPermissionStep2'),
              t('locationPermissionStep3'),
            ] as string[]).map((step, i) => (
              <li key={i} className="flex items-start gap-3">
                <span className="shrink-0 mt-0.5 w-6 h-6 rounded-full bg-primary/15 text-primary text-xs font-bold flex items-center justify-center">
                  {i + 1}
                </span>
                <span className="text-sm text-foreground">{step}</span>
              </li>
            ))}
          </ol>

          <div className="flex flex-col items-center gap-3 mt-2">
            <Button onClick={() => window.location.reload()} className="gap-2">
              <RefreshCw size={16} />
              {t('locationPermissionRetry')}
            </Button>
            <button
              onClick={() => setManualOpen(true)}
              className="text-xs text-muted-foreground underline underline-offset-2 hover:text-foreground transition-colors"
            >
              {t('manualConfirm')}
            </button>
          </div>
        </div>
      )}

      {/* ── Camera area ──
          Когато е активна: фиксирана на ~52% от родителската височина,
          оставяйки bottom panel-а видим без скролване.
          Когато е неактивна: заема цялото налично място (flex-1)
          за да са центрирани "Start camera" / loading state-овете. */}
      <div className={`relative bg-black overflow-hidden ${
        cameraActive ? 'h-[52%] shrink-0 grow-0' : 'flex-1 min-h-0'
      }`}>
        <video
          ref={videoRef}
          playsInline
          muted
          className="w-full h-full object-cover scale-x-[-1]"
        />

        {/* Camera off state */}
        {!cameraActive && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-6 bg-background px-8">
            {faceState === 'loading' && (
              <div className="flex flex-col items-center gap-4 text-muted-foreground">
                <div className="rounded-2xl bg-muted/40 p-6">
                  <Loader2 size={36} className="animate-spin text-primary" />
                </div>
                <p className="text-sm">{t('modelsLoading')}</p>
              </div>
            )}
            {faceState === 'error' && (
              <div className="flex flex-col items-center gap-3">
                <div className="rounded-2xl bg-destructive/10 p-6">
                  <Camera size={36} className="text-destructive" />
                </div>
                <p className="text-sm text-destructive text-center">{t('modelsError')}</p>
              </div>
            )}
            {(faceState === 'ready' || faceState === 'idle') && (
              <>
                <div className="flex flex-col items-center gap-4">
                  <div className="rounded-2xl bg-muted/40 p-7">
                    <Camera size={44} className="text-muted-foreground opacity-60" />
                  </div>
                  {cameraError && (
                    <p className="text-sm text-destructive text-center">{cameraError}</p>
                  )}
                </div>
                <Button
                  onClick={startCamera}
                  size="lg"
                  className="gap-2 px-8"
                  disabled={faceState !== 'ready'}
                >
                  <Camera size={18} />
                  {t('startCamera')}
                </Button>
                {!workersWithFace && (
                  <p className="text-xs text-muted-foreground text-center">{t('noDescriptors')}</p>
                )}
              </>
            )}
          </div>
        )}

        {/* Face guide oval + size controls */}
        {cameraActive && (
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <svg
              viewBox="0 0 200 240"
              style={{ width: `${ovalSize}%`, maxWidth: '380px', minWidth: '120px' }}
              xmlns="http://www.w3.org/2000/svg"
            >
              <defs>
                <mask id="oval-mask">
                  <rect width="200" height="240" fill="white" />
                  <ellipse cx="100" cy="120" rx="82" ry="108" fill="black" />
                </mask>
              </defs>
              <rect width="200" height="240" fill="black" fillOpacity="0.35" mask="url(#oval-mask)" />
              <ellipse cx="100" cy="120" rx="82" ry="108" fill="none" stroke={ovalStroke} strokeWidth="2.5" />
            </svg>

            {/* Size controls — bottom-centre of camera area */}
            <div className="absolute bottom-3 flex items-center gap-2 pointer-events-auto">
              <button
                onClick={() => setOvalSize((s) => Math.max(20, s - 5))}
                className="w-7 h-7 rounded-full bg-black/50 text-white/80 hover:text-white hover:bg-black/70 flex items-center justify-center text-base leading-none transition-colors"
                aria-label="Намали"
              >
                −
              </button>
              <button
                onClick={() => setOvalSize((s) => Math.min(85, s + 5))}
                className="w-7 h-7 rounded-full bg-black/50 text-white/80 hover:text-white hover:bg-black/70 flex items-center justify-center text-base leading-none transition-colors"
                aria-label="Уголеми"
              >
                +
              </button>
            </div>
          </div>
        )}

        {/* Top bar: GPS status + close */}
        {cameraActive && (
          <div className="absolute top-0 inset-x-0 flex items-center justify-between px-4 py-3 bg-gradient-to-b from-black/70 to-transparent">
            <div className={`flex items-center gap-1.5 text-xs font-medium ${geoBarColor}`}>
              <MapPin size={13} />
              {geoBarLabel}
            </div>
            <button onClick={stopCamera} className="text-white/70 hover:text-white transition-colors">
              <XCircle size={22} />
            </button>
          </div>
        )}
      </div>

      {/* ── Bottom panel ──
          flex-1 min-h-0: заема оставащите ~48% от екрана след камерата.
          overflow-y-auto: scroll само на много малки устройства ако все пак не се събира. */}
      {cameraActive && (
        <div className="flex-1 min-h-0 overflow-y-auto bg-card border-t border-border flex flex-col">

          <div className="px-4 pt-3 pb-4 flex flex-col gap-3">
            {confirming ? (
              <div className="flex items-center justify-center py-5">
                <Loader2 size={28} className="animate-spin text-primary" />
              </div>
            ) : detected ? (
              <>
                <div className="flex items-center gap-4 px-4 py-4 rounded-2xl bg-green-500/10 border border-green-500/30 dark:bg-green-500/10 dark:border-green-500/25">
                  <CheckCircle2 size={32} className="text-green-500 shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="font-bold text-foreground text-xl truncate leading-tight">{detected.workerName}</p>
                    <p className="text-xs text-muted-foreground mt-1">{t('confidence', { value: detected.confidence })}</p>
                  </div>
                  {lastAction === 'CHECK_IN' && (
                    <span className="shrink-0 text-xs font-medium px-2.5 py-1 rounded-full bg-green-500/20 text-green-700 dark:text-green-400">
                      {t('alreadyCheckedIn')}
                    </span>
                  )}
                  {lastAction === 'CHECK_OUT' && (
                    <span className="shrink-0 text-xs font-medium px-2.5 py-1 rounded-full bg-red-500/15 text-red-600 dark:text-red-400">
                      {t('alreadyCheckedOut')}
                    </span>
                  )}
                </div>

                {lastAction === 'CHECK_IN' ? (
                  <Button
                    className="h-14 text-base font-bold w-full bg-red-600 hover:bg-red-700 dark:bg-red-600 dark:hover:bg-red-700"
                    onClick={() => handleConfirm('CHECK_OUT')}
                    disabled={confirming || !detectedWorkerGeo || geo.loading}
                  >
                    {geo.loading ? (
                      <span className="flex items-center gap-2"><Loader2 size={16} className="animate-spin" />{t('locationLoading')}</span>
                    ) : t('checkOut')}
                  </Button>
                ) : (
                  <Button
                    className="h-14 text-base font-bold w-full bg-green-600 hover:bg-green-700 dark:bg-green-600 dark:hover:bg-green-700"
                    onClick={() => handleConfirm('CHECK_IN')}
                    disabled={confirming || !detectedWorkerGeo || geo.loading}
                  >
                    {geo.loading ? (
                      <span className="flex items-center gap-2"><Loader2 size={16} className="animate-spin" />{t('locationLoading')}</span>
                    ) : t('checkIn')}
                  </Button>
                )}
              </>
            ) : faceVisible ? (
              <div className="flex items-center justify-center gap-2 py-4 text-amber-500">
                <UserX size={18} />
                <span className="text-sm font-medium">{t('unknownFace')}</span>
              </div>
            ) : (
              <div className="flex items-center justify-center gap-2 py-4 text-muted-foreground">
                <RefreshCw size={13} className="animate-spin" />
                <span className="text-sm">{t('scanning')}</span>
              </div>
            )}

            <Button
              variant="ghost"
              size="sm"
              className="text-muted-foreground hover:text-foreground gap-2 w-full"
              onClick={() => setManualOpen(true)}
              disabled={confirming}
            >
              <Users size={14} />
              {t('manualConfirm')}
            </Button>
          </div>
        </div>
      )}

      <ManualOverrideModal
        open={manualOpen}
        onClose={() => setManualOpen(false)}
        workers={uniqueWorkers}
        onConfirm={handleManualRecord}
      />
    </div>
  )
}

function sleep(ms: number) {
  return new Promise<void>((r) => setTimeout(r, ms))
}

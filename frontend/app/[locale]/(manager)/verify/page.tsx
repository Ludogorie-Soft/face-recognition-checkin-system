'use client'

import { useState, useCallback, useEffect, useMemo, useRef } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { MapPin, RefreshCw } from 'lucide-react'
import { useSites } from '@/hooks/useSites'
import { useSiteSync } from '@/hooks/useSiteSync'
import { VerifyCamera, type SessionEntry } from '@/components/verify/VerifyCamera'
import { SyncLoader } from '@/components/verify/SyncLoader'
import { db } from '@/lib/db'
import { getDeviceId, APP_VERSION } from '@/lib/deviceId'
import { toLocalISOString } from '@/lib/utils'
import api from '@/lib/axios'
import type { SiteInfo, WorkerRecord, LocalSessionStatus } from '@/lib/db'

type Phase = 'syncing' | 'camera' | 'error'

// sessionLog is keyed by `${workerId}:${siteId}` so a worker assigned to multiple
// sites keeps an independent status per site.
const statusKey = (workerId: string, siteId: string) => `${workerId}:${siteId}`

export default function VerifyPage() {
  const t = useTranslations('verify')
  const { data: apiSites, isLoading: sitesLoading, isError: sitesError } = useSites()
  const { syncAll } = useSiteSync()

  const [phase, setPhase] = useState<Phase>('syncing')
  const [sitesMap, setSitesMap] = useState<Map<string, SiteInfo>>(new Map())
  const [workers, setWorkers] = useState<WorkerRecord[]>([])
  const [resyncing, setResyncing] = useState(false)
  const [sessionLog, setSessionLog] = useState<Map<string, SessionEntry>>(new Map())
  const [cachedSiteIds, setCachedSiteIds] = useState<string[]>([])
  const [syncProgress, setSyncProgress] = useState<{ done: number; total: number } | null>(null)
  const syncStartedRef = useRef(false)
  const lastStatusRefreshRef = useRef(0)


  // When offline and the API request fails, fall back to site IDs stored in IndexedDB
  useEffect(() => {
    if ((sitesError || (!sitesLoading && !apiSites?.length)) && !navigator.onLine) {
      db.siteInfo.toArray().then((rows) => setCachedSiteIds(rows.map((r) => r.id)))
    }
  }, [sitesError, sitesLoading, apiSites])

  const siteIds = useMemo(
    () => (apiSites?.length ? apiSites.map((s) => s.id) : cachedSiteIds),
    [apiSites, cachedSiteIds],
  )

  // ── Session log ────────────────────────────────────────────────────────────
  // Rebuilds sessionLog from the authoritative server status + local persisted
  // status (offline fallback) + today's db.pending (newest, wins). Called on
  // initial sync AND periodically / on tab re-focus so a long-open kiosk terminal
  // never keeps a stale (e.g. previous-day) status in memory.
  const rebuildSessionLog = useCallback(async (ids: string[]) => {
    if (ids.length === 0) return
    // Local date — the server uses its own local day, and pending timestamps are
    // local; a UTC slice here would drop records around midnight (BG is UTC+2/3).
    const today = toLocalISOString().slice(0, 10) // 'YYYY-MM-DD' local
    const next = new Map<string, SessionEntry>()

    const todayResults = await Promise.allSettled(
      ids.map((id) =>
        api.get<Record<string, string>>('/api/attendance/today', { params: { siteId: id } }),
      ),
    )

    const confirmedRows: LocalSessionStatus[] = []
    for (let i = 0; i < ids.length; i++) {
      const id = ids[i]
      const r = todayResults[i]
      if (r.status === 'fulfilled') {
        Object.entries(r.value.data).forEach(([wId, type]) => {
          const t = type as 'CHECK_IN' | 'CHECK_OUT'
          next.set(statusKey(wId, id), { type: t, serverConfirmed: true })
          confirmedRows.push({ workerId: wId, siteId: id, type: t, date: today, serverConfirmed: true })
        })
      } else {
        // Offline: fall back to the last locally persisted status for this site.
        const cached = await db.sessionStatus.where('siteId').equals(id).toArray()
        cached
          .filter((s) => s.date === today)
          .forEach((s) => next.set(statusKey(s.workerId, s.siteId), { type: s.type, serverConfirmed: s.serverConfirmed }))
      }
    }
    if (confirmedRows.length) await db.sessionStatus.bulkPut(confirmedRows)

    // Only today's pending records — stale records from previous days must not
    // pollute today's session log. Local pending is always an unconfirmed guess.
    const pending = await db.pending
      .filter((r) => r.recordedAt.startsWith(today))
      .toArray()
    pending.sort((a, b) => a.recordedAt.localeCompare(b.recordedAt))
    pending.forEach((r) => next.set(statusKey(r.workerId, r.siteId), { type: r.type, serverConfirmed: false }))
    setSessionLog(next)
  }, [])

  // ── Sync all sites ───────────────────────────────────────────────────────────

  const runSync = useCallback(
    async (ids: string[]) => {
      setPhase('syncing')
      setSyncProgress({ done: 0, total: ids.length })
      try {
        const { sitesMap: sm, workers: ws } = await syncAll(ids, (done, total) => {
          setSyncProgress({ done, total })
        })

        setSitesMap(sm)
        setWorkers(ws)
        await rebuildSessionLog(ids)

        setPhase('camera')
      } catch {
        setPhase('error')
      }
    },
    [syncAll, rebuildSessionLog],
  )

  // Auto-trigger sync once site IDs are available
  useEffect(() => {
    if (syncStartedRef.current || sitesLoading || siteIds.length === 0) return
    syncStartedRef.current = true
    runSync(siteIds)
  }, [siteIds, sitesLoading, runSync])

  // Keep the terminal's status fresh: refresh every 5 min and whenever the tab
  // regains focus. Prevents an always-on kiosk from acting on a stale in-memory
  // status (root cause of the previous-day carry-over check-out anomalies).
  useEffect(() => {
    if (phase !== 'camera' || siteIds.length === 0) return
    const refresh = () => {
      if (navigator.onLine && document.visibilityState === 'visible') {
        rebuildSessionLog(siteIds).catch(() => {})
      }
    }
    const interval = setInterval(refresh, 5 * 60 * 1000)
    document.addEventListener('visibilitychange', refresh)
    window.addEventListener('focus', refresh)
    return () => {
      clearInterval(interval)
      document.removeEventListener('visibilitychange', refresh)
      window.removeEventListener('focus', refresh)
    }
  }, [phase, siteIds, rebuildSessionLog])

  const handleResync = useCallback(async () => {
    if (resyncing || siteIds.length === 0) return
    setResyncing(true)
    try {
      const { sitesMap: sm, workers: ws } = await syncAll(siteIds)
      setSitesMap(sm)
      setWorkers(ws)
      toast.success(t('syncSuccess', { count: ws.length }))
    } catch {
      toast.error(t('syncError'))
    } finally {
      setResyncing(false)
    }
  }, [siteIds, syncAll, t, resyncing])

  // ── Record attendance ────────────────────────────────────────────────────────

  const handleRecord = useCallback(
    async (params: {
      workerId: string
      workerName: string
      siteId: string
      type: 'CHECK_IN' | 'CHECK_OUT'
      lat: number
      lng: number
      locationValid: boolean
      faceConfidence: number | null
      manualOverride: boolean
    }) => {
      const recordedAt = toLocalISOString()
      await db.pending.add({
        clientEventId: crypto.randomUUID(),
        createdOffline: !navigator.onLine,
        clientDeviceId: getDeviceId(),
        appVersion: APP_VERSION,
        workerId: params.workerId,
        siteId: params.siteId,
        type: params.type,
        lat: params.lat,
        lng: params.lng,
        locationValid: params.locationValid,
        faceConfidence: params.faceConfidence,
        manualOverride: params.manualOverride,
        recordedAt,
        status: 'pending',
      })

      // Persist locally so the decision survives a reload while offline.
      await db.sessionStatus.put({
        workerId: params.workerId,
        siteId: params.siteId,
        type: params.type,
        date: recordedAt.slice(0, 10),
        serverConfirmed: false,
      })

      setSessionLog((prev) =>
        new Map(prev).set(statusKey(params.workerId, params.siteId), {
          type: params.type,
          serverConfirmed: false,
        }),
      )
      toast.success(t('success', { name: params.workerName }))
    },
    [t],
  )

  // Refresh the server status when a worker is recognised, so the button direction is
  // right at the moment of the scan. Debounced, and skipped entirely when offline.
  const handleWorkerDetected = useCallback(() => {
    if (!navigator.onLine || siteIds.length === 0) return
    const now = Date.now()
    if (now - lastStatusRefreshRef.current < 10_000) return
    lastStatusRefreshRef.current = now
    rebuildSessionLog(siteIds).catch(() => {})
  }, [siteIds, rebuildSessionLog])

  // ── Render ───────────────────────────────────────────────────────────────────

  if (sitesLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <SyncLoader label={t('syncingAllSites')} />
      </div>
    )
  }

  if (!sitesLoading && siteIds.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-4 px-6 text-center">
        <div className="rounded-2xl bg-muted/40 p-6">
          <MapPin size={40} className="text-muted-foreground opacity-40 mx-auto" />
        </div>
        <p className="font-medium text-foreground">{t('noSites')}</p>
      </div>
    )
  }

  if (phase === 'syncing') {
    const progress = syncProgress && syncProgress.total > 0
      ? syncProgress.done / syncProgress.total
      : undefined
    const sublabel = syncProgress && syncProgress.total > 1
      ? `${syncProgress.done} / ${syncProgress.total}`
      : undefined
    return (
      <div className="flex-1 flex items-center justify-center">
        <SyncLoader
          progress={progress}
          label={t('syncingAllSites')}
          sublabel={sublabel}
        />
      </div>
    )
  }

  if (phase === 'error') {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-4 px-6 text-center">
        <p className="text-sm text-destructive">{t('syncAllError')}</p>
        <button
          onClick={() => {
            syncStartedRef.current = false
            runSync(siteIds)
          }}
          className="text-sm text-primary underline"
        >
          {t('sync')}
        </button>
      </div>
    )
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col">
      {/* Thin site-count + resync bar */}
      <div className="flex items-center justify-between px-3 py-2 bg-card border-b border-border shrink-0">
        <span className="text-xs text-muted-foreground font-medium">
          {t('sitesCount', { count: sitesMap.size })}
        </span>
        <button
          onClick={handleResync}
          disabled={resyncing}
          className="p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted transition-colors disabled:opacity-50"
          aria-label={t('sync')}
        >
          <RefreshCw size={16} className={resyncing ? 'animate-spin' : ''} />
        </button>
      </div>

      {/* flex-1 min-h-0 дава на VerifyCamera точно оставащата височина след site bar-а,
          така че h-full вътре да работи коректно без overflow */}
      <div className="flex-1 min-h-0">
        <VerifyCamera
          sites={sitesMap}
          workers={workers}
          onRecord={handleRecord}
          sessionLog={sessionLog}
          onWorkerDetected={handleWorkerDetected}
        />
      </div>
    </div>
  )
}

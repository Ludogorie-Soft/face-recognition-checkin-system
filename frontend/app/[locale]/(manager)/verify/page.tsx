'use client'

import { useState, useCallback, useEffect, useMemo, useRef } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { MapPin, RefreshCw } from 'lucide-react'
import { useSites } from '@/hooks/useSites'
import { useSiteSync } from '@/hooks/useSiteSync'
import { VerifyCamera } from '@/components/verify/VerifyCamera'
import { SyncLoader } from '@/components/verify/SyncLoader'
import { db } from '@/lib/db'
import { toLocalISOString } from '@/lib/utils'
import api from '@/lib/axios'
import type { SiteInfo, WorkerRecord } from '@/lib/db'

type Phase = 'syncing' | 'camera' | 'error'

export default function VerifyPage() {
  const t = useTranslations('verify')
  const { data: apiSites, isLoading: sitesLoading, isError: sitesError } = useSites()
  const { syncAll } = useSiteSync()

  const [phase, setPhase] = useState<Phase>('syncing')
  const [sitesMap, setSitesMap] = useState<Map<string, SiteInfo>>(new Map())
  const [workers, setWorkers] = useState<WorkerRecord[]>([])
  const [resyncing, setResyncing] = useState(false)
  const [sessionLog, setSessionLog] = useState<Map<string, 'CHECK_IN' | 'CHECK_OUT'>>(new Map())
  const [cachedSiteIds, setCachedSiteIds] = useState<string[]>([])
  const [syncProgress, setSyncProgress] = useState<{ done: number; total: number } | null>(null)
  const syncStartedRef = useRef(false)

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

        // Build sessionLog: merge API records (synced) + db.pending (unsynced).
        // Pending records are newer and take precedence.
        const initial = new Map<string, 'CHECK_IN' | 'CHECK_OUT'>()
        const todayResults = await Promise.allSettled(
          ids.map((id) =>
            api.get<Record<string, string>>('/api/attendance/today', { params: { siteId: id } }),
          ),
        )
        todayResults.forEach((r) => {
          if (r.status === 'fulfilled') {
            Object.entries(r.value.data).forEach(([wId, type]) => {
              initial.set(wId, type as 'CHECK_IN' | 'CHECK_OUT')
            })
          }
        })
        // Only today's pending records — stale records from previous days
        // must not pollute today's session log.
        const todayPrefix = new Date().toISOString().slice(0, 10) // 'YYYY-MM-DD'
        const pending = await db.pending
          .filter((r) => r.recordedAt.startsWith(todayPrefix))
          .toArray()
        pending.sort((a, b) => a.recordedAt.localeCompare(b.recordedAt))
        pending.forEach((r) => initial.set(r.workerId, r.type))
        setSessionLog(initial)

        setPhase('camera')
      } catch {
        setPhase('error')
      }
    },
    [syncAll],
  )

  // Auto-trigger sync once site IDs are available
  useEffect(() => {
    if (syncStartedRef.current || sitesLoading || siteIds.length === 0) return
    syncStartedRef.current = true
    runSync(siteIds)
  }, [siteIds, sitesLoading, runSync])

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
      await db.pending.add({
        workerId: params.workerId,
        siteId: params.siteId,
        type: params.type,
        lat: params.lat,
        lng: params.lng,
        locationValid: params.locationValid,
        faceConfidence: params.faceConfidence,
        manualOverride: params.manualOverride,
        recordedAt: toLocalISOString(),
        status: 'pending',
      })

      setSessionLog((prev) => new Map(prev).set(params.workerId, params.type))
      toast.success(t('success', { name: params.workerName }))
    },
    [t],
  )

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
        />
      </div>
    </div>
  )
}

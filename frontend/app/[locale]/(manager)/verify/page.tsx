'use client'

import { useState, useCallback, useEffect } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Loader2, MapPin, RefreshCw, ChevronLeft } from 'lucide-react'
import { useSites } from '@/hooks/useSites'
import { useSiteSync } from '@/hooks/useSiteSync'
import { VerifyCamera } from '@/components/verify/VerifyCamera'
import { db } from '@/lib/db'
import { toLocalISOString } from '@/lib/utils'
import api from '@/lib/axios'
import type { SiteInfo, WorkerRecord } from '@/lib/db'
import type { SiteResponse } from '@/types/site'

type Phase = 'select' | 'syncing' | 'camera'

export default function VerifyPage() {
  const t = useTranslations('verify')
  const tn = useTranslations('nav')
  const { data: apiSites, isLoading: sitesLoading, isError: sitesError } = useSites()
  const { sync } = useSiteSync()

  const [phase, setPhase] = useState<Phase>('select')
  const [site, setSite] = useState<SiteInfo | null>(null)
  const [workers, setWorkers] = useState<WorkerRecord[]>([])
  const [resyncing, setResyncing] = useState(false)
  const [sessionLog, setSessionLog] = useState<Map<string, 'CHECK_IN' | 'CHECK_OUT'>>(new Map())
  const [cachedSites, setCachedSites] = useState<SiteResponse[]>([])

  // When offline and API fails, fall back to IndexedDB
  useEffect(() => {
    if ((sitesError || (!sitesLoading && !apiSites?.length)) && !navigator.onLine) {
      db.siteInfo.toArray().then((rows) =>
        setCachedSites(
          rows.map((s) => ({
            ...s,
            address: null,
            active: true,
            createdAt: '',
            managers: [],
            workers: [],
          }))
        )
      )
    }
  }, [sitesError, sitesLoading, apiSites])

  const sites: SiteResponse[] = apiSites?.length ? apiSites : cachedSites

  // ── Site selection ──────────────────────────────────────────────────────────

  const handleSelectSite = useCallback(async (siteId: string) => {
    setPhase('syncing')
    try {
      const [syncResult, todayRes] = await Promise.allSettled([
        sync(siteId),
        api.get<Record<string, string>>('/api/attendance/today', { params: { siteId } }),
      ])

      if (syncResult.status === 'rejected') {
        setPhase('select')
        toast.error(t('syncError'))
        return
      }

      setSite(syncResult.value.site)
      setWorkers(syncResult.value.workers)

      // Build sessionLog from API (synced records) + db.pending (unsynced records)
      const initial = new Map<string, 'CHECK_IN' | 'CHECK_OUT'>()
      if (todayRes.status === 'fulfilled') {
        Object.entries(todayRes.value.data).forEach(([id, type]) => {
          initial.set(id, type as 'CHECK_IN' | 'CHECK_OUT')
        })
      }
      // Pending records are newer — they override the API data
      const pending = await db.pending.where('siteId').equals(siteId).sortBy('recordedAt')
      pending.forEach((r) => initial.set(r.workerId, r.type))
      setSessionLog(initial)

      setPhase('camera')
    } catch {
      setPhase('select')
      toast.error(t('syncError'))
    }
  }, [sync, t])

  const handleResync = useCallback(async () => {
    if (!site || resyncing) return
    setResyncing(true)
    try {
      const result = await sync(site.id)
      setWorkers(result.workers)
      toast.success(t('syncSuccess', { count: result.workers.length }))
    } catch {
      toast.error(t('syncError'))
    } finally {
      setResyncing(false)
    }
  }, [site, sync, t, resyncing])

  const handleBack = useCallback(() => {
    setSite(null)
    setWorkers([])
    setPhase('select')
    setSessionLog(new Map())
  }, [])

  // ── Record attendance ───────────────────────────────────────────────────────

  const handleRecord = useCallback(async (params: {
    workerId: string
    workerName: string
    type: 'CHECK_IN' | 'CHECK_OUT'
    lat: number
    lng: number
    locationValid: boolean
    faceConfidence: number | null
    manualOverride: boolean
  }) => {
    if (!site) return

    await db.pending.add({
      workerId: params.workerId,
      siteId: site.id,
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
  }, [site, t])

  // ── Render ──────────────────────────────────────────────────────────────────

  // Loading sites
  if (sitesLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <Loader2 size={32} className="animate-spin text-muted-foreground" />
      </div>
    )
  }

  // No assigned sites
  if (sites.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3 px-6 text-center">
        <MapPin size={48} className="text-muted-foreground opacity-30" />
        <p className="text-muted-foreground text-sm">{t('noSites')}</p>
      </div>
    )
  }

  // Syncing
  if (phase === 'syncing') {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-4">
        <Loader2 size={36} className="animate-spin text-primary" />
        <p className="text-sm text-muted-foreground">{t('sync')}...</p>
      </div>
    )
  }

  // Camera phase
  if (phase === 'camera' && site) {
    return (
      <div className="flex-1 flex flex-col">
        {/* Site header bar */}
        <div className="flex items-center justify-between px-3 py-2 bg-card border-b border-border shrink-0">
          <button
            onClick={handleBack}
            className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ChevronLeft size={18} />
            {t('selectSite')}
          </button>
          <span className="text-sm font-semibold text-foreground truncate max-w-[160px]">
            {site.name}
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

        <VerifyCamera
          site={site}
          workers={workers}
          onRecord={handleRecord}
          sessionLog={sessionLog}
        />
      </div>
    )
  }

  // Site selection
  return (
    <div className="flex-1 flex flex-col px-4 py-6 gap-4">
      <h2 className="text-base font-semibold text-foreground">{t('selectSite')}</h2>
      <ul className="flex flex-col gap-3">
        {sites.map((s) => (
          <li key={s.id}>
            <button
              onClick={() => handleSelectSite(s.id)}
              className="w-full text-left px-4 py-4 rounded-xl border border-border bg-card hover:border-primary hover:bg-primary/5 transition-colors"
            >
              <p className="font-semibold text-foreground text-sm">{s.name}</p>
              {s.address && (
                <p className="text-xs text-muted-foreground mt-0.5 truncate">{s.address}</p>
              )}
              <p className="text-xs text-muted-foreground mt-1">
                {s.workers.length} {tn('workers').toLowerCase()}
              </p>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

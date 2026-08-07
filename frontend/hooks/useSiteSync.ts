import { useCallback, useState } from 'react'
import api from '@/lib/axios'
import { db, type SiteInfo, type WorkerRecord } from '@/lib/db'

interface SiteSyncResponse {
  site: {
    id: string
    name: string
    lat: number
    lng: number
    radiusMeters: number
    workStartTime: string | null
    workEndTime: string | null
    checkpoints: Array<{
      id: string
      name: string | null
      lat: number
      lng: number
      radiusMeters: number
      lat2: number | null
      lng2: number | null
      checkpointType: 'POINT' | 'LINE'
    }>
  }
  workers: Array<{
    id: string
    name: string
    descriptor: number[] | null
  }>
}

export interface SyncResult {
  site: SiteInfo
  workers: WorkerRecord[]
}

export interface SyncAllResult {
  sitesMap: Map<string, SiteInfo>
  workers: WorkerRecord[]
}

export type SyncStatus = 'idle' | 'syncing' | 'done' | 'error' | 'offline'

// Module-level function — no state management, safe to call concurrently.
async function fetchSiteData(siteId: string): Promise<SyncResult> {
  if (navigator.onLine) {
    try {
      const { data } = await api.get<SiteSyncResponse>(`/api/sync/site/${siteId}`)

      const siteInfo: SiteInfo = {
        id: data.site.id,
        name: data.site.name,
        lat: data.site.lat,
        lng: data.site.lng,
        radiusMeters: data.site.radiusMeters,
        workStartTime: data.site.workStartTime,
        workEndTime: data.site.workEndTime,
        checkpoints: data.site.checkpoints ?? [],
      }

      const workers: WorkerRecord[] = data.workers.map((w) => ({
        id: w.id,
        siteId,
        name: w.name,
        descriptor: w.descriptor,
      }))

      await db.siteInfo.put(siteInfo)
      await db.workers.where('siteId').equals(siteId).delete()
      await db.workers.bulkPut(workers)

      return { site: siteInfo, workers }
    } catch {
      // Fall through to offline cache
    }
  }

  // Fallback: load from IndexedDB
  const cached = await db.siteInfo.get(siteId)
  const cachedWorkers = await db.workers.where('siteId').equals(siteId).toArray()

  if (cached) {
    return { site: { ...cached, checkpoints: cached.checkpoints ?? [] }, workers: cachedWorkers }
  }

  throw new Error('No cached data available')
}

export function useSiteSync() {
  const [status, setStatus] = useState<SyncStatus>('idle')

  const sync = useCallback(async (siteId: string): Promise<SyncResult> => {
    setStatus('syncing')
    try {
      const result = await fetchSiteData(siteId)
      setStatus('done')
      return result
    } catch {
      setStatus('error')
      throw new Error('No cached data available')
    }
  }, [])

  // Syncs all provided sites in parallel. Partial success is accepted —
  // if at least one site syncs, the result is returned. Throws only if
  // every single site fails (no data at all).
  // onProgress is called after each individual site completes so the caller
  // can show a determinate progress indicator.
  const syncAll = useCallback(async (
    siteIds: string[],
    onProgress?: (done: number, total: number) => void,
  ): Promise<SyncAllResult> => {
    setStatus('syncing')
    let done = 0
    const total = siteIds.length
    onProgress?.(0, total)
    const results = await Promise.allSettled(
      siteIds.map(async (id) => {
        const result = await fetchSiteData(id)
        onProgress?.(++done, total)
        return result
      }),
    )

    const sitesMap = new Map<string, SiteInfo>()
    const workers: WorkerRecord[] = []

    for (const r of results) {
      if (r.status === 'fulfilled') {
        sitesMap.set(r.value.site.id, r.value.site)
        workers.push(...r.value.workers)
      }
    }

    if (sitesMap.size === 0) {
      setStatus('error')
      throw new Error('No sites could be synced')
    }

    const allSucceeded = results.every((r) => r.status === 'fulfilled')
    setStatus(allSucceeded ? 'done' : 'offline')
    return { sitesMap, workers }
  }, [])

  return { sync, syncAll, status }
}

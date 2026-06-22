import { useState } from 'react'
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

export type SyncStatus = 'idle' | 'syncing' | 'done' | 'error' | 'offline'

export function useSiteSync() {
  const [status, setStatus] = useState<SyncStatus>('idle')

  const sync = async (siteId: string): Promise<SyncResult> => {
    setStatus('syncing')

    // Try API first when online
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
        }

        const workers: WorkerRecord[] = data.workers.map((w) => ({
          id: w.id,
          siteId: siteId,
          name: w.name,
          descriptor: w.descriptor,
        }))

        // Persist to IndexedDB
        await db.siteInfo.put(siteInfo)
        await db.workers.where('siteId').equals(siteId).delete()
        await db.workers.bulkPut(workers)

        setStatus('done')
        return { site: siteInfo, workers }
      } catch {
        // Fall through to offline cache
      }
    }

    // Fallback: load from IndexedDB
    const cached = await db.siteInfo.get(siteId)
    const cachedWorkers = await db.workers.where('siteId').equals(siteId).toArray()

    if (cached) {
      setStatus('offline')
      return { site: cached, workers: cachedWorkers }
    }

    setStatus('error')
    throw new Error('No cached data available')
  }

  return { sync, status }
}

'use client'

import { useEffect, useState, useRef, useCallback } from 'react'
import { WifiOff, RefreshCw } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { db } from '@/lib/db'
import api from '@/lib/axios'

export function SyncBanner() {
  const t = useTranslations('common')
  const [isOnline, setIsOnline] = useState(true)
  const [pendingCount, setPendingCount] = useState(0)
  const [syncing, setSyncing] = useState(false)
  // Ref-based guard so event handlers always see the current value without
  // re-registering listeners on every render.
  const syncingRef = useRef(false)

  const refreshCount = useCallback(async () => {
    const count = await db.pending.where('status').anyOf('pending', 'syncing').count()
    setPendingCount(count)
  }, [])

  const syncNow = useCallback(async () => {
    if (syncingRef.current || !navigator.onLine) return
    syncingRef.current = true
    setSyncing(true)

    try {
      const records = await db.pending.where('status').equals('pending').toArray()
      if (records.length === 0) return

      // Mark as 'syncing' before upload — prevents a second concurrent syncNow()
      // from picking up the same records.
      await db.pending
        .where('id').anyOf(records.map((r) => r.id!))
        .modify({ status: 'syncing' })

      // Group by siteId and upload per site. A per-site failure only affects
      // that site's records — other sites still get uploaded successfully.
      const bySite = records.reduce<Record<string, typeof records>>((acc, r) => {
        acc[r.siteId] ??= []
        acc[r.siteId].push(r)
        return acc
      }, {})

      const successIds: number[] = []
      for (const [siteId, siteRecords] of Object.entries(bySite)) {
        try {
          await api.post('/api/attendance/sync', { siteId, records: siteRecords })
          successIds.push(...siteRecords.map((r) => r.id!))
        } catch {
          // This site failed — its records remain 'syncing' and will be
          // reset to 'pending' in the finally block so they retry next time.
        }
      }

      if (successIds.length > 0) {
        await db.pending.bulkDelete(successIds)
      }
    } finally {
      // Reset any records still marked 'syncing' (failed uploads) → 'pending'
      await db.pending.where('status').equals('syncing').modify({ status: 'pending' })
      await refreshCount()
      syncingRef.current = false
      setSyncing(false)
    }
  }, [refreshCount])

  // ── Online / offline tracking ────────────────────────────────────────────────

  useEffect(() => {
    const onOnline = () => {
      setIsOnline(true)
      syncNow()
    }
    const onOffline = () => setIsOnline(false)

    window.addEventListener('online', onOnline)
    window.addEventListener('offline', onOffline)
    setIsOnline(navigator.onLine)

    return () => {
      window.removeEventListener('online', onOnline)
      window.removeEventListener('offline', onOffline)
    }
  }, [syncNow])

  // ── Sync when tab regains focus ──────────────────────────────────────────────

  useEffect(() => {
    const onVisible = () => {
      if (!document.hidden) syncNow()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => document.removeEventListener('visibilitychange', onVisible)
  }, [syncNow])

  // ── Initial count + 30 s fallback interval (count-only, no upload) ───────────

  useEffect(() => {
    refreshCount()
    const id = setInterval(refreshCount, 30_000)
    return () => clearInterval(id)
  }, [refreshCount])

  // ── Auto-sync whenever online and pending records exist ──────────────────────

  useEffect(() => {
    if (isOnline && pendingCount > 0) syncNow()
  }, [isOnline, pendingCount, syncNow])

  // ── Render ───────────────────────────────────────────────────────────────────

  if (isOnline && pendingCount === 0) return null

  return (
    <div
      className={`flex items-center gap-2 px-4 py-2 text-sm font-medium ${
        !isOnline
          ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400'
          : 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
      }`}
    >
      {!isOnline ? (
        <>
          <WifiOff size={14} />
          <span>{t('offline')}</span>
          {pendingCount > 0 && (
            <span className="ml-1 opacity-75">
              · {t('pendingSync', { count: pendingCount })}
            </span>
          )}
        </>
      ) : (
        <>
          <RefreshCw size={14} className={syncing ? 'animate-spin' : ''} />
          <span>
            {syncing ? t('syncing') : t('pendingSync', { count: pendingCount })}
          </span>
        </>
      )}
    </div>
  )
}

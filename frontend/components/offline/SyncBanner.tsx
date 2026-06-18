'use client'

import { useEffect, useState } from 'react'
import { WifiOff, RefreshCw } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { db } from '@/lib/db'
import api from '@/lib/axios'

export function SyncBanner() {
  const t = useTranslations('common')
  const [isOnline, setIsOnline] = useState(true)
  const [pendingCount, setPendingCount] = useState(0)
  const [syncing, setSyncing] = useState(false)

  useEffect(() => {
    const updateOnline = () => setIsOnline(navigator.onLine)
    window.addEventListener('online', updateOnline)
    window.addEventListener('offline', updateOnline)
    updateOnline()
    return () => {
      window.removeEventListener('online', updateOnline)
      window.removeEventListener('offline', updateOnline)
    }
  }, [])

  useEffect(() => {
    const checkCount = async () => {
      const count = await db.pending.where('status').equals('pending').count()
      setPendingCount(count)
    }
    checkCount()  // run immediately on mount
    const interval = setInterval(checkCount, 3000)
    return () => clearInterval(interval)
  }, [])

  // Auto-sync when online or when pending records appear
  useEffect(() => {
    if (isOnline && pendingCount > 0) {
      syncNow()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOnline, pendingCount])

  const syncNow = async () => {
    if (syncing) return
    setSyncing(true)
    try {
      const records = await db.pending.where('status').equals('pending').toArray()
      if (records.length === 0) return

      // Group by siteId
      const bySite = records.reduce<Record<string, typeof records>>((acc, r) => {
        if (!acc[r.siteId]) acc[r.siteId] = []
        acc[r.siteId].push(r)
        return acc
      }, {})

      for (const [siteId, siteRecords] of Object.entries(bySite)) {
        await api.post('/api/attendance/sync', { siteId, records: siteRecords })
        await db.pending.bulkDelete(siteRecords.map((r) => r.id!))
      }

      setPendingCount(0)
    } catch {
      // Will retry next cycle
    } finally {
      setSyncing(false)
    }
  }

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
          <span>{syncing ? t('syncing') : t('pendingSync', { count: pendingCount })}</span>
        </>
      )}
    </div>
  )
}

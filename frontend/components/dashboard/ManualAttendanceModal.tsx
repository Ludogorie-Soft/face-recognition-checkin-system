'use client'

import { useState, useCallback, useEffect } from 'react'
import { useTranslations } from 'next-intl'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { LogIn, LogOut, X, Loader2, Users, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { useSites } from '@/hooks/useSites'
import api from '@/lib/axios'

// ── Types ─────────────────────────────────────────────────────────────────────

interface WorkerDayStatus {
  workerId: string
  workerName: string
  attendanceId: string | null
  lastType: 'CHECK_IN' | 'CHECK_OUT' | null
}

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// ── Component ─────────────────────────────────────────────────────────────────

interface Props {
  open: boolean
  onClose: () => void
}

export function ManualAttendanceModal({ open, onClose }: Props) {
  const t = useTranslations('dashboard')
  const tc = useTranslations('common')
  const { data: sites = [] } = useSites()
  const [siteId, setSiteId] = useState('')
  const [date, setDate] = useState(todayStr())
  const [queryKey, setQueryKey] = useState(0)
  const [loadingAction, setLoadingAction] = useState<string | null>(null)

  // Reset state every time the modal opens so stale data from a previous
  // session doesn't linger.
  useEffect(() => {
    if (open) {
      setSiteId('')
      setDate(todayStr())
      setQueryKey(0)
      setLoadingAction(null)
    }
  }, [open])

  const QK = ['manual-attendance', siteId, date, queryKey]

  const { data: workers = [], isFetching } = useQuery<WorkerDayStatus[]>({
    queryKey: QK,
    queryFn: () =>
      api.get('/api/attendance/workers-status', { params: { siteId, date } })
        .then((r) => r.data),
    enabled: !!siteId && queryKey > 0,
    placeholderData: (prev) => prev,
  })

  const load = useCallback(() => {
    if (!siteId) { toast.warning(t('selectSiteFirst')); return }
    setQueryKey((k) => k + 1)
  }, [siteId, t])

  const handleMark = useCallback(async (worker: WorkerDayStatus, type: 'CHECK_IN' | 'CHECK_OUT') => {
    setLoadingAction(worker.workerId + type)
    try {
      await api.post('/api/attendance/manual', { workerId: worker.workerId, siteId, type, date })
      toast.success(t('markedSuccess', { name: worker.workerName }))
      setQueryKey((k) => k + 1)
    } catch {
      toast.error(tc('error'))
    } finally {
      setLoadingAction(null)
    }
  }, [siteId, date, t, tc])

  const handleClear = useCallback(async (worker: WorkerDayStatus) => {
    if (!worker.attendanceId) return
    setLoadingAction(worker.workerId + 'clear')
    try {
      await api.delete(`/api/attendance/${worker.attendanceId}`)
      toast.success(t('clearedSuccess', { name: worker.workerName }))
      setQueryKey((k) => k + 1)
    } catch {
      toast.error(tc('error'))
    } finally {
      setLoadingAction(null)
    }
  }, [t, tc])

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="sm:max-w-lg max-h-[90vh] flex flex-col">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Users size={17} />
            {t('manualAttendanceTitle')}
          </DialogTitle>
        </DialogHeader>

        {/* Filters */}
        <div className="flex flex-wrap gap-2 shrink-0">
          <Select value={siteId} onValueChange={(v) => { setSiteId(v); setQueryKey(0) }}>
            <SelectTrigger className="flex-1 min-w-[160px]">
              <SelectValue placeholder={t('selectSite')} />
            </SelectTrigger>
            <SelectContent>
              {sites.map((s) => (
                <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>

          <Input
            type="date"
            value={date}
            onChange={(e) => { setDate(e.target.value); setQueryKey(0) }}
            className="w-40"
          />

          <Button onClick={load} disabled={isFetching} className="gap-1.5 shrink-0">
            {isFetching
              ? <Loader2 size={14} className="animate-spin" />
              : <Search size={14} />}
            {tc('search')}
          </Button>
        </div>

        {/* Worker list */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          {queryKey === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-10">
              {t('selectSiteAndLoad')}
            </p>
          ) : isFetching && workers.length === 0 ? (
            <div className="flex justify-center py-10">
              <Loader2 size={24} className="animate-spin text-muted-foreground" />
            </div>
          ) : workers.length === 0 ? (
            <p className="text-sm text-muted-foreground text-center py-10">{t('noWorkersAssigned')}</p>
          ) : (
            <div className={`flex flex-col divide-y divide-border transition-opacity ${isFetching ? 'opacity-50 pointer-events-none' : ''}`}>
              {workers.map((w) => {
                const busy = loadingAction !== null
                const busyThis = loadingAction?.startsWith(w.workerId) ?? false

                return (
                  <div key={w.workerId} className="flex items-center gap-3 py-3 first:pt-1">
                    {/* Status dot */}
                    <span className={`w-2 h-2 rounded-full shrink-0 ${
                      w.lastType === 'CHECK_IN'
                        ? 'bg-green-500'
                        : w.lastType === 'CHECK_OUT'
                        ? 'bg-orange-400'
                        : 'bg-muted-foreground/30'
                    }`} />

                    {/* Name + badge */}
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-foreground truncate">{w.workerName}</p>
                      <Badge
                        variant="outline"
                        className={`text-[10px] mt-0.5 ${
                          w.lastType === 'CHECK_IN'
                            ? 'border-green-500/40 text-green-600 dark:text-green-400'
                            : w.lastType === 'CHECK_OUT'
                            ? 'border-orange-400/40 text-orange-600 dark:text-orange-400'
                            : 'border-border text-muted-foreground'
                        }`}
                      >
                        {w.lastType === 'CHECK_IN'
                          ? t('statusCheckedIn')
                          : w.lastType === 'CHECK_OUT'
                          ? t('statusCheckedOut')
                          : t('statusNoRecord')}
                      </Badge>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-1 shrink-0">
                      {w.lastType !== 'CHECK_IN' && (
                        <Button
                          size="sm"
                          variant="outline"
                          className="h-8 gap-1.5 text-green-600 border-green-500/40 hover:bg-green-500/10 hover:text-green-600"
                          disabled={busy}
                          onClick={() => handleMark(w, 'CHECK_IN')}
                        >
                          {busyThis && loadingAction === w.workerId + 'CHECK_IN'
                            ? <Loader2 size={12} className="animate-spin" />
                            : <LogIn size={12} />}
                          {t('checkIn')}
                        </Button>
                      )}
                      {w.lastType === 'CHECK_IN' && (
                        <Button
                          size="sm"
                          variant="outline"
                          className="h-8 gap-1.5 text-orange-600 border-orange-400/40 hover:bg-orange-500/10 hover:text-orange-600"
                          disabled={busy}
                          onClick={() => handleMark(w, 'CHECK_OUT')}
                        >
                          {busyThis && loadingAction === w.workerId + 'CHECK_OUT'
                            ? <Loader2 size={12} className="animate-spin" />
                            : <LogOut size={12} />}
                          {t('checkOut')}
                        </Button>
                      )}
                      {w.attendanceId && (
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8 text-muted-foreground hover:text-destructive"
                          disabled={busy}
                          onClick={() => handleClear(w)}
                          title={t('clearRecord')}
                        >
                          {busyThis && loadingAction === w.workerId + 'clear'
                            ? <Loader2 size={13} className="animate-spin" />
                            : <X size={13} />}
                        </Button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

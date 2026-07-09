'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  Download, Loader2, CheckCircle2, XCircle, AlertTriangle, Pencil, AlertCircle,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useSites } from '@/hooks/useSites'
import api from '@/lib/axios'

// ── Types ─────────────────────────────────────────────────────────────────────

interface AttendanceRow {
  workerId: string
  workerName: string
  siteName: string
  date: string
  type: 'CHECK_IN' | 'CHECK_OUT'
  recordedAt: string
  lat: number
  lng: number
  locationValid: boolean
  faceConfidence: number | null
  manualOverride: boolean
}

interface MissingRow {
  workerId: string
  workerName: string
}

interface WorkedHoursRow {
  workerId: string
  workerName: string
  siteId: string
  siteName: string
  date: string
  checkIn: string | null
  checkOut: string | null
  inferredCheckOut: boolean
  calculatedHours: number | null
  effectiveHours: number | null
  correctedHours: number | null
  correctionNote: string | null
}

interface WorkedHoursSummaryRow {
  workerId: string
  workerName: string
  details: WorkedHoursRow[]
  totalHours: number
}

interface CorrectionTarget {
  workerId: string
  workerName: string
  siteId: string
  siteName: string
  date: string
  currentHours: number | null
}

type Tab = 'attendance' | 'missing' | 'hours'
type HoursView = 'site' | 'summary'

// ── Helpers ───────────────────────────────────────────────────────────────────

function today() {
  return new Date().toISOString().slice(0, 10)
}

function formatTime(dt: string) {
  return dt.slice(0, 5)
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const t = useTranslations('reports')
  const tc = useTranslations('common')

  const { data: sites = [] } = useSites()

  const [tab, setTab] = useState<Tab>('attendance')
  const [hoursView, setHoursView] = useState<HoursView>('site')
  const [siteId, setSiteId] = useState('')
  const [dateFrom, setDateFrom] = useState(today())
  const [dateTo, setDateTo] = useState(today())
  const [date, setDate] = useState(today())
  const [queryKey, setQueryKey] = useState(0)
  const [exporting, setExporting] = useState(false)

  // Correction dialog state
  const [corrTarget, setCorrTarget] = useState<CorrectionTarget | null>(null)
  const [corrHours, setCorrHours] = useState('')
  const [corrNote, setCorrNote] = useState('')

  // Summary correction: expand one worker row to show details
  const [expandedWorker, setExpandedWorker] = useState<string | null>(null)

  const qc = useQueryClient()

  // ── Queries ────────────────────────────────────────────────────────────────

  const attendanceQuery = useQuery<AttendanceRow[]>({
    queryKey: ['reports', 'attendance', siteId, dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/attendance', {
        params: { siteId, from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'attendance' && !!siteId && queryKey > 0,
  })

  const missingQuery = useQuery<MissingRow[]>({
    queryKey: ['reports', 'missing', siteId, date, queryKey],
    queryFn: () =>
      api.get('/api/reports/missing', {
        params: { siteId, date },
      }).then((r) => r.data),
    enabled: tab === 'missing' && !!siteId && queryKey > 0,
  })

  const hoursQuery = useQuery<WorkedHoursRow[]>({
    queryKey: ['reports', 'hours', siteId, dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/hours', {
        params: { siteId, from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'hours' && hoursView === 'site' && !!siteId && queryKey > 0,
  })

  const hoursSummaryQuery = useQuery<WorkedHoursSummaryRow[]>({
    queryKey: ['reports', 'hours-summary', dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/hours/summary', {
        params: { from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'hours' && hoursView === 'summary' && queryKey > 0,
  })

  // ── Correction mutation ────────────────────────────────────────────────────

  const correctionMutation = useMutation({
    mutationFn: (body: { workerId: string; siteId: string; date: string; correctedHours: number; note: string | null }) =>
      api.put('/api/reports/hours/correction', body),
    onSuccess: () => {
      toast.success(tc('success'))
      qc.invalidateQueries({ queryKey: ['reports', 'hours'] })
      qc.invalidateQueries({ queryKey: ['reports', 'hours-summary'] })
      setCorrTarget(null)
    },
    onError: () => toast.error(tc('error')),
  })

  // ── Actions ────────────────────────────────────────────────────────────────

  const handleSearch = () => {
    if (tab !== 'hours' || hoursView !== 'summary') {
      if (!siteId) { toast.warning(t('site')); return }
    }
    setQueryKey((k) => k + 1)
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      let url = ''
      let filename = ''
      if (tab === 'attendance') {
        const response = await api.get('/api/reports/attendance/export', {
          params: { siteId, from: dateFrom, to: dateTo },
          responseType: 'blob',
        })
        url = URL.createObjectURL(response.data)
        filename = `attendance_${dateFrom}_${dateTo}.xlsx`
      } else if (tab === 'hours') {
        if (hoursView === 'site') {
          const response = await api.get('/api/reports/hours/export', {
            params: { siteId, from: dateFrom, to: dateTo },
            responseType: 'blob',
          })
          url = URL.createObjectURL(response.data)
          filename = `worked_hours_${dateFrom}_${dateTo}.xlsx`
        } else {
          const response = await api.get('/api/reports/hours/summary/export', {
            params: { from: dateFrom, to: dateTo },
            responseType: 'blob',
          })
          url = URL.createObjectURL(response.data)
          filename = `worked_hours_summary_${dateFrom}_${dateTo}.xlsx`
        }
      }
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error(tc('error'))
    } finally {
      setExporting(false)
    }
  }

  const openCorrection = (row: WorkedHoursRow) => {
    setCorrTarget({
      workerId: row.workerId,
      workerName: row.workerName,
      siteId: row.siteId,
      siteName: row.siteName,
      date: row.date,
      currentHours: row.effectiveHours,
    })
    setCorrHours(row.effectiveHours != null ? String(row.effectiveHours) : '')
    setCorrNote(row.correctionNote ?? '')
  }

  const handleSaveCorrection = () => {
    if (!corrTarget) return
    const parsed = parseFloat(corrHours)
    if (isNaN(parsed) || parsed < 0) {
      toast.warning(t('correctionInvalidHours'))
      return
    }
    correctionMutation.mutate({
      workerId: corrTarget.workerId,
      siteId: corrTarget.siteId,
      date: corrTarget.date,
      correctedHours: Math.round(parsed * 4) / 4, // snap to nearest 0.25
      note: corrNote.trim() || null,
    })
  }

  // ── Derived ────────────────────────────────────────────────────────────────

  const needsSite = tab !== 'hours' || hoursView !== 'summary'

  let activeQuery
  if (tab === 'attendance') activeQuery = attendanceQuery
  else if (tab === 'missing') activeQuery = missingQuery
  else activeQuery = hoursView === 'site' ? hoursQuery : hoursSummaryQuery

  const isLoading = activeQuery.isLoading
  const rows = activeQuery.data ?? []
  const showExport = tab === 'attendance' || tab === 'hours'

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-muted rounded-lg w-fit">
        {(['attendance', 'missing', 'hours'] as Tab[]).map((tabKey) => (
          <button
            key={tabKey}
            onClick={() => setTab(tabKey)}
            className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
              tab === tabKey
                ? 'bg-background text-foreground shadow-sm'
                : 'text-muted-foreground hover:text-foreground'
            }`}
          >
            {t(tabKey)}
          </button>
        ))}
      </div>

      {/* Hours sub-toggle */}
      {tab === 'hours' && (
        <div className="flex gap-1 p-1 bg-muted/50 border border-border rounded-lg w-fit">
          {(['site', 'summary'] as HoursView[]).map((v) => (
            <button
              key={v}
              onClick={() => { setHoursView(v); setQueryKey(0) }}
              className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                hoursView === v
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              {t(v === 'site' ? 'hoursBySite' : 'hoursOverall')}
            </button>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        {needsSite && (
          <div className="flex flex-col gap-1.5 w-full sm:w-auto sm:min-w-[200px]">
            <label className="text-xs font-medium text-muted-foreground">{t('site')}</label>
            <Select value={siteId} onValueChange={setSiteId}>
              <SelectTrigger>
                <SelectValue placeholder={t('site')} />
              </SelectTrigger>
              <SelectContent>
                {sites.map((s) => (
                  <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {tab === 'missing' ? (
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-muted-foreground">{t('date')}</label>
            <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} className="w-40" />
          </div>
        ) : (
          <>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-muted-foreground">{t('dateFrom')}</label>
              <Input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="w-40" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-muted-foreground">{t('dateTo')}</label>
              <Input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="w-40" />
            </div>
          </>
        )}

        <Button onClick={handleSearch} disabled={isLoading}>
          {isLoading ? <Loader2 size={15} className="animate-spin mr-2" /> : null}
          {tc('search')}
        </Button>

        {showExport && (
          <Button
            variant="outline"
            onClick={handleExport}
            disabled={exporting || (needsSite && !siteId)}
            className="gap-2"
          >
            {exporting ? <Loader2 size={15} className="animate-spin" /> : <Download size={15} />}
            {t('export')}
          </Button>
        )}
      </div>

      {/* Results */}
      {queryKey === 0 ? null : isLoading ? (
        <div className="flex justify-center py-12">
          <Loader2 size={28} className="animate-spin text-muted-foreground" />
        </div>
      ) : rows.length === 0 ? (
        <p className="text-sm text-muted-foreground py-8 text-center">{t('noResults')}</p>
      ) : tab === 'attendance' ? (
        <AttendanceTable rows={rows as AttendanceRow[]} t={t} />
      ) : tab === 'missing' ? (
        <MissingTable rows={rows as MissingRow[]} t={t} />
      ) : hoursView === 'site' ? (
        <WorkedHoursTable rows={rows as WorkedHoursRow[]} t={t} onCorrect={openCorrection} />
      ) : (
        <WorkedHoursSummaryTable
          rows={rows as WorkedHoursSummaryRow[]}
          t={t}
          onCorrect={openCorrection}
          expandedWorker={expandedWorker}
          setExpandedWorker={setExpandedWorker}
        />
      )}

      {/* Correction dialog */}
      <Dialog open={!!corrTarget} onOpenChange={(open) => !open && setCorrTarget(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('correctHours')}</DialogTitle>
          </DialogHeader>
          {corrTarget && (
            <div className="flex flex-col gap-4 py-2">
              <div className="text-sm text-muted-foreground space-y-0.5">
                <p><span className="font-medium text-foreground">{corrTarget.workerName}</span></p>
                <p>{corrTarget.siteName} · {corrTarget.date}</p>
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="corr-hours">{t('correctedHours')} (h)</Label>
                <Input
                  id="corr-hours"
                  type="number"
                  min="0"
                  step="0.25"
                  value={corrHours}
                  onChange={(e) => setCorrHours(e.target.value)}
                  placeholder="0.00"
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="corr-note">{t('correctionNote')} ({t('optional')})</Label>
                <Textarea
                  id="corr-note"
                  rows={2}
                  value={corrNote}
                  onChange={(e) => setCorrNote(e.target.value)}
                  placeholder={t('correctionNotePlaceholder')}
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setCorrTarget(null)}>{tc('cancel')}</Button>
            <Button onClick={handleSaveCorrection} disabled={correctionMutation.isPending}>
              {correctionMutation.isPending && <Loader2 size={14} className="animate-spin mr-2" />}
              {tc('save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

// ── Attendance table ───────────────────────────────────────────────────────────

function AttendanceTable({
  rows, t,
}: { rows: AttendanceRow[]; t: ReturnType<typeof useTranslations> }) {
  return (
    <div className="rounded-lg border border-border overflow-x-auto">
      <Table className="min-w-[640px]">
        <TableHeader>
          <TableRow className="bg-muted/50">
            <TableHead>{t('date')}</TableHead>
            <TableHead>{t('recordedAt')}</TableHead>
            <TableHead>{t('worker')}</TableHead>
            <TableHead>{t('site')}</TableHead>
            <TableHead>{t('type')}</TableHead>
            <TableHead>{t('location')}</TableHead>
            <TableHead>{t('locationValid')}</TableHead>
            <TableHead>{t('faceConfidence')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, i) => (
            <TableRow key={i}>
              <TableCell className="text-sm">{row.date}</TableCell>
              <TableCell className="text-sm font-mono">{formatTime(row.recordedAt.slice(11))}</TableCell>
              <TableCell className="font-medium">{row.workerName}</TableCell>
              <TableCell className="text-muted-foreground text-sm">{row.siteName}</TableCell>
              <TableCell>
                <Badge
                  variant={row.type === 'CHECK_IN' ? 'default' : 'secondary'}
                  className={row.type === 'CHECK_IN'
                    ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
                    : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'}
                >
                  {t(row.type === 'CHECK_IN' ? 'checkIn' : 'checkOut')}
                </Badge>
              </TableCell>
              <TableCell className="text-xs font-mono text-muted-foreground whitespace-nowrap">
                <a
                  href={`https://www.google.com/maps?q=${row.lat},${row.lng}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="hover:text-primary underline-offset-2 hover:underline"
                >
                  {row.lat.toFixed(5)}, {row.lng.toFixed(5)}
                </a>
              </TableCell>
              <TableCell>
                {row.locationValid ? (
                  <span className="flex items-center gap-1.5 text-green-600 dark:text-green-400 text-sm font-medium">
                    <CheckCircle2 size={15} />{t('inZone')}
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5 text-red-600 dark:text-red-400 text-sm font-medium">
                    <XCircle size={15} />{t('outOfZone')}
                  </span>
                )}
              </TableCell>
              <TableCell className="text-sm">
                {row.manualOverride ? (
                  <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400">
                    <AlertTriangle size={13} />{t('manual')}
                  </span>
                ) : row.faceConfidence != null ? (
                  `${Math.round(row.faceConfidence)}%`
                ) : (
                  <span className="text-muted-foreground">—</span>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

// ── Missing workers table ──────────────────────────────────────────────────────

function MissingTable({
  rows, t,
}: { rows: MissingRow[]; t: ReturnType<typeof useTranslations> }) {
  return (
    <div className="rounded-lg border border-border overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow className="bg-muted/50">
            <TableHead>#</TableHead>
            <TableHead>{t('worker')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, i) => (
            <TableRow key={row.workerId}>
              <TableCell className="text-muted-foreground text-sm w-12">{i + 1}</TableCell>
              <TableCell className="font-medium">{row.workerName}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

// ── Worked hours table (by-site) ───────────────────────────────────────────────

function WorkedHoursTable({
  rows, t, onCorrect,
}: {
  rows: WorkedHoursRow[]
  t: ReturnType<typeof useTranslations>
  onCorrect: (row: WorkedHoursRow) => void
}) {
  return (
    <div className="rounded-lg border border-border overflow-x-auto">
      <Table className="min-w-[700px]">
        <TableHeader>
          <TableRow className="bg-muted/50">
            <TableHead>{t('worker')}</TableHead>
            <TableHead>{t('site')}</TableHead>
            <TableHead>{t('date')}</TableHead>
            <TableHead>{t('checkIn')}</TableHead>
            <TableHead>{t('checkOut')}</TableHead>
            <TableHead>{t('hoursWorked')}</TableHead>
            <TableHead></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row, i) => {
            const openShift = row.checkOut === null && !row.inferredCheckOut
            const hasCorrection = row.correctedHours !== null
            return (
              <TableRow key={i} className={openShift ? 'bg-amber-50/40 dark:bg-amber-900/10' : ''}>
                <TableCell className="font-medium">{row.workerName}</TableCell>
                <TableCell className="text-sm text-muted-foreground">{row.siteName}</TableCell>
                <TableCell className="text-sm">{row.date}</TableCell>
                <TableCell className="font-mono text-sm">
                  {row.checkIn ? formatTime(row.checkIn) : '—'}
                </TableCell>
                <TableCell className="font-mono text-sm">
                  {openShift ? (
                    <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                      <AlertCircle size={13} />
                      {t('openShift')}
                    </span>
                  ) : row.inferredCheckOut ? (
                    <span className="flex items-center gap-1 text-sky-600 dark:text-sky-400 font-mono text-sm">
                      {row.checkOut ? formatTime(row.checkOut) : '—'}
                      <span title={t('inferredCheckOut')}><AlertCircle size={12} className="ml-0.5 opacity-70" /></span>
                    </span>
                  ) : formatTime(row.checkOut!)}
                </TableCell>
                <TableCell className="text-sm">
                  {openShift ? (
                    <span className="text-muted-foreground">—</span>
                  ) : (
                    <span className={hasCorrection ? 'text-blue-600 dark:text-blue-400 font-medium' : ''}>
                      {row.effectiveHours}h
                      {hasCorrection && (
                        <span className="ml-1 text-xs text-muted-foreground line-through">
                          {row.calculatedHours}h
                        </span>
                      )}
                    </span>
                  )}
                </TableCell>
                <TableCell>
                  {!openShift && (
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-7 w-7 text-muted-foreground hover:text-foreground"
                      onClick={() => onCorrect(row)}
                    >
                      <Pencil size={13} />
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}

// ── Worked hours summary table (all sites) ────────────────────────────────────

function WorkedHoursSummaryTable({
  rows, t, onCorrect, expandedWorker, setExpandedWorker,
}: {
  rows: WorkedHoursSummaryRow[]
  t: ReturnType<typeof useTranslations>
  onCorrect: (row: WorkedHoursRow) => void
  expandedWorker: string | null
  setExpandedWorker: (id: string | null) => void
}) {
  return (
    <div className="flex flex-col gap-3">
      {rows.map((worker) => {
        const isExpanded = expandedWorker === worker.workerId
        return (
          <div key={worker.workerId} className="rounded-lg border border-border overflow-hidden">
            {/* Worker header row */}
            <div
              className="flex items-center justify-between px-4 py-3 bg-muted/40 cursor-pointer hover:bg-muted/60 transition-colors"
              onClick={() => setExpandedWorker(isExpanded ? null : worker.workerId)}
            >
              <span className="font-medium text-foreground">{worker.workerName}</span>
              <div className="flex items-center gap-3">
                <span className="text-sm font-semibold text-foreground">
                  {t('total')}: {worker.totalHours}h
                </span>
                <Button
                  size="sm"
                  variant="ghost"
                  className="h-7 text-xs text-muted-foreground"
                  onClick={(e) => {
                    e.stopPropagation()
                    setExpandedWorker(isExpanded ? null : worker.workerId)
                  }}
                >
                  {isExpanded ? t('collapse') : t('expand')}
                </Button>
              </div>
            </div>

            {/* Expanded detail rows */}
            {isExpanded && (
              <div className="overflow-x-auto">
                <Table className="min-w-[600px]">
                  <TableHeader>
                    <TableRow className="bg-muted/20">
                      <TableHead className="text-xs">{t('site')}</TableHead>
                      <TableHead className="text-xs">{t('date')}</TableHead>
                      <TableHead className="text-xs">{t('checkIn')}</TableHead>
                      <TableHead className="text-xs">{t('checkOut')}</TableHead>
                      <TableHead className="text-xs">{t('hoursWorked')}</TableHead>
                      <TableHead></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {worker.details.map((row, i) => {
                      const openShift = row.checkOut === null && !row.inferredCheckOut
                      const hasCorrection = row.correctedHours !== null
                      return (
                        <TableRow key={i} className={openShift ? 'bg-amber-50/40 dark:bg-amber-900/10' : ''}>
                          <TableCell className="text-sm text-muted-foreground">{row.siteName}</TableCell>
                          <TableCell className="text-sm">{row.date}</TableCell>
                          <TableCell className="font-mono text-sm">
                            {row.checkIn ? formatTime(row.checkIn) : '—'}
                          </TableCell>
                          <TableCell className="font-mono text-sm">
                            {openShift ? (
                              <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium text-xs">
                                <AlertCircle size={12} />
                                {t('openShift')}
                              </span>
                            ) : row.inferredCheckOut ? (
                              <span className="flex items-center gap-1 text-sky-600 dark:text-sky-400 font-mono text-sm">
                                {row.checkOut ? formatTime(row.checkOut) : '—'}
                                <span title={t('inferredCheckOut')}><AlertCircle size={11} className="ml-0.5 opacity-70" /></span>
                              </span>
                            ) : formatTime(row.checkOut!)}
                          </TableCell>
                          <TableCell className="text-sm">
                            {openShift ? (
                              <span className="text-muted-foreground">—</span>
                            ) : (
                              <span className={hasCorrection ? 'text-blue-600 dark:text-blue-400 font-medium' : ''}>
                                {row.effectiveHours}h
                                {hasCorrection && (
                                  <span className="ml-1 text-xs text-muted-foreground line-through">
                                    {row.calculatedHours}h
                                  </span>
                                )}
                              </span>
                            )}
                          </TableCell>
                          <TableCell>
                            {!openShift && (
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-7 w-7 text-muted-foreground hover:text-foreground"
                                onClick={() => onCorrect(row)}
                              >
                                <Pencil size={13} />
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

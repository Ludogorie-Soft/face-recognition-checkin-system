'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import {
  Download, Loader2, CheckCircle2, XCircle, AlertTriangle, Pencil, AlertCircle, MapPin, Filter, ArrowUp, ArrowDown, ArrowUpDown, Info,
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
import { AttendanceDetailsModal } from '@/components/reports/AttendanceDetailsModal'
import { useSites } from '@/hooks/useSites'
import { useCompanies } from '@/hooks/useCompanies'
import api from '@/lib/axios'

// ── Types ─────────────────────────────────────────────────────────────────────

interface AttendanceRow {
  id: string
  workerId: string
  workerName: string
  companyName: string | null
  siteName: string
  date: string
  type: 'CHECK_IN' | 'CHECK_OUT'
  recordedAt: string
  lat: number
  lng: number
  locationValid: boolean
  faceConfidence: number | null
  manualOverride: boolean
  adminManual: boolean
}

interface MissingRow {
  workerId: string
  workerName: string
}

interface WorkedHoursRow {
  checkInId: string | null
  checkOutId: string | null
  workerId: string
  workerName: string
  companyName: string | null
  siteId: string
  siteName: string
  date: string
  pairIndex: number          // ≥0 = session row; -1 = day-total row
  checkIn: string | null
  checkOut: string | null
  inferredCheckOut: boolean
  autoCheckout: boolean
  adminManualCheckIn: boolean
  calculatedHours: number | null
  effectiveHours: number | null
  correctedHours: number | null
  correctionNote: string | null
  checkOutLat: number | null
  checkOutLng: number | null
  checkInLat: number | null
  checkInLng: number | null
  checkInLocationValid: boolean | null
  checkOutLocationValid: boolean | null
  anomalyReason: string | null   // non-null → session flagged by sync reconciliation
  offline: boolean               // recorded on a device with no connectivity
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

type Tab = 'attendance' | 'missing' | 'hours' | 'summary'
type Period = 'day' | 'week' | 'month' | 'custom'

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtLocal(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function today() {
  return fmtLocal(new Date())
}

function formatTime(dt: string) {
  return dt.slice(0, 5)
}

function fmt(d: Date) {
  return fmtLocal(d)
}

function getPeriodDates(period: Period): { from: string; to: string } {
  const now = new Date()
  if (period === 'day') {
    const t = fmt(now)
    return { from: t, to: t }
  }
  if (period === 'week') {
    const day = now.getDay()
    const diffToMon = day === 0 ? -6 : 1 - day
    const mon = new Date(now)
    mon.setDate(now.getDate() + diffToMon)
    const sun = new Date(mon)
    sun.setDate(mon.getDate() + 6)
    return { from: fmt(mon), to: fmt(sun) }
  }
  if (period === 'month') {
    const from = new Date(now.getFullYear(), now.getMonth(), 1)
    const to = new Date(now.getFullYear(), now.getMonth() + 1, 0)
    return { from: fmt(from), to: fmt(to) }
  }
  return { from: fmt(now), to: fmt(now) }
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const t = useTranslations('reports')
  const tc = useTranslations('common')

  const { data: sites = [] } = useSites()
  const { data: companies = [] } = useCompanies()

  const [tab, setTab] = useState<Tab>('hours')
  const [siteId, setSiteId] = useState('')
  const [companyId, setCompanyId] = useState('')
  const [period, setPeriod] = useState<Period>('day')

  const selectedCompany = companies.find((c) => c.id === companyId) ?? null
  const filteredSites = selectedCompany
    ? sites.filter((s) => selectedCompany.sites.some((cs) => cs.id === s.id))
    : sites
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
    queryKey: ['reports', 'attendance', siteId, companyId, dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/attendance', {
        params: { siteId: siteId || undefined, companyId: companyId || undefined, from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'attendance' && queryKey > 0,
  })

  const missingQuery = useQuery<MissingRow[]>({
    queryKey: ['reports', 'missing', siteId, companyId, date, queryKey],
    queryFn: () =>
      api.get('/api/reports/missing', {
        params: { siteId, companyId: companyId || undefined, date },
      }).then((r) => r.data),
    enabled: tab === 'missing' && !!siteId && queryKey > 0,
  })

  const hoursQuery = useQuery<WorkedHoursRow[]>({
    queryKey: ['reports', 'hours', siteId, companyId, dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/hours', {
        params: { siteId: siteId || undefined, companyId: companyId || undefined, from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'hours' && queryKey > 0,
  })

  const hoursSummaryQuery = useQuery<WorkedHoursSummaryRow[]>({
    queryKey: ['reports', 'hours-summary', companyId, dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/hours/summary', {
        params: { companyId: companyId || undefined, from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'summary' && queryKey > 0,
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

  // ── Revalidate location mutation ──────────────────────────────────────────

  const revalidateMutation = useMutation({
    mutationFn: () =>
      api.post('/api/attendance/revalidate', null, {
        params: { siteId: siteId || undefined, from: dateFrom, to: dateTo },
      }).then((r) => r.data as { updated: number }),
    onSuccess: (data) => {
      toast.success(t('revalidateSuccess', { count: data.updated }))
      qc.invalidateQueries({ queryKey: ['reports', 'attendance'] })
    },
    onError: () => toast.error(tc('error')),
  })

  // ── Period helpers ─────────────────────────────────────────────────────────

  const handlePeriodChange = (p: Period) => {
    setPeriod(p)
    if (p !== 'custom') {
      const { from, to } = getPeriodDates(p)
      setDateFrom(from)
      setDateTo(to)
    }
    setQueryKey(0)
  }

  const handleDateFromChange = (v: string) => {
    setDateFrom(v)
    setPeriod('custom')
    setQueryKey(0)
  }

  const handleDateToChange = (v: string) => {
    setDateTo(v)
    setPeriod('custom')
    setQueryKey(0)
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  const handleSearch = () => {
    if (tab === 'missing' && !siteId) { toast.warning(t('site')); return }
    setQueryKey((k) => k + 1)
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      let url = ''
      let filename = ''
      if (tab === 'attendance') {
        const response = await api.get('/api/reports/attendance/export', {
          params: { siteId: siteId || undefined, from: dateFrom, to: dateTo },
          responseType: 'blob',
        })
        url = URL.createObjectURL(response.data)
        filename = `attendance_${dateFrom}_${dateTo}.xlsx`
      } else if (tab === 'hours') {
        const response = await api.get('/api/reports/hours/export', {
          params: { siteId: siteId || undefined, from: dateFrom, to: dateTo },
          responseType: 'blob',
        })
        url = URL.createObjectURL(response.data)
        filename = `worked_hours_${dateFrom}_${dateTo}.xlsx`
      } else if (tab === 'summary') {
        const response = await api.get('/api/reports/hours/summary/export', {
          params: { companyId: companyId || undefined, from: dateFrom, to: dateTo },
          responseType: 'blob',
        })
        url = URL.createObjectURL(response.data)
        filename = `worked_hours_summary_${dateFrom}_${dateTo}.xlsx`
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
      correctedHours: parsed,
      note: corrNote.trim() || null,
    })
  }

  // ── Derived ────────────────────────────────────────────────────────────────

  const needsSite = tab !== 'summary'

  let activeQuery
  if (tab === 'attendance') activeQuery = attendanceQuery
  else if (tab === 'missing') activeQuery = missingQuery
  else if (tab === 'summary') activeQuery = hoursSummaryQuery
  else activeQuery = hoursQuery

  const isLoading = activeQuery.isLoading
  const rows = activeQuery.data ?? []
  const showExport = tab === 'attendance' || tab === 'hours' || tab === 'summary'

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-muted rounded-lg w-fit">
        {(['hours', 'summary', 'attendance', 'missing'] as Tab[]).map((tabKey) => (
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

      {/* Period selector */}
      {tab !== 'missing' && (
        <div className="flex gap-1 p-1 bg-muted/50 border border-border rounded-lg w-fit">
          {(['day', 'week', 'month', 'custom'] as Period[]).map((p) => (
            <button
              key={p}
              onClick={() => handlePeriodChange(p)}
              className={`px-3 py-1 rounded-md text-xs font-medium transition-colors ${
                period === p
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              {t(p === 'day' ? 'periodDay' : p === 'week' ? 'periodWeek' : p === 'month' ? 'periodMonth' : 'periodCustom')}
            </button>
          ))}
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        {/* Company filter */}
        {companies.length > 0 && (
          <div className="flex flex-col gap-1.5 w-full sm:w-auto sm:min-w-[180px]">
            <label className="text-xs font-medium text-muted-foreground">{t('company')}</label>
            <Select value={companyId} onValueChange={(v) => { setCompanyId(v === '_all' ? '' : v); setSiteId(''); setQueryKey(0) }}>
              <SelectTrigger>
                <SelectValue placeholder={t('allCompanies')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="_all">{t('allCompanies')}</SelectItem>
                {companies.map((c) => (
                  <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        {needsSite && (
          <div className="flex flex-col gap-1.5 w-full sm:w-auto sm:min-w-[200px]">
            <label className="text-xs font-medium text-muted-foreground">{t('site')}</label>
            <Select value={siteId || '_all'} onValueChange={(v) => { setSiteId(v === '_all' ? '' : v); setQueryKey(0) }}>
              <SelectTrigger>
                <SelectValue placeholder={t('allSites')} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="_all">{t('allSites')}</SelectItem>
                {filteredSites.map((s) => (
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
              <Input type="date" value={dateFrom} onChange={(e) => handleDateFromChange(e.target.value)} className="w-40" />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-muted-foreground">{t('dateTo')}</label>
              <Input type="date" value={dateTo} onChange={(e) => handleDateToChange(e.target.value)} className="w-40" />
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
            disabled={exporting}
            className="gap-2"
          >
            {exporting ? <Loader2 size={15} className="animate-spin" /> : <Download size={15} />}
            {t('export')}
          </Button>
        )}

        {tab === 'attendance' && (
          <Button
            variant="outline"
            onClick={() => revalidateMutation.mutate()}
            disabled={revalidateMutation.isPending || queryKey === 0}
            className="gap-2"
          >
            {revalidateMutation.isPending
              ? <Loader2 size={15} className="animate-spin" />
              : <MapPin size={15} />}
            {t('revalidate')}
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
        <AttendanceTable rows={rows as AttendanceRow[]} t={t} sites={filteredSites} />
      ) : tab === 'missing' ? (
        <MissingTable rows={rows as MissingRow[]} t={t} />
      ) : tab === 'hours' ? (
        <WorkedHoursTable rows={rows as WorkedHoursRow[]} t={t} onCorrect={openCorrection} sites={filteredSites} />
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
  rows, t, sites,
}: {
  rows: AttendanceRow[]
  t: ReturnType<typeof useTranslations>
  sites: import('@/types/site').SiteResponse[]
}) {
  const tc = useTranslations('common')
  const qc = useQueryClient()

  const [changeSiteRow, setChangeSiteRow] = useState<AttendanceRow | null>(null)
  const [selectedSiteId, setSelectedSiteId] = useState('')
  const [detailsRow, setDetailsRow] = useState<AttendanceRow | null>(null)

  const changeSiteMutation = useMutation({
    mutationFn: ({ id, siteId }: { id: string; siteId: string }) =>
      api.patch(`/api/attendance/${id}/site`, null, { params: { siteId } }),
    onSuccess: () => {
      toast.success(tc('success'))
      qc.invalidateQueries({ queryKey: ['reports', 'attendance'] })
      setChangeSiteRow(null)
    },
    onError: () => toast.error(tc('error')),
  })

  const handleOpenChangeSite = (row: AttendanceRow) => {
    setChangeSiteRow(row)
    setSelectedSiteId('')
  }

  return (
    <>
      <div className="rounded-lg border border-border overflow-x-auto">
        <Table className="min-w-[700px]">
          <TableHeader>
            <TableRow className="bg-muted/50">
              <TableHead>{t('date')}</TableHead>
              <TableHead>{t('recordedAt')}</TableHead>
              <TableHead>{t('worker')}</TableHead>
              <TableHead>{t('company')}</TableHead>
              <TableHead>{t('site')}</TableHead>
              <TableHead>{t('type')}</TableHead>
              <TableHead>{t('location')}</TableHead>
              <TableHead>{t('locationValid')}</TableHead>
              <TableHead>{t('faceConfidence')}</TableHead>
              <TableHead></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row, i) => (
              <TableRow key={i}>
                <TableCell className="text-sm">{row.date}</TableCell>
                <TableCell className="text-sm font-mono">{formatTime(row.recordedAt.slice(11))}</TableCell>
                <TableCell className="font-medium">{row.workerName}</TableCell>
                <TableCell className="text-muted-foreground text-sm">{row.companyName ?? '—'}</TableCell>
                <TableCell className="text-muted-foreground text-sm">
                  <span className="flex items-center gap-1.5">
                    {row.siteName}
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-6 w-6 text-muted-foreground hover:text-foreground shrink-0"
                      title={t('changeSite')}
                      onClick={() => handleOpenChangeSite(row)}
                    >
                      <Pencil size={11} />
                    </Button>
                  </span>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-1.5">
                    <Badge
                      variant={row.type === 'CHECK_IN' ? 'default' : 'secondary'}
                      className={row.type === 'CHECK_IN'
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
                        : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'}
                    >
                      {t(row.type === 'CHECK_IN' ? 'checkIn' : 'checkOut')}
                    </Badge>
                    {row.adminManual && (
                      <Badge variant="outline" className="text-xs text-muted-foreground border-muted-foreground/40">
                        {t('adminManual')}
                      </Badge>
                    )}
                  </div>
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
                <TableCell>
                  <Button
                    size="icon"
                    variant="ghost"
                    className="h-7 w-7 text-muted-foreground hover:text-foreground"
                    title={t('details')}
                    onClick={() => setDetailsRow(row)}
                  >
                    <Info size={13} />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {/* Change site dialog */}
      <Dialog open={!!changeSiteRow} onOpenChange={(v) => !v && setChangeSiteRow(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('changeSite')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-2">
            <p className="text-sm text-muted-foreground">
              {changeSiteRow?.workerName} — {changeSiteRow?.siteName}
            </p>
            <Select value={selectedSiteId} onValueChange={setSelectedSiteId}>
              <SelectTrigger>
                <SelectValue placeholder={t('selectSite')} />
              </SelectTrigger>
              <SelectContent>
                {sites.map((s) => (
                    <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setChangeSiteRow(null)}>{tc('cancel')}</Button>
            <Button
              disabled={!selectedSiteId || changeSiteMutation.isPending}
              onClick={() => changeSiteRow && changeSiteMutation.mutate({ id: changeSiteRow.id, siteId: selectedSiteId })}
            >
              {changeSiteMutation.isPending && <Loader2 size={14} className="animate-spin mr-2" />}
              {tc('save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AttendanceDetailsModal
        open={!!detailsRow}
        onClose={() => setDetailsRow(null)}
        checkInId={detailsRow?.id ?? null}
        checkOutId={null}
        t={t}
      />
    </>
  )
}

// ── Missing workers table ──────────────────────────────────────────────────────

function MissingTable({
  rows, t,
}: { rows: MissingRow[]; t: ReturnType<typeof useTranslations> }) {
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc')

  const sorted = [...rows].sort((a, b) =>
    sortDir === 'asc'
      ? a.workerName.localeCompare(b.workerName, undefined, { sensitivity: 'base' })
      : b.workerName.localeCompare(a.workerName, undefined, { sensitivity: 'base' })
  )

  const SortIcon = sortDir === 'asc' ? ArrowUp : ArrowDown

  return (
    <div className="rounded-lg border border-border overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow className="bg-muted/50">
            <TableHead className="w-12">#</TableHead>
            <TableHead>
              <button
                onClick={() => setSortDir((d) => d === 'asc' ? 'desc' : 'asc')}
                className="flex items-center gap-1.5 hover:text-foreground transition-colors"
              >
                {t('worker')}
                <SortIcon size={13} className="text-muted-foreground" />
              </button>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((row, i) => (
            <TableRow key={row.workerId}>
              <TableCell className="text-muted-foreground text-sm">{i + 1}</TableCell>
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
  rows, t, onCorrect, sites,
}: {
  rows: WorkedHoursRow[]
  t: ReturnType<typeof useTranslations>
  onCorrect: (row: WorkedHoursRow) => void
  sites: import('@/types/site').SiteResponse[]
}) {
  const tc = useTranslations('common')
  const qc = useQueryClient()

  const [changeSiteRow, setChangeSiteRow] = useState<WorkedHoursRow | null>(null)
  const [selectedSiteId, setSelectedSiteId] = useState('')
  const [showAutoCheckoutOnly, setShowAutoCheckoutOnly] = useState(false)
  const [showOutOfZoneOnly, setShowOutOfZoneOnly] = useState(false)
  const [showAnomalyOnly, setShowAnomalyOnly] = useState(false)
  const [detailsRow, setDetailsRow] = useState<WorkedHoursRow | null>(null)

  const changeSiteMutation = useMutation({
    mutationFn: ({ checkInId, checkOutId, siteId }: { checkInId: string; checkOutId: string | null; siteId: string }) =>
      api.patch('/api/attendance/move-session', null, { params: { checkInId, checkOutId: checkOutId ?? undefined, siteId } }),
    onSuccess: () => {
      toast.success(tc('success'))
      qc.invalidateQueries({ queryKey: ['reports', 'hours'] })
      setChangeSiteRow(null)
    },
    onError: () => toast.error(tc('error')),
  })

  // When filter is on: keep session rows with autoCheckout=true, and total rows
  // whose worker+site+date group has at least one autoCheckout session.
  const isOutOfZone = (r: WorkedHoursRow) =>
    r.pairIndex >= 0 && (r.checkInLocationValid === false || r.checkOutLocationValid === false)

  const autoCheckoutKeys = showAutoCheckoutOnly
    ? new Set(rows.filter((r) => r.pairIndex >= 0 && r.autoCheckout).map((r) => `${r.workerId}:${r.siteId}:${r.date}`))
    : null
  const outOfZoneKeys = showOutOfZoneOnly
    ? new Set(rows.filter(isOutOfZone).map((r) => `${r.workerId}:${r.siteId}:${r.date}`))
    : null
  const anomalyKeys = showAnomalyOnly
    ? new Set(rows.filter((r) => r.pairIndex >= 0 && r.anomalyReason).map((r) => `${r.workerId}:${r.siteId}:${r.date}`))
    : null

  const displayedRows = rows.filter((r) => {
    const key = `${r.workerId}:${r.siteId}:${r.date}`
    if (autoCheckoutKeys && (r.pairIndex === -1 ? !autoCheckoutKeys.has(key) : !r.autoCheckout)) return false
    if (outOfZoneKeys && (r.pairIndex === -1 ? !outOfZoneKeys.has(key) : !isOutOfZone(r))) return false
    if (anomalyKeys && (r.pairIndex === -1 ? !anomalyKeys.has(key) : !r.anomalyReason)) return false
    return true
  })

  const anomalyLabel = (reason: string | null) =>
    reason === 'DUPLICATE_CHECK_IN' ? t('anomalyDuplicateCheckIn')
    : reason === 'DUPLICATE_CHECK_OUT' ? t('anomalyDuplicateCheckOut')
    : reason === 'CHECKOUT_WITHOUT_CHECKIN' ? t('anomalyCheckoutWithoutCheckin')
    : ''

  return (
    <>
    {/* Filter toggles */}
    <div className="flex items-center gap-2 mb-2">
      <button
        onClick={() => setShowAutoCheckoutOnly((v) => !v)}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium border transition-colors ${
          showAutoCheckoutOnly
            ? 'bg-orange-100 text-orange-700 border-orange-300 dark:bg-orange-900/30 dark:text-orange-400 dark:border-orange-700'
            : 'bg-background text-muted-foreground border-border hover:text-foreground'
        }`}
      >
        <Filter size={12} />
        {t('filterAutoCheckout')}
        {showAutoCheckoutOnly && (
          <span className="ml-1 bg-orange-500 text-white rounded-full px-1.5 text-[10px] font-bold leading-4">
            {displayedRows.filter((r) => r.pairIndex >= 0).length}
          </span>
        )}
      </button>
      <button
        onClick={() => setShowOutOfZoneOnly((v) => !v)}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium border transition-colors ${
          showOutOfZoneOnly
            ? 'bg-red-100 text-red-700 border-red-300 dark:bg-red-900/30 dark:text-red-400 dark:border-red-700'
            : 'bg-background text-muted-foreground border-border hover:text-foreground'
        }`}
      >
        <Filter size={12} />
        {t('filterOutOfZone')}
        {showOutOfZoneOnly && (
          <span className="ml-1 bg-red-500 text-white rounded-full px-1.5 text-[10px] font-bold leading-4">
            {displayedRows.filter((r) => r.pairIndex >= 0).length}
          </span>
        )}
      </button>
      <button
        onClick={() => setShowAnomalyOnly((v) => !v)}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium border transition-colors ${
          showAnomalyOnly
            ? 'bg-red-100 text-red-700 border-red-300 dark:bg-red-900/30 dark:text-red-400 dark:border-red-700'
            : 'bg-background text-muted-foreground border-border hover:text-foreground'
        }`}
      >
        <Filter size={12} />
        {t('filterAnomaly')}
        {showAnomalyOnly && (
          <span className="ml-1 bg-red-500 text-white rounded-full px-1.5 text-[10px] font-bold leading-4">
            {displayedRows.filter((r) => r.pairIndex >= 0).length}
          </span>
        )}
      </button>
    </div>
    <div className="rounded-lg border border-border overflow-x-auto">
      <Table className="min-w-[700px]">
        <TableHeader>
          <TableRow className="bg-muted/50">
            <TableHead>{t('worker')}</TableHead>
            <TableHead>{t('company')}</TableHead>
            <TableHead>{t('site')}</TableHead>
            <TableHead>{t('date')}</TableHead>
            <TableHead>{t('checkIn')}</TableHead>
            <TableHead>{t('checkOut')}</TableHead>
            <TableHead>{t('hoursWorked')}</TableHead>
            <TableHead>{t('checkInLocation')}</TableHead>
            <TableHead>{t('checkOutLocation')}</TableHead>
            <TableHead></TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {displayedRows.map((row, i) => {
            const isTotalRow = row.pairIndex === -1
            const openShift = !isTotalRow && row.checkOut === null && !row.inferredCheckOut
            const hasCorrection = row.correctedHours !== null
            return (
              <TableRow
                key={i}
                className={
                  isTotalRow
                    ? 'bg-muted/40 font-semibold'
                    : openShift
                    ? 'bg-amber-50/40 dark:bg-amber-900/10'
                    : ''
                }
              >
                <TableCell className="font-medium">{row.workerName}</TableCell>
                <TableCell className="text-sm text-muted-foreground">{row.companyName ?? '—'}</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    {row.siteName}
                    {!isTotalRow && row.checkInId && (
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-6 w-6 text-muted-foreground hover:text-foreground shrink-0"
                        title={t('changeSite')}
                        onClick={() => { setChangeSiteRow(row); setSelectedSiteId('') }}
                      >
                        <Pencil size={11} />
                      </Button>
                    )}
                  </span>
                </TableCell>
                <TableCell className="text-sm">{row.date}</TableCell>
                <TableCell className="font-mono text-sm">
                  {isTotalRow
                    ? <span className="text-xs text-muted-foreground">{t('total')}</span>
                    : <span className="flex items-center gap-1.5">
                        {row.checkIn ? formatTime(row.checkIn) : '—'}
                        {row.adminManualCheckIn && (
                          <Badge variant="outline" className="text-xs text-muted-foreground border-muted-foreground/40 font-normal">
                            {t('adminManual')}
                          </Badge>
                        )}
                        {row.anomalyReason && (
                          <span title={anomalyLabel(row.anomalyReason)} className="inline-flex items-center gap-1 text-red-600 dark:text-red-400">
                            <AlertTriangle size={12} />
                            <Badge variant="outline" className="text-xs text-red-600 border-red-400/50 dark:text-red-400 font-normal">
                              {t('anomaly')}
                            </Badge>
                          </span>
                        )}
                        {row.offline && (
                          <span title={t('offlineHint')}>
                            <Badge variant="outline" className="text-xs text-amber-600 border-amber-400/50 dark:text-amber-400 font-normal">
                              {t('offline')}
                            </Badge>
                          </span>
                        )}
                      </span>}
                </TableCell>
                <TableCell className="font-mono text-sm">
                  {isTotalRow ? null : openShift ? (
                    <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium">
                      <AlertCircle size={13} />
                      {t('openShift')}
                    </span>
                  ) : row.inferredCheckOut ? (
                    <span className="flex items-center gap-1 text-sky-600 dark:text-sky-400 font-mono text-sm">
                      {row.checkOut ? formatTime(row.checkOut) : '—'}
                      <span title={t('inferredCheckOut')}><AlertCircle size={12} className="ml-0.5 opacity-70" /></span>
                    </span>
                  ) : row.autoCheckout ? (
                    <span className="flex items-center gap-1 text-orange-500 dark:text-orange-400 font-mono text-sm">
                      {formatTime(row.checkOut!)}
                      <span title={t('autoCheckout')}><AlertTriangle size={12} className="ml-0.5 opacity-70" /></span>
                    </span>
                  ) : formatTime(row.checkOut!)}
                </TableCell>
                <TableCell className="text-sm">
                  {openShift ? (
                    <span className="text-muted-foreground">—</span>
                  ) : isTotalRow ? (
                    <span className={hasCorrection ? 'text-blue-600 dark:text-blue-400 font-medium' : 'font-semibold'}>
                      {row.effectiveHours}h
                      {hasCorrection && (
                        <span className="ml-1 text-xs text-muted-foreground line-through font-normal">
                          {row.calculatedHours}h
                        </span>
                      )}
                    </span>
                  ) : (
                    <span className="text-muted-foreground">{row.calculatedHours}h</span>
                  )}
                </TableCell>
                <TableCell>
                  {!isTotalRow && row.checkInLat != null ? (
                    <span className="flex items-center gap-1 whitespace-nowrap">
                      <a
                        href={`https://www.google.com/maps?q=${row.checkInLat},${row.checkInLng}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-1 text-xs font-mono hover:underline underline-offset-2 ${
                          row.checkInLocationValid === false
                            ? 'text-red-600 dark:text-red-400 hover:text-red-700'
                            : 'text-muted-foreground hover:text-primary'
                        }`}
                      >
                        <MapPin size={12} />
                        {row.checkInLat.toFixed(5)}, {row.checkInLng!.toFixed(5)}
                      </a>
                      {row.checkInLocationValid === false && (
                        <span title={t('outOfZoneCheckIn')}>
                          <XCircle size={12} className="text-red-500 shrink-0" />
                        </span>
                      )}
                    </span>
                  ) : (
                    <span className="text-muted-foreground text-xs">—</span>
                  )}
                </TableCell>
                <TableCell>
                  {!isTotalRow && row.checkOutLat != null ? (
                    <span className="flex items-center gap-1 whitespace-nowrap">
                      <a
                        href={`https://www.google.com/maps?q=${row.checkOutLat},${row.checkOutLng}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-1 text-xs font-mono hover:underline underline-offset-2 ${
                          row.checkOutLocationValid === false
                            ? 'text-red-600 dark:text-red-400 hover:text-red-700'
                            : 'text-muted-foreground hover:text-primary'
                        }`}
                      >
                        <MapPin size={12} />
                        {row.checkOutLat.toFixed(5)}, {row.checkOutLng!.toFixed(5)}
                      </a>
                      {row.checkOutLocationValid === false && (
                        <span title={t('outOfZoneCheckOut')}>
                          <XCircle size={12} className="text-red-500 shrink-0" />
                        </span>
                      )}
                    </span>
                  ) : (
                    <span className="text-muted-foreground text-xs">—</span>
                  )}
                </TableCell>
                <TableCell>
                  <span className="flex items-center">
                    {!isTotalRow && row.checkInId && (
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-7 w-7 text-muted-foreground hover:text-foreground"
                        title={t('details')}
                        onClick={() => setDetailsRow(row)}
                      >
                        <Info size={13} />
                      </Button>
                    )}
                    {(isTotalRow || (!isTotalRow && row.effectiveHours !== null)) && !openShift && (
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-7 w-7 text-muted-foreground hover:text-foreground"
                        title={t('correctHours')}
                        onClick={() => onCorrect(row)}
                      >
                        <Pencil size={13} />
                      </Button>
                    )}
                  </span>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>

      {/* Change site dialog */}
      <Dialog open={!!changeSiteRow} onOpenChange={(v) => !v && setChangeSiteRow(null)}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('changeSite')}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3 py-2">
            <p className="text-sm text-muted-foreground">
              {changeSiteRow?.workerName} — {changeSiteRow?.siteName}
            </p>
            <Select value={selectedSiteId} onValueChange={setSelectedSiteId}>
              <SelectTrigger>
                <SelectValue placeholder={t('selectSite')} />
              </SelectTrigger>
              <SelectContent>
                {sites
                  .filter((s) => s.id !== changeSiteRow?.siteId)
                  .map((s) => (
                    <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setChangeSiteRow(null)}>{tc('cancel')}</Button>
            <Button
              disabled={!selectedSiteId || changeSiteMutation.isPending}
              onClick={() => changeSiteRow && changeSiteMutation.mutate({
                checkInId: changeSiteRow.checkInId!,
                checkOutId: changeSiteRow.checkOutId,
                siteId: selectedSiteId,
              })}
            >
              {changeSiteMutation.isPending && <Loader2 size={14} className="animate-spin mr-2" />}
              {tc('save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AttendanceDetailsModal
        open={!!detailsRow}
        onClose={() => setDetailsRow(null)}
        checkInId={detailsRow?.checkInId ?? null}
        checkOutId={detailsRow?.checkOutId ?? null}
        t={t}
      />
    </>
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
  const [detailsRow, setDetailsRow] = useState<WorkedHoursRow | null>(null)
  const anomalyLabel = (reason: string | null) =>
    reason === 'DUPLICATE_CHECK_IN' ? t('anomalyDuplicateCheckIn')
    : reason === 'DUPLICATE_CHECK_OUT' ? t('anomalyDuplicateCheckOut')
    : reason === 'CHECKOUT_WITHOUT_CHECKIN' ? t('anomalyCheckoutWithoutCheckin')
    : ''

  return (
    <>
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
                      <TableHead className="text-xs">{t('company')}</TableHead>
                      <TableHead className="text-xs">{t('site')}</TableHead>
                      <TableHead className="text-xs">{t('date')}</TableHead>
                      <TableHead className="text-xs">{t('checkIn')}</TableHead>
                      <TableHead className="text-xs">{t('checkOut')}</TableHead>
                      <TableHead className="text-xs">{t('hoursWorked')}</TableHead>
                      <TableHead className="text-xs">{t('checkInLocation')}</TableHead>
                      <TableHead className="text-xs">{t('checkOutLocation')}</TableHead>
                      <TableHead></TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {worker.details.map((row, i) => {
                      const isTotalRow = row.pairIndex === -1
                      const openShift = !isTotalRow && row.checkOut === null && !row.inferredCheckOut
                      const hasCorrection = row.correctedHours !== null
                      return (
                        <TableRow
                          key={i}
                          className={
                            isTotalRow
                              ? 'bg-muted/40 font-semibold'
                              : openShift
                              ? 'bg-amber-50/40 dark:bg-amber-900/10'
                              : ''
                          }
                        >
                          <TableCell className="text-sm text-muted-foreground">{row.companyName ?? '—'}</TableCell>
                          <TableCell className="text-sm text-muted-foreground">{row.siteName}</TableCell>
                          <TableCell className="text-sm">{row.date}</TableCell>
                          <TableCell className="font-mono text-sm">
                            {isTotalRow
                              ? <span className="text-xs text-muted-foreground">{t('total')}</span>
                              : <span className="flex items-center gap-1.5">
                                  {row.checkIn ? formatTime(row.checkIn) : '—'}
                                  {row.anomalyReason && (
                                    <span title={anomalyLabel(row.anomalyReason)} className="text-red-600 dark:text-red-400">
                                      <AlertTriangle size={12} />
                                    </span>
                                  )}
                                  {row.offline && (
                                    <span title={t('offlineHint')} className="text-amber-600 dark:text-amber-400 text-[10px] font-medium">
                                      {t('offline')}
                                    </span>
                                  )}
                                </span>}
                          </TableCell>
                          <TableCell className="font-mono text-sm">
                            {isTotalRow ? null : openShift ? (
                              <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400 font-medium text-xs">
                                <AlertCircle size={12} />
                                {t('openShift')}
                              </span>
                            ) : row.inferredCheckOut ? (
                              <span className="flex items-center gap-1 text-sky-600 dark:text-sky-400 font-mono text-sm">
                                {row.checkOut ? formatTime(row.checkOut) : '—'}
                                <span title={t('inferredCheckOut')}><AlertCircle size={11} className="ml-0.5 opacity-70" /></span>
                              </span>
                            ) : row.autoCheckout ? (
                              <span className="flex items-center gap-1 text-orange-500 dark:text-orange-400 font-mono text-sm">
                                {formatTime(row.checkOut!)}
                                <span title={t('autoCheckout')}><AlertTriangle size={11} className="ml-0.5 opacity-70" /></span>
                              </span>
                            ) : formatTime(row.checkOut!)}
                          </TableCell>
                          <TableCell className="text-sm">
                            {openShift ? (
                              <span className="text-muted-foreground">—</span>
                            ) : isTotalRow ? (
                              <span className={hasCorrection ? 'text-blue-600 dark:text-blue-400 font-medium' : 'font-semibold'}>
                                {row.effectiveHours}h
                                {hasCorrection && (
                                  <span className="ml-1 text-xs text-muted-foreground line-through font-normal">
                                    {row.calculatedHours}h
                                  </span>
                                )}
                              </span>
                            ) : (
                              <span className="text-muted-foreground">{row.calculatedHours}h</span>
                            )}
                          </TableCell>
                          <TableCell>
                            {!isTotalRow && row.checkInLat != null ? (
                              <a
                                href={`https://www.google.com/maps?q=${row.checkInLat},${row.checkInLng}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-1 text-xs font-mono text-muted-foreground hover:text-primary hover:underline underline-offset-2 whitespace-nowrap"
                              >
                                <MapPin size={11} />
                                {row.checkInLat.toFixed(5)}, {row.checkInLng!.toFixed(5)}
                              </a>
                            ) : (
                              <span className="text-muted-foreground text-xs">—</span>
                            )}
                          </TableCell>
                          <TableCell>
                            {!isTotalRow && row.checkOutLat != null ? (
                              <a
                                href={`https://www.google.com/maps?q=${row.checkOutLat},${row.checkOutLng}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="flex items-center gap-1 text-xs font-mono text-muted-foreground hover:text-primary hover:underline underline-offset-2 whitespace-nowrap"
                              >
                                <MapPin size={11} />
                                {row.checkOutLat.toFixed(5)}, {row.checkOutLng!.toFixed(5)}
                              </a>
                            ) : (
                              <span className="text-muted-foreground text-xs">—</span>
                            )}
                          </TableCell>
                          <TableCell>
                            <span className="flex items-center">
                              {!isTotalRow && row.checkInId && (
                                <Button
                                  size="icon"
                                  variant="ghost"
                                  className="h-7 w-7 text-muted-foreground hover:text-foreground"
                                  title={t('details')}
                                  onClick={() => setDetailsRow(row)}
                                >
                                  <Info size={13} />
                                </Button>
                              )}
                              {(isTotalRow || (!isTotalRow && row.effectiveHours !== null)) && !openShift && (
                                <Button
                                  size="icon"
                                  variant="ghost"
                                  className="h-7 w-7 text-muted-foreground hover:text-foreground"
                                  title={t('correctHours')}
                                  onClick={() => onCorrect(row)}
                                >
                                  <Pencil size={13} />
                                </Button>
                              )}
                            </span>
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

    <AttendanceDetailsModal
      open={!!detailsRow}
      onClose={() => setDetailsRow(null)}
      checkInId={detailsRow?.checkInId ?? null}
      checkOutId={detailsRow?.checkOutId ?? null}
      t={t}
    />
    </>
  )
}

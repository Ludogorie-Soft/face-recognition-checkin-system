'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Download, Loader2, CheckCircle2, XCircle, AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table'
import { useSites } from '@/hooks/useSites'
import api from '@/lib/axios'

// ── Types ─────────────────────────────────────────────────────────────────────

interface AttendanceRow {
  workerId: string
  workerName: string
  siteName: string
  date: string          // "YYYY-MM-DD"
  type: 'CHECK_IN' | 'CHECK_OUT'
  recordedAt: string    // "YYYY-MM-DDTHH:mm:ss"
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

type Tab = 'attendance' | 'missing'

// ── Helpers ───────────────────────────────────────────────────────────────────

function today() {
  return new Date().toISOString().slice(0, 10)
}

function formatTime(dt: string) {
  return dt.slice(11, 16)  // "HH:mm" from "YYYY-MM-DDTHH:mm:ss"
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const t = useTranslations('reports')
  const tc = useTranslations('common')

  const { data: sites = [] } = useSites()

  const [tab, setTab] = useState<Tab>('attendance')
  const [siteId, setSiteId] = useState('')
  const [dateFrom, setDateFrom] = useState(today())
  const [dateTo, setDateTo] = useState(today())
  const [date, setDate] = useState(today())
  const [queryKey, setQueryKey] = useState(0)  // increment to trigger fetch
  const [exporting, setExporting] = useState(false)

  // ── Attendance query ───────────────────────────────────────────────────────

  const attendanceQuery = useQuery<AttendanceRow[]>({
    queryKey: ['reports', 'attendance', siteId, dateFrom, dateTo, queryKey],
    queryFn: () =>
      api.get('/api/reports/attendance', {
        params: { siteId, from: dateFrom, to: dateTo },
      }).then((r) => r.data),
    enabled: tab === 'attendance' && !!siteId && queryKey > 0,
  })

  // ── Missing query ──────────────────────────────────────────────────────────

  const missingQuery = useQuery<MissingRow[]>({
    queryKey: ['reports', 'missing', siteId, date, queryKey],
    queryFn: () =>
      api.get('/api/reports/missing', {
        params: { siteId, date },
      }).then((r) => r.data),
    enabled: tab === 'missing' && !!siteId && queryKey > 0,
  })

  // ── Actions ────────────────────────────────────────────────────────────────

  const handleSearch = () => {
    if (!siteId) {
      toast.warning(t('site'))
      return
    }
    setQueryKey((k) => k + 1)
  }

  const handleExport = async () => {
    if (!siteId) return
    setExporting(true)
    try {
      const response = await api.get('/api/reports/attendance/export', {
        params: { siteId, from: dateFrom, to: dateTo },
        responseType: 'blob',
      })
      const url = URL.createObjectURL(response.data)
      const a = document.createElement('a')
      a.href = url
      a.download = `attendance_${dateFrom}_${dateTo}.xlsx`
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error(tc('error'))
    } finally {
      setExporting(false)
    }
  }

  // ── Derived ────────────────────────────────────────────────────────────────

  const activeQuery = tab === 'attendance' ? attendanceQuery : missingQuery
  const isLoading = activeQuery.isLoading
  const rows = activeQuery.data ?? []

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>

      {/* Tabs */}
      <div className="flex gap-1 p-1 bg-muted rounded-lg w-fit">
        <button
          onClick={() => setTab('attendance')}
          className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
            tab === 'attendance'
              ? 'bg-background text-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          {t('attendance')}
        </button>
        <button
          onClick={() => setTab('missing')}
          className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
            tab === 'missing'
              ? 'bg-background text-foreground shadow-sm'
              : 'text-muted-foreground hover:text-foreground'
          }`}
        >
          {t('missing')}
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-end gap-3">
        {/* Site selector */}
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

        {tab === 'attendance' ? (
          <>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-muted-foreground">{t('dateFrom')}</label>
              <Input
                type="date"
                value={dateFrom}
                onChange={(e) => setDateFrom(e.target.value)}
                className="w-40"
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <label className="text-xs font-medium text-muted-foreground">{t('dateTo')}</label>
              <Input
                type="date"
                value={dateTo}
                onChange={(e) => setDateTo(e.target.value)}
                className="w-40"
              />
            </div>
          </>
        ) : (
          <div className="flex flex-col gap-1.5">
            <label className="text-xs font-medium text-muted-foreground">{t('date')}</label>
            <Input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="w-40"
            />
          </div>
        )}

        <Button onClick={handleSearch} disabled={isLoading}>
          {isLoading ? <Loader2 size={15} className="animate-spin mr-2" /> : null}
          {tc('search')}
        </Button>

        {tab === 'attendance' && (
          <Button
            variant="outline"
            onClick={handleExport}
            disabled={exporting || !siteId}
            className="gap-2"
          >
            {exporting
              ? <Loader2 size={15} className="animate-spin" />
              : <Download size={15} />
            }
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
      ) : (
        <MissingTable rows={rows as MissingRow[]} t={t} />
      )}
    </div>
  )
}

// ── Attendance table ───────────────────────────────────────────────────────────

function AttendanceTable({
  rows,
  t,
}: {
  rows: AttendanceRow[]
  t: ReturnType<typeof useTranslations>
}) {
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
              <TableCell className="text-sm font-mono">{formatTime(row.recordedAt)}</TableCell>
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
                    <CheckCircle2 size={15} />
                    В зоната
                  </span>
                ) : (
                  <span className="flex items-center gap-1.5 text-red-600 dark:text-red-400 text-sm font-medium">
                    <XCircle size={15} />
                    Извън зоната
                  </span>
                )}
              </TableCell>
              <TableCell className="text-sm">
                {row.manualOverride ? (
                  <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400">
                    <AlertTriangle size={13} />
                    {t('manual')}
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
  rows,
  t,
}: {
  rows: MissingRow[]
  t: ReturnType<typeof useTranslations>
}) {
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

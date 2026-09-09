'use client'

import { useState } from 'react'
import { useTranslations, useLocale } from 'next-intl'
import {
  Building2, Users, UserCheck, UserX, Loader2,
  Clock, TrendingUp, Activity, TriangleAlert, ShieldAlert, ClipboardEdit,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useDashboardStats, useDashboardExtended } from '@/hooks/useDashboard'
import type { DayAttendance, SiteAttendance, ActivityEntry, OutOfZoneEntry, OpenShiftEntry } from '@/hooks/useDashboard'
import { ManualAttendanceModal } from '@/components/dashboard/ManualAttendanceModal'

// ── StatCard ──────────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string
  value: number | undefined
  loading: boolean
  icon: React.ReactNode
  color: string
}

function StatCard({ label, value, loading, icon, color }: StatCardProps) {
  return (
    <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-6">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-muted-foreground">{label}</span>
        <span className={`rounded-lg p-2 ${color}`}>{icon}</span>
      </div>
      {loading
        ? <Loader2 size={24} className="animate-spin text-muted-foreground" />
        : <span className="text-3xl font-bold text-foreground">{value ?? 0}</span>
      }
    </div>
  )
}

// ── WeeklyChart ───────────────────────────────────────────────────────────────

function WeeklyChart({ thisWeek, lastWeek }: { thisWeek: DayAttendance[], lastWeek: DayAttendance[] }) {
  const locale = useLocale()
  const t = useTranslations('dashboard')

  const maxCount = Math.max(1, ...thisWeek.map(d => d.count), ...lastWeek.map(d => d.count))

  function dayLabel(dateStr: string) {
    return new Date(dateStr + 'T12:00:00').toLocaleDateString(locale, { weekday: 'short' })
  }

  return (
    <div className="rounded-xl border border-border bg-card p-6 flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-foreground flex items-center gap-2">
          <TrendingUp size={16} className="text-muted-foreground" />
          {t('weeklyAttendance')}
        </h2>
        <div className="flex items-center gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-primary/30 inline-block" />
            {t('lastWeek')}
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-3 rounded-sm bg-primary inline-block" />
            {t('thisWeek')}
          </span>
        </div>
      </div>

      <div className="flex items-end gap-2 h-36">
        {thisWeek.map((day, i) => {
          const last = lastWeek[i]
          const thisH = maxCount > 0 ? Math.max(2, Math.round((day.count / maxCount) * 100)) : 2
          const lastH = maxCount > 0 ? Math.max(2, Math.round(((last?.count ?? 0) / maxCount) * 100)) : 2
          return (
            <div key={day.date} className="flex-1 flex flex-col items-center gap-1.5 min-w-0">
              <div className="w-full flex items-end gap-0.5" style={{ height: '112px' }}>
                <div
                  className="flex-1 bg-primary/30 rounded-t-sm transition-all duration-500"
                  style={{ height: `${lastH}%` }}
                  title={`${t('lastWeek')}: ${last?.count ?? 0} ${t('workers')}`}
                />
                <div
                  className="flex-1 bg-primary rounded-t-sm transition-all duration-500"
                  style={{ height: `${thisH}%` }}
                  title={`${t('thisWeek')}: ${day.count} ${t('workers')}`}
                />
              </div>
              <span className="text-[10px] text-muted-foreground truncate w-full text-center">
                {dayLabel(day.date)}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ── SiteBreakdown ─────────────────────────────────────────────────────────────

function SiteBreakdown({ sites }: { sites: SiteAttendance[] }) {
  const t = useTranslations('dashboard')

  return (
    <div className="rounded-xl border border-border bg-card p-6 flex flex-col gap-4">
      <h2 className="font-semibold text-foreground flex items-center gap-2">
        <Building2 size={16} className="text-muted-foreground" />
        {t('siteBreakdown')}
      </h2>

      {sites.length === 0
        ? <p className="text-sm text-muted-foreground">{t('noSites')}</p>
        : (
          <div className="flex flex-col gap-3 h-80 overflow-y-auto">
            {sites.map(site => {
              const pct = site.totalWorkers > 0
                ? Math.round((site.presentCount / site.totalWorkers) * 100)
                : 0
              return (
                <div key={site.siteId} className="flex flex-col gap-1">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-medium text-foreground truncate pr-2">{site.siteName}</span>
                    <span className="text-muted-foreground shrink-0">
                      {site.presentCount}/{site.totalWorkers}
                    </span>
                  </div>
                  <div className="h-1.5 rounded-full bg-muted overflow-hidden">
                    <div
                      className="h-full rounded-full bg-primary transition-all duration-500"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                </div>
              )
            })}
          </div>
        )}
    </div>
  )
}

// ── RecentActivity ────────────────────────────────────────────────────────────

function RecentActivity({ entries }: { entries: ActivityEntry[] }) {
  const t = useTranslations('dashboard')
  const locale = useLocale()

  function formatTime(dt: string) {
    return new Date(dt).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
  }

  function formatDate(dt: string) {
    return new Date(dt).toLocaleDateString(locale, { day: 'numeric', month: 'short' })
  }

  const today = new Date().toDateString()

  return (
    <div className="rounded-xl border border-border bg-card p-6 flex flex-col gap-4">
      <h2 className="font-semibold text-foreground flex items-center gap-2">
        <Activity size={16} className="text-muted-foreground" />
        {t('recentActivity')}
      </h2>

      {entries.length === 0
        ? <p className="text-sm text-muted-foreground">{t('noActivity')}</p>
        : (
          <div className="flex flex-col divide-y divide-border h-80 overflow-y-auto">
            {entries.map((entry, i) => {
              const isCheckIn = entry.type === 'CHECK_IN'
              const dt = new Date(entry.recordedAt)
              const isToday = dt.toDateString() === today
              return (
                <div key={i} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${isCheckIn ? 'bg-green-500' : 'bg-orange-400'}`} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-foreground truncate">{entry.workerName}</p>
                    <p className="text-xs text-muted-foreground truncate">{entry.siteName}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-xs font-medium text-foreground">{formatTime(entry.recordedAt)}</p>
                    {!isToday && (
                      <p className="text-xs text-muted-foreground">{formatDate(entry.recordedAt)}</p>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
    </div>
  )
}

// ── Alerts ────────────────────────────────────────────────────────────────────

function Alerts({ autoCheckouts, outOfZoneCount, openGuardShifts }: {
  autoCheckouts: number
  outOfZoneCount: number
  openGuardShifts: OpenShiftEntry[]
}) {
  const t = useTranslations('dashboard')
  const hasAlerts = outOfZoneCount > 0 || autoCheckouts > 0 || openGuardShifts.length > 0

  return (
    <div className="rounded-xl border border-border bg-card p-6 flex flex-col gap-4">
      <h2 className="font-semibold text-foreground flex items-center gap-2">
        <TriangleAlert size={16} className="text-muted-foreground" />
        {t('alerts')}
      </h2>

      {!hasAlerts
        ? (
          <p className="text-sm text-green-600 dark:text-green-400 flex items-center gap-2">
            <UserCheck size={14} />
            {t('noAlerts')}
          </p>
        )
        : (
          <div className="flex flex-col gap-3 max-h-80 overflow-y-auto">
            {outOfZoneCount > 0 && (
              <div className="flex items-start gap-2 text-sm text-destructive">
                <ShieldAlert size={14} className="mt-0.5 shrink-0" />
                <span>
                  {outOfZoneCount === 1
                    ? '1 чекин извън зоната днес'
                    : `${outOfZoneCount} чекина извън зоната днес`}
                </span>
              </div>
            )}
            {autoCheckouts > 0 && (
              <div className="flex items-start gap-2 text-sm text-orange-600 dark:text-orange-400">
                <Clock size={14} className="mt-0.5 shrink-0" />
                <span>{t('autoCheckoutsLastNight', { count: autoCheckouts })}</span>
              </div>
            )}
            {openGuardShifts.map((s, i) => (
              <div key={i} className="flex items-start gap-2 text-sm text-amber-600 dark:text-amber-400">
                <Clock size={14} className="mt-0.5 shrink-0" />
                <span>
                  {t('openGuardShift', {
                    name: s.workerName,
                    site: s.siteName,
                    since: s.since.replace('T', ' ').slice(0, 16),
                  })}
                </span>
              </div>
            ))}
          </div>
        )}
    </div>
  )
}

// ── OutOfZoneList ─────────────────────────────────────────────────────────────

function OutOfZoneList({ entries }: { entries: OutOfZoneEntry[] }) {
  const t = useTranslations('dashboard')
  const locale = useLocale()

  function formatTime(dt: string) {
    return new Date(dt).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
  }

  return (
    <div className="rounded-xl border border-border bg-card p-6 flex flex-col gap-4">
      <h2 className="font-semibold text-foreground flex items-center gap-2">
        <ShieldAlert size={16} className="text-muted-foreground" />
        {t('outOfZoneToday')}
      </h2>

      {entries.length === 0
        ? (
          <p className="text-sm text-green-600 dark:text-green-400 flex items-center gap-2">
            <UserCheck size={14} />
            {t('noOutOfZone')}
          </p>
        )
        : (
          <div className="flex flex-col divide-y divide-border max-h-80 overflow-y-auto">
            {entries.map((entry, i) => (
              <div key={i} className="flex items-center gap-3 py-2.5 first:pt-0 last:pb-0">
                <span className="w-1.5 h-1.5 rounded-full bg-destructive shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">{entry.workerName}</p>
                  <p className="text-xs text-muted-foreground truncate">{entry.siteName}</p>
                </div>
                <p className="text-xs font-medium text-muted-foreground shrink-0">
                  {formatTime(entry.recordedAt)}
                </p>
              </div>
            ))}
          </div>
        )}
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const t = useTranslations('dashboard')
  const { data: stats, isLoading: statsLoading } = useDashboardStats()
  const { data: ext, isLoading: extLoading } = useDashboardExtended()
  const [manualOpen, setManualOpen] = useState(false)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-xl sm:text-2xl font-bold text-foreground">{t('title')}</h1>
        <Button
          variant="outline"
          size="sm"
          className="gap-2 shrink-0"
          onClick={() => setManualOpen(true)}
        >
          <ClipboardEdit size={15} />
          {t('manualAttendance')}
        </Button>
      </div>

      <ManualAttendanceModal open={manualOpen} onClose={() => setManualOpen(false)} />

      {/* Stat cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          label={t('totalSites')}
          value={stats?.totalSites}
          loading={statsLoading}
          icon={<Building2 size={18} className="text-blue-600 dark:text-blue-400" />}
          color="bg-blue-100 dark:bg-blue-900/30"
        />
        <StatCard
          label={t('totalWorkers')}
          value={stats?.totalWorkers}
          loading={statsLoading}
          icon={<Users size={18} className="text-violet-600 dark:text-violet-400" />}
          color="bg-violet-100 dark:bg-violet-900/30"
        />
        <StatCard
          label={t('presentToday')}
          value={stats?.presentToday}
          loading={statsLoading}
          icon={<UserCheck size={18} className="text-green-600 dark:text-green-400" />}
          color="bg-green-100 dark:bg-green-900/30"
        />
        <StatCard
          label={t('missingToday')}
          value={stats?.missingToday}
          loading={statsLoading}
          icon={<UserX size={18} className="text-red-600 dark:text-red-400" />}
          color="bg-red-100 dark:bg-red-900/30"
        />
      </div>

      {extLoading
        ? (
          <div className="flex justify-center py-16">
            <Loader2 size={28} className="animate-spin text-muted-foreground" />
          </div>
        )
        : ext && (
          <>
            {/* Weekly chart */}
            <WeeklyChart thisWeek={ext.thisWeek} lastWeek={ext.lastWeek} />

            {/* Site breakdown + Recent activity */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2">
                <SiteBreakdown sites={ext.sites} />
              </div>
              <RecentActivity entries={ext.recentActivity} />
            </div>

            {/* Alerts + Out-of-zone list */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <Alerts
                autoCheckouts={ext.autoCheckoutsLastNight}
                outOfZoneCount={ext.outOfZoneToday.length}
                openGuardShifts={ext.openGuardShifts ?? []}
              />
              <OutOfZoneList entries={ext.outOfZoneToday} />
            </div>
          </>
        )}
    </div>
  )
}

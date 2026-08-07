'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { PlayCircle, Loader2, CheckCircle2, Settings } from 'lucide-react'
import api from '@/lib/axios'

function today() {
  return new Date().toISOString().slice(0, 10)
}

function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

export default function SettingsPage() {
  const t = useTranslations('settings')

  const [from, setFrom] = useState(daysAgo(7))
  const [to, setTo] = useState(daysAgo(1))
  const [running, setRunning] = useState(false)
  const [result, setResult] = useState<number | null>(null)
  const [error, setError] = useState('')

  const trigger = async () => {
    setRunning(true)
    setResult(null)
    setError('')
    try {
      const res = await api.post<{ created: number }>(
        `/attendance/auto-checkout?from=${from}&to=${to}`
      )
      setResult(res.data.created)
    } catch {
      setError(t('triggerError'))
    } finally {
      setRunning(false)
    }
  }

  const inputClass =
    'h-9 rounded-md border border-input bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring'

  return (
    <div className="p-6 max-w-2xl mx-auto space-y-8">
      {/* Page header */}
      <div className="flex items-center gap-3">
        <Settings size={22} className="text-muted-foreground" />
        <h1 className="text-xl font-semibold text-foreground">{t('title')}</h1>
      </div>

      {/* Maintenance card */}
      <section className="rounded-xl border border-border bg-card shadow-sm">
        <div className="px-5 py-4 border-b border-border">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {t('maintenance')}
          </p>
        </div>

        <div className="p-5 space-y-4">
          <div>
            <h2 className="text-sm font-medium text-foreground mb-1">
              {t('autoCheckoutSection')}
            </h2>
            <p className="text-xs text-muted-foreground leading-relaxed">
              {t('autoCheckoutDesc')}
            </p>
          </div>

          <div className="flex flex-wrap gap-3 items-end">
            <div className="flex flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t('dateFrom')}</label>
              <input
                type="date"
                value={from}
                max={to}
                onChange={(e) => { setFrom(e.target.value); setResult(null); setError('') }}
                className={inputClass}
              />
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-xs text-muted-foreground">{t('dateTo')}</label>
              <input
                type="date"
                value={to}
                min={from}
                max={today()}
                onChange={(e) => { setTo(e.target.value); setResult(null); setError('') }}
                className={inputClass}
              />
            </div>
            <button
              onClick={trigger}
              disabled={running || !from || !to}
              className="flex items-center gap-2 h-9 px-4 rounded-md bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {running
                ? <Loader2 size={15} className="animate-spin" />
                : <PlayCircle size={15} />}
              {running ? t('triggering') : t('trigger')}
            </button>
          </div>

          {/* Result */}
          {result !== null && (
            <div className="flex items-center gap-2 text-sm text-emerald-600 dark:text-emerald-400">
              <CheckCircle2 size={15} />
              {result === 0 ? t('resultNone') : t('resultCreated', { count: result })}
            </div>
          )}
          {error && (
            <p className="text-sm text-destructive">{error}</p>
          )}
        </div>
      </section>
    </div>
  )
}

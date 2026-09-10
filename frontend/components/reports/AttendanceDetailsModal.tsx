'use client'

import { useQuery } from '@tanstack/react-query'
import { useTranslations } from 'next-intl'
import { Loader2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import api from '@/lib/axios'

interface AttendanceDetail {
  id: string
  workerName: string | null
  siteName: string | null
  type: string | null
  recordedAt: string | null
  syncedAt: string | null
  source: string | null
  ipAddress: string | null
  userAgent: string | null
  clientDeviceId: string | null
  appVersion: string | null
  createdOffline: boolean
  locationValid: boolean
  lat: number
  lng: number
  faceConfidence: number | null
  manualOverride: boolean
  managerName: string | null
  clientType: string | null
  ignored: boolean
  ignoredReason: string | null
}

interface Props {
  open: boolean
  onClose: () => void
  checkInId: string | null
  checkOutId: string | null
  t: ReturnType<typeof useTranslations>
}

function useDetail(id: string | null, open: boolean) {
  return useQuery<AttendanceDetail>({
    queryKey: ['attendance', 'details', id],
    queryFn: async () => (await api.get(`/api/attendance/${id}/details`)).data,
    enabled: open && !!id,
    staleTime: 60_000,
  })
}

export function AttendanceDetailsModal({ open, onClose, checkInId, checkOutId, t }: Props) {
  const checkIn = useDetail(checkInId, open)
  const checkOut = useDetail(checkOutId, open)

  const sourceLabel = (s: string | null) =>
    s === 'TERMINAL_FACE' ? t('sourceTerminalFace')
    : s === 'TERMINAL_MANUAL' ? t('sourceTerminalManual')
    : s === 'ADMIN_MANUAL' ? t('sourceAdminManual')
    : s === 'SCHEDULER_AUTO' ? t('sourceSchedulerAuto')
    : t('sourceLegacy')

  const fmt = (dt: string | null) => (dt ? dt.replace('T', ' ').slice(0, 19) : '—')

  const renderColumn = (label: string, q: ReturnType<typeof useDetail>, id: string | null) => {
    if (!id) return null
    const d = q.data
    const rows: Array<[string, React.ReactNode]> = d ? [
      [t('source'), sourceLabel(d.source)],
      [t('detailDevice'), d.clientDeviceId ?? '—'],
      [t('detailIp'), d.ipAddress ?? '—'],
      [t('detailAppVersion'), d.appVersion ?? '—'],
      [t('detailRecordedAt'), fmt(d.recordedAt)],
      [t('detailSyncedAt'), fmt(d.syncedAt)],
      [t('offline'), d.createdOffline ? t('yes') : t('no')],
      [t('detailReported'), d.clientType && d.clientType !== d.type
        ? `${d.clientType} → ${d.type}` : '—'],
      [t('locationValid'), d.locationValid ? t('inZone') : t('outOfZone')],
      [t('faceConfidence'), d.faceConfidence != null ? `${Math.round(d.faceConfidence)}%` : '—'],
      [t('manager'), d.managerName ?? '—'],
      [t('detailUserAgent'), <span key="ua" className="break-all text-[11px] leading-snug">{d.userAgent ?? '—'}</span>],
    ] : []

    return (
      <div className="flex-1 min-w-0">
        <p className="text-xs font-semibold text-foreground mb-2">{label}</p>
        {q.isLoading ? (
          <div className="flex items-center gap-2 text-muted-foreground text-sm py-4">
            <Loader2 size={14} className="animate-spin" /> {t('loading')}
          </div>
        ) : q.isError || !d ? (
          <p className="text-sm text-destructive">{t('detailError')}</p>
        ) : (
          <dl className="flex flex-col gap-1.5">
            {rows.map(([k, v]) => (
              <div key={k} className="grid grid-cols-[110px_1fr] gap-2 text-sm">
                <dt className="text-muted-foreground">{k}</dt>
                <dd className="font-medium text-foreground break-all">{v}</dd>
              </div>
            ))}
          </dl>
        )}
      </div>
    )
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('detailsTitle')}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col sm:flex-row gap-6 pt-2">
          {renderColumn(t('checkIn'), checkIn, checkInId)}
          {renderColumn(t('checkOut'), checkOut, checkOutId)}
        </div>
      </DialogContent>
    </Dialog>
  )
}

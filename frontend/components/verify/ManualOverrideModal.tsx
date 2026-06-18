'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { AlertTriangle } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import type { WorkerRecord } from '@/lib/db'

interface Props {
  open: boolean
  onClose: () => void
  workers: WorkerRecord[]
  onConfirm: (workerId: string, type: 'CHECK_IN' | 'CHECK_OUT') => void
}

export function ManualOverrideModal({ open, onClose, workers, onConfirm }: Props) {
  const t = useTranslations('verify')
  const tc = useTranslations('common')

  const [workerId, setWorkerId] = useState('')
  const [type, setType] = useState<'CHECK_IN' | 'CHECK_OUT'>('CHECK_IN')

  const handleConfirm = () => {
    if (!workerId) return
    onConfirm(workerId, type)
    setWorkerId('')
    onClose()
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle size={18} className="text-amber-500" />
            {t('manualConfirm')}
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
          <Select value={workerId} onValueChange={setWorkerId}>
            <SelectTrigger>
              <SelectValue placeholder={t('selectWorker')} />
            </SelectTrigger>
            <SelectContent>
              {workers.map((w) => (
                <SelectItem key={w.id} value={w.id}>
                  {w.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => setType('CHECK_IN')}
              className={`py-3 rounded-lg text-sm font-semibold border-2 transition-colors ${
                type === 'CHECK_IN'
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-border text-muted-foreground hover:border-primary/50'
              }`}
            >
              {t('checkIn')}
            </button>
            <button
              onClick={() => setType('CHECK_OUT')}
              className={`py-3 rounded-lg text-sm font-semibold border-2 transition-colors ${
                type === 'CHECK_OUT'
                  ? 'border-destructive bg-destructive/10 text-destructive'
                  : 'border-border text-muted-foreground hover:border-destructive/50'
              }`}
            >
              {t('checkOut')}
            </button>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{tc('cancel')}</Button>
          <Button onClick={handleConfirm} disabled={!workerId}>
            {tc('confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

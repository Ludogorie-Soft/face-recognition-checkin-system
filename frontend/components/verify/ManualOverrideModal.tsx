'use client'

import { useState, useMemo } from 'react'
import { useTranslations } from 'next-intl'
import { AlertTriangle, Search, Check } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
  const [search, setSearch] = useState('')

  const handleClose = () => {
    setWorkerId('')
    setSearch('')
    onClose()
  }

  const handleConfirm = () => {
    if (!workerId) return
    onConfirm(workerId, type)
    setWorkerId('')
    setSearch('')
    onClose()
  }

  const filteredWorkers = useMemo(() => {
    const sorted = [...workers].sort((a, b) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' }))
    const q = search.trim().toLowerCase()
    return q ? sorted.filter((w) => w.name.toLowerCase().includes(q)) : sorted
  }, [workers, search])

  return (
    <Dialog open={open} onOpenChange={(v) => !v && handleClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle size={18} className="text-amber-500" />
            {t('manualConfirm')}
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
          {/* Worker search + list */}
          <div className="flex flex-col gap-1">
            <div className="relative">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
              <Input
                placeholder={t('searchWorker')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-8 h-9 text-sm"
              />
            </div>
            <div className="max-h-44 overflow-y-auto rounded-md border border-input bg-background">
              {filteredWorkers.length === 0 ? (
                <p className="py-6 text-center text-sm text-muted-foreground">
                  {t('selectWorker')}
                </p>
              ) : (
                filteredWorkers.map((w) => (
                  <button
                    key={w.id}
                    onClick={() => setWorkerId(w.id)}
                    className={`w-full flex items-center justify-between px-3 py-2 text-sm text-left transition-colors hover:bg-muted ${
                      workerId === w.id ? 'bg-primary/10 text-primary font-medium' : 'text-foreground'
                    }`}
                  >
                    <span>{w.name}</span>
                    {workerId === w.id && <Check size={14} className="shrink-0 text-primary" />}
                  </button>
                ))
              )}
            </div>
          </div>

          {/* CHECK_IN / CHECK_OUT toggle */}
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
          <Button variant="outline" onClick={handleClose}>{tc('cancel')}</Button>
          <Button onClick={handleConfirm} disabled={!workerId}>
            {tc('confirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

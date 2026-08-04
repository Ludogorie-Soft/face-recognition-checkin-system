'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Loader2, Plus, Search, Trash2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useWorkers } from '@/hooks/useWorkers'
import { useCompanies, useAssignWorker, useRemoveWorker } from '@/hooks/useCompanies'
import { apiErrorMessage } from '@/lib/errors'
import type { CompanyResponse } from '@/types/company'

interface Props {
  open: boolean
  onClose: () => void
  company: CompanyResponse
}

export function CompanyWorkerModal({ open, onClose, company }: Props) {
  const t = useTranslations('companies')
  const tc = useTranslations('common')
  const tw = useTranslations('workers')
  const te = useTranslations('errors')

  const [search, setSearch] = useState('')

  const { data: allWorkers = [] } = useWorkers()
  const { data: allCompanies = [] } = useCompanies()
  const assignMutation = useAssignWorker(company.id)
  const removeMutation = useRemoveWorker(company.id)

  // Use live company data so assigned/unassigned lists update after each mutation
  const liveCompany = allCompanies.find((c) => c.id === company.id) ?? company
  const assignedIds = new Set(liveCompany.workers.map((w) => w.id))
  const workers = allWorkers.filter((w) => w.role === 'WORKER')
  const unassigned = workers
    .filter((w) => !assignedIds.has(w.id))
    .filter((w) => w.name.toLowerCase().includes(search.toLowerCase()))

  const handleAssign = async (workerId: string) => {
    try {
      await assignMutation.mutateAsync(workerId)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  const handleRemove = async (workerId: string) => {
    try {
      await removeMutation.mutateAsync(workerId)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  const busy = assignMutation.isPending || removeMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('manageWorkers')} — {company.name}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
          {/* Assigned workers */}
          {liveCompany.workers.length > 0 && (
            <div className="flex flex-col gap-1">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{t('assignedWorkers')}</p>
              <div className="flex flex-col divide-y divide-border rounded-md border border-border max-h-40 overflow-y-auto">
                {liveCompany.workers.map((w) => (
                  <div key={w.id} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm">{w.name}</span>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-7 w-7 text-muted-foreground hover:text-destructive"
                      disabled={busy}
                      onClick={() => handleRemove(w.id)}
                    >
                      {busy ? <Loader2 size={13} className="animate-spin" /> : <Trash2 size={13} />}
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Search + unassigned workers */}
          <div className="flex flex-col gap-2">
            <div className="relative">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder={tw('searchWorker')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-8 h-8 text-sm"
              />
            </div>
            {unassigned.length > 0 ? (
              <div className="flex flex-col divide-y divide-border rounded-md border border-border max-h-48 overflow-y-auto">
                {unassigned.map((w) => (
                  <div key={w.id} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm text-muted-foreground">{w.name}</span>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-7 w-7 text-muted-foreground hover:text-primary"
                      disabled={busy}
                      onClick={() => handleAssign(w.id)}
                    >
                      {busy ? <Loader2 size={13} className="animate-spin" /> : <Plus size={13} />}
                    </Button>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-2">
                {tw('allAssigned')}
              </p>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

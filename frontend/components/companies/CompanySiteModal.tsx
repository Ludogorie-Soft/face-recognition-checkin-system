'use client'

import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Loader2, Plus, Trash2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useSites } from '@/hooks/useSites'
import { useCompanies, useAssignSite, useRemoveSite } from '@/hooks/useCompanies'
import type { CompanyResponse } from '@/types/company'

interface Props {
  open: boolean
  onClose: () => void
  company: CompanyResponse
}

export function CompanySiteModal({ open, onClose, company }: Props) {
  const t = useTranslations('companies')
  const tc = useTranslations('common')

  const { data: allSites = [] } = useSites()
  const { data: allCompanies = [] } = useCompanies()
  const assignMutation = useAssignSite(company.id)
  const removeMutation = useRemoveSite(company.id)

  // Use live company data so assigned/unassigned lists update after each mutation
  const liveCompany = allCompanies.find((c) => c.id === company.id) ?? company
  const assignedIds = new Set(liveCompany.sites.map((s) => s.id))
  const unassigned = allSites.filter((s) => !assignedIds.has(s.id))

  const handleAssign = async (siteId: string) => {
    try {
      await assignMutation.mutateAsync(siteId)
    } catch {
      toast.error(tc('error'))
    }
  }

  const handleRemove = async (siteId: string) => {
    try {
      await removeMutation.mutateAsync(siteId)
    } catch {
      toast.error(tc('error'))
    }
  }

  const busy = assignMutation.isPending || removeMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t('manageSites')} — {company.name}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
          {/* Assigned sites */}
          {liveCompany.sites.length > 0 && (
            <div className="flex flex-col gap-1">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{t('assignedSites')}</p>
              <div className="flex flex-col divide-y divide-border rounded-md border border-border">
                {liveCompany.sites.map((s) => (
                  <div key={s.id} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm">{s.name}</span>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-7 w-7 text-muted-foreground hover:text-destructive"
                      disabled={busy}
                      onClick={() => handleRemove(s.id)}
                    >
                      {busy ? <Loader2 size={13} className="animate-spin" /> : <Trash2 size={13} />}
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Unassigned sites */}
          {unassigned.length > 0 && (
            <div className="flex flex-col gap-1">
              <p className="text-xs font-medium text-muted-foreground uppercase tracking-wide">{t('availableSites')}</p>
              <div className="flex flex-col divide-y divide-border rounded-md border border-border max-h-48 overflow-y-auto">
                {unassigned.map((s) => (
                  <div key={s.id} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm text-muted-foreground">{s.name}</span>
                    <Button
                      size="icon"
                      variant="ghost"
                      className="h-7 w-7 text-muted-foreground hover:text-primary"
                      disabled={busy}
                      onClick={() => handleAssign(s.id)}
                    >
                      {busy ? <Loader2 size={13} className="animate-spin" /> : <Plus size={13} />}
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {liveCompany.sites.length === 0 && unassigned.length === 0 && (
            <p className="text-sm text-muted-foreground text-center py-4">{tc('noData')}</p>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

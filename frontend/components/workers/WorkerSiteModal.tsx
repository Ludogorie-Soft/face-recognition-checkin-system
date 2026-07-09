'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Building2, X, Plus, Loader2, Search, CheckCircle2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { useSites } from '@/hooks/useSites'
import api from '@/lib/axios'
import { apiErrorMessage } from '@/lib/errors'
import type { UserResponse } from '@/types/user'
import type { SiteResponse } from '@/types/site'

interface Props {
  open: boolean
  onClose: () => void
  worker: UserResponse
}

export function WorkerSiteModal({ open, onClose, worker }: Props) {
  const tw = useTranslations('workers')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const [siteSearch, setSiteSearch] = useState('')
  const [pendingAdd, setPendingAdd] = useState<string | null>(null)
  const [pendingRemove, setPendingRemove] = useState<string | null>(null)

  const { data: allSites = [], isLoading } = useSites()
  const qc = useQueryClient()

  const refetch = () => {
    qc.invalidateQueries({ queryKey: ['sites'] })
    qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] })
  }

  // W3 fix: optimistic remove — instantly remove site from worker's list, rollback on error
  const removeMutation = useMutation({
    mutationFn: (siteId: string) => api.delete(`/api/sites/${siteId}/workers/${worker.id}`),
    onMutate: async (siteId) => {
      await qc.cancelQueries({ queryKey: ['sites'] })
      const previous = qc.getQueryData<SiteResponse[]>(['sites'])
      qc.setQueryData<SiteResponse[]>(['sites'], (old) =>
        old?.map((s) =>
          s.id === siteId
            ? { ...s, workers: s.workers.filter((w) => w.id !== worker.id) }
            : s
        ) ?? []
      )
      return { previous }
    },
    onError: (_err, _siteId, context) => {
      if (context?.previous) qc.setQueryData(['sites'], context.previous)
    },
    onSettled: () => refetch(),
  })

  // Optimistic assign — instantly add worker to site's list, rollback on error
  const assignMutation = useMutation({
    mutationFn: (siteId: string) => api.post(`/api/sites/${siteId}/workers/${worker.id}`),
    onMutate: async (siteId) => {
      await qc.cancelQueries({ queryKey: ['sites'] })
      const previous = qc.getQueryData<SiteResponse[]>(['sites'])
      qc.setQueryData<SiteResponse[]>(['sites'], (old) =>
        old?.map((s) =>
          s.id === siteId
            ? { ...s, workers: [...s.workers, worker] }
            : s
        ) ?? []
      )
      return { previous }
    },
    onError: (_err, _siteId, context) => {
      if (context?.previous) qc.setQueryData(['sites'], context.previous)
    },
    onSettled: () => refetch(),
  })

  const assignedSites = allSites.filter((s) => s.workers.some((w) => w.id === worker.id))
  const assignedIds = new Set(assignedSites.map((s) => s.id))

  const availableSites = allSites
    .filter((s) => !assignedIds.has(s.id))
    .filter((s) => s.name.toLowerCase().includes(siteSearch.toLowerCase()))

  const handleAdd = async (siteId: string) => {
    setPendingAdd(siteId)
    try {
      await assignMutation.mutateAsync(siteId)
      setSiteSearch('')
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    } finally {
      setPendingAdd(null)
    }
  }

  const handleRemove = async (siteId: string) => {
    setPendingRemove(siteId)
    try {
      await removeMutation.mutateAsync(siteId)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    } finally {
      setPendingRemove(null)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Building2 size={16} />
            {worker.name} — {tw('assignedSites')}
          </DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          {/* W2 fix: loading state while sites fetch */}
          {isLoading ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3].map((i) => <Skeleton key={i} className="h-10 rounded-lg" />)}
            </div>
          ) : (
            <>
              {/* Assigned sites */}
              <div className="flex flex-col gap-1.5">
                {assignedSites.length === 0 ? (
                  <p className="text-sm text-muted-foreground py-2">{tc('noData')}</p>
                ) : (
                  assignedSites.map((s) => (
                    <div key={s.id} className="flex items-center justify-between py-2 px-3 rounded-lg bg-muted/40">
                      <div className="flex items-center gap-2">
                        <CheckCircle2 size={13} className="text-green-500 shrink-0" />
                        <span className="text-sm font-medium text-foreground">{s.name}</span>
                        {s.address && (
                          <span className="text-xs text-muted-foreground truncate max-w-[140px]">{s.address}</span>
                        )}
                      </div>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-7 w-7 text-muted-foreground hover:text-destructive shrink-0"
                        disabled={pendingRemove === s.id}
                        onClick={() => handleRemove(s.id)}
                      >
                        {pendingRemove === s.id
                          ? <Loader2 size={13} className="animate-spin" />
                          : <X size={13} />}
                      </Button>
                    </div>
                  ))
                )}
              </div>

              {/* Search + add available sites */}
              <div className="pt-2 border-t border-border flex flex-col gap-2">
                <div className="relative">
                  <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    placeholder={tw('searchSite')}
                    value={siteSearch}
                    onChange={(e) => setSiteSearch(e.target.value)}
                    className="pl-8 text-sm h-9"
                  />
                </div>
                {siteSearch.trim() !== '' && (
                  <div className="flex flex-col gap-1 max-h-48 overflow-y-auto">
                    {availableSites.length === 0 ? (
                      <p className="text-xs text-muted-foreground px-1 py-2">{tc('noData')}</p>
                    ) : (
                      availableSites.map((s) => (
                        <div key={s.id} className="flex items-center justify-between py-1.5 px-3 rounded-lg hover:bg-muted/40 transition-colors">
                          <div className="flex items-center gap-2">
                            <span className="text-sm text-foreground">{s.name}</span>
                            {s.address && (
                              <span className="text-xs text-muted-foreground truncate max-w-[160px]">{s.address}</span>
                            )}
                          </div>
                          <Button
                            size="icon"
                            variant="ghost"
                            className="h-7 w-7 text-muted-foreground hover:text-primary shrink-0"
                            disabled={pendingAdd === s.id}
                            onClick={() => handleAdd(s.id)}
                          >
                            {pendingAdd === s.id
                              ? <Loader2 size={13} className="animate-spin" />
                              : <Plus size={13} />}
                          </Button>
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

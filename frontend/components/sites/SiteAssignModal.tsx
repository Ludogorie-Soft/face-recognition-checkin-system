'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { UserMinus, UserPlus, Loader2, ScanFace, Search } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import {
  useSite, useAssignWorker, useRemoveWorker,
} from '@/hooks/useSites'
import { useWorkers } from '@/hooks/useWorkers'
import type { SiteResponse } from '@/types/site'
import { apiErrorMessage } from '@/lib/errors'

interface Props {
  open: boolean
  onClose: () => void
  site: SiteResponse
}

export function SiteAssignModal({ open, onClose, site }: Props) {
  const ts = useTranslations('sites')
  const tc = useTranslations('common')
  const te = useTranslations('errors')
  const tw = useTranslations('workers')
  const [removing, setRemoving] = useState<string | null>(null)
  const [assigning, setAssigning] = useState<string | null>(null)
  const [workerSearch, setWorkerSearch] = useState('')

  const { data: detail, isLoading } = useSite(site.id)
  const { data: allWorkers = [] } = useWorkers('WORKER')

  const assignWorker = useAssignWorker(site.id)
  const removeWorker = useRemoveWorker(site.id)

  const currentWorkerIds = new Set((detail?.workers ?? []).map((u) => u.id))
  const unassignedWorkers = allWorkers.filter((u) => !currentWorkerIds.has(u.id))
  const availableWorkers = unassignedWorkers
    .filter((u) => u.name.toLowerCase().includes(workerSearch.toLowerCase()))

  const handleAssign = async (userId: string) => {
    setAssigning(userId)
    try {
      await assignWorker.mutateAsync(userId)
      setWorkerSearch('')
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    } finally {
      setAssigning(null)
    }
  }

  const handleRemove = async (userId: string) => {
    setRemoving(userId)
    try {
      await removeWorker.mutateAsync(userId)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    } finally {
      setRemoving(null)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{site.name} — {ts('workers')}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          {isLoading ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3].map((i) => <Skeleton key={i} className="h-10 rounded-lg" />)}
            </div>
          ) : (
            <>
              {/* Current workers */}
              <div className="flex flex-col gap-1.5">
                {(detail?.workers ?? []).length === 0 ? (
                  <p className="text-sm text-muted-foreground py-2">{tc('noData')}</p>
                ) : (
                  (detail?.workers ?? []).map((u) => (
                    <div key={u.id} className="flex items-center justify-between py-2 px-3 rounded-lg bg-muted/40">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-foreground">{u.name}</span>
                        {u.faceRegistered && (
                          <ScanFace size={13} className="text-green-500" />
                        )}
                        <span className="text-xs text-muted-foreground">{u.email}</span>
                      </div>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-7 w-7 text-muted-foreground hover:text-destructive"
                        disabled={removing === u.id}
                        onClick={() => handleRemove(u.id)}
                      >
                        {removing === u.id
                          ? <Loader2 size={13} className="animate-spin" />
                          : <UserMinus size={13} />}
                      </Button>
                    </div>
                  ))
                )}
              </div>

              {/* Add worker — searchable list */}
              <div className="pt-2 border-t border-border flex flex-col gap-2">
                {unassignedWorkers.length === 0 ? (
                  <p className="text-xs text-muted-foreground py-1">{tw('allAssigned')}</p>
                ) : (
                  <>
                    <div className="relative">
                      <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                      <Input
                        placeholder={tw('searchWorker')}
                        value={workerSearch}
                        onChange={(e) => setWorkerSearch(e.target.value)}
                        className="pl-8 text-sm h-9"
                      />
                    </div>
                    {workerSearch.trim() !== '' && (
                      <div className="flex flex-col gap-1 max-h-48 overflow-y-auto">
                        {availableWorkers.length === 0 ? (
                          <p className="text-xs text-muted-foreground px-1 py-2">{tc('noData')}</p>
                        ) : (
                          availableWorkers.map((u) => (
                            <div key={u.id} className="flex items-center justify-between py-1.5 px-3 rounded-lg hover:bg-muted/40 transition-colors">
                              <div className="flex items-center gap-2">
                                <span className="text-sm text-foreground">{u.name}</span>
                                {u.faceRegistered
                                  ? <ScanFace size={12} className="text-green-500" />
                                  : <span className="text-xs text-muted-foreground">(без лице)</span>}
                              </div>
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-7 w-7 text-muted-foreground hover:text-primary"
                                disabled={assigning === u.id}
                                onClick={() => handleAssign(u.id)}
                              >
                                {assigning === u.id
                                  ? <Loader2 size={13} className="animate-spin" />
                                  : <UserPlus size={13} />}
                              </Button>
                            </div>
                          ))
                        )}
                      </div>
                    )}
                  </>
                )}
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

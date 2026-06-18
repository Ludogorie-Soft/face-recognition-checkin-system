'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { UserMinus, UserPlus, Loader2, ScanFace } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
  useSite, useAssignManager, useRemoveManager, useAssignWorker, useRemoveWorker,
} from '@/hooks/useSites'
import { useWorkers } from '@/hooks/useWorkers'
import type { SiteResponse } from '@/types/site'
import type { UserResponse } from '@/types/user'
import { apiErrorMessage } from '@/lib/errors'

interface Props {
  open: boolean
  onClose: () => void
  site: SiteResponse
}

type Tab = 'managers' | 'workers'

function AssignSection({
  siteId,
  tab,
  current,
  available,
  onAssign,
  onRemove,
  assigning,
  removing,
}: {
  siteId: string
  tab: Tab
  current: UserResponse[]
  available: UserResponse[]
  onAssign: (id: string) => Promise<void>
  onRemove: (id: string) => Promise<void>
  assigning: boolean
  removing: string | null
}) {
  const tc = useTranslations('common')
  const ts = useTranslations('sites')
  const [selected, setSelected] = useState('')

  const handleAssign = async () => {
    if (!selected) return
    await onAssign(selected)
    setSelected('')
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Current assignments */}
      <div className="flex flex-col gap-2">
        {current.length === 0 ? (
          <p className="text-sm text-muted-foreground py-2">{tc('noData')}</p>
        ) : (
          current.map((u) => (
            <div key={u.id} className="flex items-center justify-between py-2 px-3 rounded-lg bg-muted/40">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-foreground">{u.name}</span>
                {tab === 'workers' && u.faceRegistered && (
                  <ScanFace size={13} className="text-green-500" />
                )}
                <span className="text-xs text-muted-foreground">{u.email}</span>
              </div>
              <Button
                size="icon"
                variant="ghost"
                className="h-7 w-7 text-muted-foreground hover:text-destructive"
                disabled={removing === u.id}
                onClick={() => onRemove(u.id)}
              >
                {removing === u.id
                  ? <Loader2 size={13} className="animate-spin" />
                  : <UserMinus size={13} />}
              </Button>
            </div>
          ))
        )}
      </div>

      {/* Add new */}
      {available.length > 0 && (
        <div className="flex gap-2 pt-2 border-t border-border">
          <Select value={selected} onValueChange={setSelected}>
            <SelectTrigger className="flex-1 text-sm">
              <SelectValue placeholder={tab === 'managers' ? ts('assignManager') : ts('assignWorker')} />
            </SelectTrigger>
            <SelectContent>
              {available.map((u) => (
                <SelectItem key={u.id} value={u.id}>
                  <span>{u.name}</span>
                  {tab === 'workers' && !u.faceRegistered && (
                    <span className="ml-2 text-xs text-muted-foreground">(без лице)</span>
                  )}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            size="sm"
            disabled={!selected || assigning}
            onClick={handleAssign}
          >
            {assigning ? <Loader2 size={14} className="animate-spin" /> : <UserPlus size={14} />}
          </Button>
        </div>
      )}
    </div>
  )
}

export function SiteAssignModal({ open, onClose, site }: Props) {
  const ts = useTranslations('sites')
  const tc = useTranslations('common')
  const te = useTranslations('errors')
  const [tab, setTab] = useState<Tab>('managers')
  const [removing, setRemoving] = useState<string | null>(null)

  const { data: detail, isLoading } = useSite(site.id)
  const { data: allManagers = [] } = useWorkers('MANAGER')
  const { data: allWorkers = [] } = useWorkers('WORKER')

  const assignManager = useAssignManager(site.id)
  const removeManager = useRemoveManager(site.id)
  const assignWorker = useAssignWorker(site.id)
  const removeWorker = useRemoveWorker(site.id)

  const currentManagerIds = new Set((detail?.managers ?? []).map((u) => u.id))
  const currentWorkerIds = new Set((detail?.workers ?? []).map((u) => u.id))

  const availableManagers = allManagers.filter((u) => !currentManagerIds.has(u.id))
  const availableWorkers = allWorkers.filter((u) => !currentWorkerIds.has(u.id))

  const handleAssign = async (userId: string) => {
    try {
      if (tab === 'managers') await assignManager.mutateAsync(userId)
      else await assignWorker.mutateAsync(userId)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  const handleRemove = async (userId: string) => {
    setRemoving(userId)
    try {
      if (tab === 'managers') await removeManager.mutateAsync(userId)
      else await removeWorker.mutateAsync(userId)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    } finally {
      setRemoving(null)
    }
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'managers', label: ts('managers') },
    { key: 'workers', label: ts('workers') },
  ]

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{site.name}</DialogTitle>
        </DialogHeader>

        {/* Tab switcher */}
        <div className="flex gap-1 p-1 bg-muted rounded-lg">
          {tabs.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`flex-1 py-1.5 text-sm font-medium rounded-md transition-colors ${
                tab === key
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              {label}
              {detail && (
                <Badge variant="secondary" className="ml-1.5 text-xs">
                  {key === 'managers' ? detail.managers.length : detail.workers.length}
                </Badge>
              )}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="min-h-[160px]">
          {isLoading ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3].map((i) => <Skeleton key={i} className="h-10 rounded-lg" />)}
            </div>
          ) : (
            <AssignSection
              siteId={site.id}
              tab={tab}
              current={tab === 'managers' ? (detail?.managers ?? []) : (detail?.workers ?? [])}
              available={tab === 'managers' ? availableManagers : availableWorkers}
              onAssign={handleAssign}
              onRemove={handleRemove}
              assigning={assignManager.isPending || assignWorker.isPending}
              removing={removing}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}

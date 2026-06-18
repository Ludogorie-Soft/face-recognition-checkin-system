'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Plus, Search, Pencil, Trash2, Camera, ScanFace } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { WorkerDialog } from '@/components/workers/WorkerDialog'
import { FaceRegisterModal } from '@/components/workers/FaceRegisterModal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useWorkers, useDeactivateWorker, useDeleteFace } from '@/hooks/useWorkers'
import { apiErrorMessage } from '@/lib/errors'
import type { UserResponse } from '@/types/user'

const ROLE_VARIANT: Record<string, 'default' | 'secondary' | 'destructive'> = {
  ADMIN: 'destructive',
  MANAGER: 'default',
  WORKER: 'secondary',
}

export default function WorkersPage() {
  const t = useTranslations('workers')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const [search, setSearch] = useState('')
  const [workerDialog, setWorkerDialog] = useState<{ open: boolean; user?: UserResponse | null }>({ open: false })
  const [faceModal, setFaceModal] = useState<UserResponse | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<UserResponse | null>(null)
  const [deleteFaceConfirm, setDeleteFaceConfirm] = useState<UserResponse | null>(null)

  const { data: users = [], isLoading } = useWorkers()
  const deactivate = useDeactivateWorker()
  const deleteFace = useDeleteFace(deleteFaceConfirm?.id ?? '')

  const filtered = users.filter((u) =>
    `${u.name} ${u.email}`.toLowerCase().includes(search.toLowerCase())
  )

  const handleDeactivate = async () => {
    if (!deleteConfirm) return
    try {
      await deactivate.mutateAsync(deleteConfirm.id)
      toast.success(tc('success'))
      setDeleteConfirm(null)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  const handleDeleteFace = async () => {
    if (!deleteFaceConfirm) return
    try {
      await deleteFace.mutateAsync()
      toast.success(tc('success'))
      setDeleteFaceConfirm(null)
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl sm:text-2xl font-bold text-foreground">{t('title')}</h1>
        <Button onClick={() => setWorkerDialog({ open: true, user: null })} className="shrink-0">
          <Plus size={16} />
          <span className="hidden sm:inline">{t('addWorker')}</span>
        </Button>
      </div>

      {/* Search */}
      <div className="relative max-w-sm">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
        <Input
          placeholder={tc('search')}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
        />
      </div>

      {/* Table */}
      <div className="rounded-xl border border-border overflow-x-auto">
        <table className="w-full text-sm min-w-[560px]">
          <thead>
            <tr className="bg-muted/50 text-muted-foreground text-left">
              <th className="px-4 py-3 font-medium">{t('name')}</th>
              <th className="px-4 py-3 font-medium">{t('email')}</th>
              <th className="px-4 py-3 font-medium">{t('phone')}</th>
              <th className="px-4 py-3 font-medium">{t('role')}</th>
              <th className="px-4 py-3 font-medium">Лице</th>
              <th className="px-4 py-3 font-medium text-right">{tc('actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading
              ? Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 6 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <Skeleton className="h-4 w-24" />
                      </td>
                    ))}
                  </tr>
                ))
              : filtered.length === 0
              ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground">
                    {tc('noData')}
                  </td>
                </tr>
              )
              : filtered.map((user) => (
                <tr key={user.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-4 py-3 font-medium text-foreground">{user.name}</td>
                  <td className="px-4 py-3 text-muted-foreground">{user.email}</td>
                  <td className="px-4 py-3 text-muted-foreground">{user.phone ?? '—'}</td>
                  <td className="px-4 py-3">
                    <Badge variant={ROLE_VARIANT[user.role]}>
                      {t(`roles.${user.role}`)}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    {user.faceRegistered ? (
                      <span className="inline-flex items-center gap-1 text-green-600 dark:text-green-400 text-xs font-medium">
                        <ScanFace size={14} />
                        {t('faceRegistered')}
                      </span>
                    ) : (
                      <span className="text-muted-foreground text-xs">{t('faceNotRegistered')}</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      {/* Register / delete face */}
                      {user.faceRegistered ? (
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8 text-muted-foreground hover:text-destructive"
                          title={t('deleteFace')}
                          onClick={() => setDeleteFaceConfirm(user)}
                        >
                          <ScanFace size={15} />
                        </Button>
                      ) : (
                        <Button
                          size="icon"
                          variant="ghost"
                          className="h-8 w-8 text-muted-foreground hover:text-primary"
                          title={t('registerFace')}
                          onClick={() => setFaceModal(user)}
                        >
                          <Camera size={15} />
                        </Button>
                      )}
                      {/* Edit */}
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-foreground"
                        onClick={() => setWorkerDialog({ open: true, user })}
                      >
                        <Pencil size={15} />
                      </Button>
                      {/* Deactivate */}
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                        onClick={() => setDeleteConfirm(user)}
                      >
                        <Trash2 size={15} />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {/* Dialogs */}
      <WorkerDialog
        open={workerDialog.open}
        user={workerDialog.user}
        onClose={() => setWorkerDialog({ open: false })}
      />

      {faceModal && (
        <FaceRegisterModal
          open={!!faceModal}
          worker={faceModal}
          onClose={() => setFaceModal(null)}
        />
      )}

      <ConfirmDialog
        open={!!deleteConfirm}
        title={t('deleteConfirm')}
        description={deleteConfirm ? `${deleteConfirm.name} (${deleteConfirm.email})` : ''}
        onConfirm={handleDeactivate}
        onClose={() => setDeleteConfirm(null)}
        loading={deactivate.isPending}
      />

      <ConfirmDialog
        open={!!deleteFaceConfirm}
        title={t('deleteFace')}
        description={deleteFaceConfirm ? `${deleteFaceConfirm.name}` : ''}
        onConfirm={handleDeleteFace}
        onClose={() => setDeleteFaceConfirm(null)}
        loading={deleteFace.isPending}
      />
    </div>
  )
}

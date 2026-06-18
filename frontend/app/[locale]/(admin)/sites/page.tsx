'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Plus, Search, Pencil, Trash2, Users, Clock, MapPin } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/skeleton'
import { SiteDialog } from '@/components/sites/SiteDialog'
import { SiteAssignModal } from '@/components/sites/SiteAssignModal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { useSites, useDeactivateSite } from '@/hooks/useSites'
import { apiErrorMessage } from '@/lib/errors'
import type { SiteResponse } from '@/types/site'

function formatTime(t: string | null): string {
  if (!t) return '—'
  return t.substring(0, 5)
}

export default function SitesPage() {
  const t = useTranslations('sites')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const [search, setSearch] = useState('')
  const [siteDialog, setSiteDialog] = useState<{ open: boolean; site?: SiteResponse | null }>({ open: false })
  const [assignModal, setAssignModal] = useState<SiteResponse | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<SiteResponse | null>(null)

  const { data: sites = [], isLoading } = useSites()
  const deactivate = useDeactivateSite()

  const filtered = sites.filter((s) =>
    `${s.name} ${s.address ?? ''}`.toLowerCase().includes(search.toLowerCase())
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

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl sm:text-2xl font-bold text-foreground">{t('title')}</h1>
        <Button onClick={() => setSiteDialog({ open: true, site: null })} className="shrink-0">
          <Plus size={16} />
          <span className="hidden sm:inline">{t('addSite')}</span>
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
        <table className="w-full text-sm min-w-[700px]">
          <thead>
            <tr className="bg-muted/50 text-muted-foreground text-left">
              <th className="px-4 py-3 font-medium">{t('name')}</th>
              <th className="px-4 py-3 font-medium">{t('address')}</th>
              <th className="px-4 py-3 font-medium">{t('coordinates')}</th>
              <th className="px-4 py-3 font-medium">{t('radius')}</th>
              <th className="px-4 py-3 font-medium">Смяна</th>
              <th className="px-4 py-3 font-medium">{t('managers')}</th>
              <th className="px-4 py-3 font-medium">{t('workers')}</th>
              <th className="px-4 py-3 font-medium text-right">{tc('actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 8 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <Skeleton className="h-4 w-20" />
                      </td>
                    ))}
                  </tr>
                ))
              : filtered.length === 0
              ? (
                <tr>
                  <td colSpan={8} className="px-4 py-8 text-center text-muted-foreground">
                    {tc('noData')}
                  </td>
                </tr>
              )
              : filtered.map((site) => (
                <tr key={site.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-4 py-3 font-medium text-foreground">
                    <div className="flex items-center gap-1.5">
                      <MapPin size={13} className="text-muted-foreground shrink-0" />
                      {site.name}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground max-w-[160px] truncate">
                    {site.address ?? '—'}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground text-xs font-mono">
                    {site.lat.toFixed(5)}, {site.lng.toFixed(5)}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {site.radiusMeters} м
                  </td>
                  <td className="px-4 py-3">
                    {site.workStartTime || site.workEndTime ? (
                      <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                        <Clock size={12} />
                        {formatTime(site.workStartTime)} – {formatTime(site.workEndTime)}
                      </span>
                    ) : (
                      <span className="text-muted-foreground text-xs">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant="secondary" className="text-xs">
                      {site.managers.length}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant="secondary" className="text-xs">
                      {site.workers.length}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-primary"
                        title={t('workers')}
                        onClick={() => setAssignModal(site)}
                      >
                        <Users size={15} />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-foreground"
                        onClick={() => setSiteDialog({ open: true, site })}
                      >
                        <Pencil size={15} />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                        onClick={() => setDeleteConfirm(site)}
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
      <SiteDialog
        open={siteDialog.open}
        site={siteDialog.site}
        onClose={() => setSiteDialog({ open: false })}
      />

      {assignModal && (
        <SiteAssignModal
          open={!!assignModal}
          site={assignModal}
          onClose={() => setAssignModal(null)}
        />
      )}

      <ConfirmDialog
        open={!!deleteConfirm}
        title={t('deleteSite')}
        description={t('deleteConfirm')}
        onConfirm={handleDeactivate}
        onClose={() => setDeleteConfirm(null)}
        loading={deactivate.isPending}
      />
    </div>
  )
}

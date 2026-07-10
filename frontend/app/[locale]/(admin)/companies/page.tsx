'use client'

import { useState } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Plus, Search, Pencil, Trash2, MapPin, Users } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { CompanyDialog } from '@/components/companies/CompanyDialog'
import { CompanySiteModal } from '@/components/companies/CompanySiteModal'
import { CompanyWorkerModal } from '@/components/companies/CompanyWorkerModal'
import { useCompanies, useDeactivateCompany } from '@/hooks/useCompanies'
import { apiErrorMessage } from '@/lib/errors'
import type { CompanyResponse } from '@/types/company'

export default function CompaniesPage() {
  const t = useTranslations('companies')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const [search, setSearch] = useState('')
  const [dialog, setDialog] = useState<{ open: boolean; company?: CompanyResponse | null }>({ open: false })
  const [siteModal, setSiteModal] = useState<CompanyResponse | null>(null)
  const [workerModal, setWorkerModal] = useState<CompanyResponse | null>(null)
  const [deleteConfirm, setDeleteConfirm] = useState<CompanyResponse | null>(null)

  const { data: companies = [], isLoading } = useCompanies()
  const deactivate = useDeactivateCompany()

  const filtered = companies.filter((c) =>
    `${c.name} ${c.registrationNumber ?? ''}`.toLowerCase().includes(search.toLowerCase())
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
        <Button onClick={() => setDialog({ open: true, company: null })} className="shrink-0">
          <Plus size={16} />
          <span className="hidden sm:inline">{t('addCompany')}</span>
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
        <table className="w-full text-sm min-w-[640px]">
          <thead>
            <tr className="bg-muted/50 text-muted-foreground text-left">
              <th className="px-4 py-3 font-medium">{t('name')}</th>
              <th className="px-4 py-3 font-medium">{t('registrationNumber')}</th>
              <th className="px-4 py-3 font-medium">{t('mol')}</th>
              <th className="px-4 py-3 font-medium">{t('phone')}</th>
              <th className="px-4 py-3 font-medium text-center">{t('sitesCount')}</th>
              <th className="px-4 py-3 font-medium text-center">{t('workersCount')}</th>
              <th className="px-4 py-3 font-medium text-right">{tc('actions')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading
              ? Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i}>
                    {Array.from({ length: 7 }).map((_, j) => (
                      <td key={j} className="px-4 py-3">
                        <Skeleton className="h-4 w-20" />
                      </td>
                    ))}
                  </tr>
                ))
              : filtered.length === 0
              ? (
                <tr>
                  <td colSpan={7} className="px-4 py-8 text-center text-muted-foreground">
                    {tc('noData')}
                  </td>
                </tr>
              )
              : filtered.map((company) => (
                <tr key={company.id} className="hover:bg-muted/30 transition-colors">
                  <td className="px-4 py-3 font-medium text-foreground">
                    <div>{company.name}</div>
                    {company.address && (
                      <div className="text-xs text-muted-foreground">{company.address}</div>
                    )}
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{company.registrationNumber || '—'}</td>
                  <td className="px-4 py-3 text-muted-foreground">{company.mol || '—'}</td>
                  <td className="px-4 py-3 text-muted-foreground">{company.phone || '—'}</td>
                  <td className="px-4 py-3 text-center">
                    <span className="text-sm font-medium">{company.sites.length}</span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    <span className="text-sm font-medium">{company.workers.length}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-foreground"
                        title={t('manageSites')}
                        onClick={() => setSiteModal(company)}
                      >
                        <MapPin size={15} />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-foreground"
                        title={t('manageWorkers')}
                        onClick={() => setWorkerModal(company)}
                      >
                        <Users size={15} />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-foreground"
                        onClick={() => setDialog({ open: true, company })}
                      >
                        <Pencil size={15} />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                        onClick={() => setDeleteConfirm(company)}
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

      <CompanyDialog
        open={dialog.open}
        company={dialog.company}
        onClose={() => setDialog({ open: false })}
      />

      {siteModal && (
        <CompanySiteModal
          open={!!siteModal}
          company={siteModal}
          onClose={() => setSiteModal(null)}
        />
      )}

      {workerModal && (
        <CompanyWorkerModal
          open={!!workerModal}
          company={workerModal}
          onClose={() => setWorkerModal(null)}
        />
      )}

      <ConfirmDialog
        open={!!deleteConfirm}
        title={t('deleteConfirm')}
        description={deleteConfirm?.name ?? ''}
        onConfirm={handleDeactivate}
        onClose={() => setDeleteConfirm(null)}
        loading={deactivate.isPending}
      />
    </div>
  )
}

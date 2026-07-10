'use client'

import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Loader2 } from 'lucide-react'
import { useCreateCompany, useUpdateCompany } from '@/hooks/useCompanies'
import { apiErrorMessage } from '@/lib/errors'
import type { CompanyRequest, CompanyResponse } from '@/types/company'

interface Props {
  open: boolean
  onClose: () => void
  company?: CompanyResponse | null
}

function Req() {
  return <span className="text-destructive ml-0.5">*</span>
}

export function CompanyDialog({ open, onClose, company }: Props) {
  const t = useTranslations('companies')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const { register, handleSubmit, reset, formState: { errors } } = useForm<CompanyRequest>()

  const isCreate = !company
  const createMutation = useCreateCompany()
  const updateMutation = useUpdateCompany(company?.id ?? '')

  useEffect(() => {
    if (open) {
      reset(company
        ? {
            name: company.name,
            address: company.address ?? '',
            phone: company.phone ?? '',
            email: company.email ?? '',
            registrationNumber: company.registrationNumber ?? '',
            mol: company.mol ?? '',
          }
        : { name: '', address: '', phone: '', email: '', registrationNumber: '', mol: '' }
      )
    }
  }, [open, company, reset])

  const onSubmit = async (data: CompanyRequest) => {
    try {
      if (isCreate) {
        await createMutation.mutateAsync(data)
      } else {
        await updateMutation.mutateAsync(data)
      }
      toast.success(tc('success'))
      onClose()
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  const loading = createMutation.isPending || updateMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isCreate ? t('addCompany') : t('editCompany')}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4 py-2">

          <div className="flex flex-col gap-1.5">
            <Label>{t('name')}<Req /></Label>
            <Input
              {...register('name', { required: true })}
              disabled={loading}
              className={errors.name ? 'border-destructive' : ''}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>{t('registrationNumber')}</Label>
            <Input {...register('registrationNumber')} disabled={loading} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>{t('mol')}</Label>
            <Input {...register('mol')} disabled={loading} />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>{t('address')}</Label>
            <Input {...register('address')} disabled={loading} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <Label>{t('phone')}</Label>
              <Input {...register('phone')} disabled={loading} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>{t('email')}</Label>
              <Input type="email" {...register('email')} disabled={loading} />
            </div>
          </div>

          <DialogFooter className="pt-2">
            <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
              {tc('cancel')}
            </Button>
            <Button type="submit" disabled={loading} className="gap-2">
              {loading && <Loader2 size={15} className="animate-spin" />}
              {tc('save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

'use client'

import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { ChevronDown, Loader2 } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuCheckboxItem,
} from '@/components/ui/dropdown-menu'
import { useCreateWorker, useUpdateWorker } from '@/hooks/useWorkers'
import { useCompanies } from '@/hooks/useCompanies'
import { apiErrorMessage } from '@/lib/errors'
import type { UserResponse, UserRequest, Role, ShiftType } from '@/types/user'

interface FormValues extends Omit<UserRequest, 'companyIds'> {
  confirmPassword?: string
}

interface Props {
  open: boolean
  onClose: () => void
  user?: UserResponse | null
}

const ROLES: Role[] = ['WORKER', 'ADMIN']
const SHIFT_TYPES: ShiftType[] = ['DAY', 'SHIFT_24H']

function Req() {
  return <span className="text-destructive ml-0.5">*</span>
}

export function WorkerDialog({ open, onClose, user }: Props) {
  const t = useTranslations('workers')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const { register, handleSubmit, setValue, watch, reset, formState: { errors } } = useForm<FormValues>({
    defaultValues: { role: 'WORKER', shiftType: 'DAY' },
  })

  const role = watch('role')
  const shiftType = watch('shiftType') ?? 'DAY'
  const isAdmin = role === 'ADMIN'
  const isCreate = !user

  const [selectedCompanyIds, setSelectedCompanyIds] = useState<string[]>([])

  const { data: companies = [] } = useCompanies()
  const createMutation = useCreateWorker()
  const updateMutation = useUpdateWorker(user?.id ?? '')

  useEffect(() => {
    if (open) {
      reset(user
        ? {
            name: user.name,
            email: user.email?.endsWith('@worker.local') ? '' : (user.email ?? ''),
            phone: user.phone ?? '',
            role: user.role,
            shiftType: user.shiftType ?? 'DAY',
          }
        : { name: '', email: '', phone: '', password: '', confirmPassword: '', role: 'WORKER', shiftType: 'DAY' }
      )
      setSelectedCompanyIds(user?.companies?.map((c) => c.id) ?? [])
    }
  }, [open, user, reset])

  const toggleCompany = (id: string) => {
    setSelectedCompanyIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    )
  }

  const onSubmit = async (data: FormValues) => {
    if (isCreate && isAdmin && data.password !== data.confirmPassword) {
      toast.warning(t('passwordMismatch'))
      return
    }

    const { confirmPassword, ...rest } = data

    const payload: UserRequest = {
      ...rest,
      companyIds: !isAdmin ? selectedCompanyIds : undefined,
    }

    if (isAdmin) {
      delete payload.companyIds
    } else {
      delete payload.password
    }

    try {
      if (user) {
        await updateMutation.mutateAsync(payload)
      } else {
        await createMutation.mutateAsync(payload)
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
          <DialogTitle>{user ? t('editWorker') : t('addWorker')}</DialogTitle>
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
            <Label>{t('email')}{isAdmin && <Req />}</Label>
            <Input
              type="email"
              {...register('email', { required: isAdmin })}
              disabled={loading}
              className={errors.email ? 'border-destructive' : ''}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label>{t('phone')}{isAdmin && <Req />}</Label>
            <Input
              {...register('phone', { required: isAdmin })}
              disabled={loading}
              className={errors.phone ? 'border-destructive' : ''}
            />
          </div>

          {/* Companies — multi-select dropdown, only for WORKER */}
          {!isAdmin && companies.length > 0 && (
            <div className="flex flex-col gap-1.5">
              <Label>{t('companiesLabel')}</Label>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button
                    type="button"
                    variant="outline"
                    disabled={loading}
                    className="w-full justify-between font-normal"
                  >
                    <span className="truncate">
                      {selectedCompanyIds.length === 0
                        ? t('selectCompany')
                        : companies
                            .filter((c) => selectedCompanyIds.includes(c.id))
                            .map((c) => c.name)
                            .join(', ')}
                    </span>
                    <ChevronDown size={15} className="ml-2 shrink-0 text-muted-foreground" />
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent className="w-[--radix-dropdown-menu-trigger-width]">
                  {companies.map((c) => (
                    <DropdownMenuCheckboxItem
                      key={c.id}
                      checked={selectedCompanyIds.includes(c.id)}
                      onCheckedChange={() => toggleCompany(c.id)}
                    >
                      {c.name}
                    </DropdownMenuCheckboxItem>
                  ))}
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}

          {isCreate && isAdmin && (
            <>
              <div className="flex flex-col gap-1.5">
                <Label>{t('password')}<Req /></Label>
                <Input
                  type="password"
                  {...register('password', { required: true })}
                  disabled={loading}
                  className={errors.password ? 'border-destructive' : ''}
                />
              </div>
              <div className="flex flex-col gap-1.5">
                <Label>{t('confirmPassword')}<Req /></Label>
                <Input
                  type="password"
                  {...register('confirmPassword', { required: true })}
                  disabled={loading}
                  className={errors.confirmPassword ? 'border-destructive' : ''}
                />
              </div>
            </>
          )}

          <div className="flex flex-col gap-1.5">
            <Label>{t('role')}</Label>
            <Select value={role} onValueChange={(v) => setValue('role', v as Role)} disabled={loading || !isCreate}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ROLES.map((r) => (
                  <SelectItem key={r} value={r}>
                    {t(`roles.${r}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {!isAdmin && (
            <div className="flex flex-col gap-1.5">
              <Label>{t('shiftType')}</Label>
              <Select
                value={shiftType}
                onValueChange={(v) => setValue('shiftType', v as ShiftType)}
                disabled={loading}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SHIFT_TYPES.map((st) => (
                    <SelectItem key={st} value={st}>
                      {t(`shiftTypes.${st}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">{t('shiftTypeHint')}</p>
            </div>
          )}

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

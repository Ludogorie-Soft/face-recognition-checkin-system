'use client'

import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Loader2 } from 'lucide-react'
import { useCreateWorker, useUpdateWorker } from '@/hooks/useWorkers'
import { apiErrorMessage } from '@/lib/errors'
import type { UserResponse, UserRequest, Role } from '@/types/user'

interface FormValues extends UserRequest {
  confirmPassword?: string
}

interface Props {
  open: boolean
  onClose: () => void
  user?: UserResponse | null
}

const ROLES: Role[] = ['WORKER', 'ADMIN']

function Req() {
  return <span className="text-destructive ml-0.5">*</span>
}

export function WorkerDialog({ open, onClose, user }: Props) {
  const t = useTranslations('workers')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const { register, handleSubmit, setValue, watch, reset, formState: { errors } } = useForm<FormValues>({
    defaultValues: { role: 'WORKER' },
  })

  const role = watch('role')
  const isAdmin = role === 'ADMIN'
  const isCreate = !user

  const createMutation = useCreateWorker()
  const updateMutation = useUpdateWorker(user?.id ?? '')

  useEffect(() => {
    if (open) {
      reset(user
        ? {
            name: user.name,
            // Hide auto-generated placeholder emails from the form
            email: user.email?.endsWith('@worker.local') ? '' : (user.email ?? ''),
            phone: user.phone ?? '',
            company: user.company ?? '',
            role: user.role,
          }
        : { name: '', email: '', phone: '', company: '', password: '', confirmPassword: '', role: 'WORKER' }
      )
    }
  }, [open, user, reset])

  const onSubmit = async (data: FormValues) => {
    if (isCreate && isAdmin && data.password !== data.confirmPassword) {
      toast.warning(t('passwordMismatch'))
      return
    }

    const { confirmPassword, ...payload } = data

    // Company is only for workers; password is never sent for workers
    if (payload.role === 'ADMIN') {
      delete payload.company
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

          {/* Name — always required */}
          <div className="flex flex-col gap-1.5">
            <Label>{t('name')}<Req /></Label>
            <Input
              {...register('name', { required: true })}
              disabled={loading}
              className={errors.name ? 'border-destructive' : ''}
            />
          </div>

          {/* Email — required for ADMIN, optional for WORKER */}
          <div className="flex flex-col gap-1.5">
            <Label>{t('email')}{isAdmin && <Req />}</Label>
            <Input
              type="email"
              {...register('email', { required: isAdmin })}
              disabled={loading}
              className={errors.email ? 'border-destructive' : ''}
            />
          </div>

          {/* Phone — required for ADMIN, optional for WORKER */}
          <div className="flex flex-col gap-1.5">
            <Label>{t('phone')}{isAdmin && <Req />}</Label>
            <Input
              {...register('phone', { required: isAdmin })}
              disabled={loading}
              className={errors.phone ? 'border-destructive' : ''}
            />
          </div>

          {/* Company — only for WORKER, always optional */}
          {!isAdmin && (
            <div className="flex flex-col gap-1.5">
              <Label>{t('company')}</Label>
              <Input {...register('company')} disabled={loading} />
            </div>
          )}

          {/* Password — required for new ADMIN only */}
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

          {/* Role */}
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

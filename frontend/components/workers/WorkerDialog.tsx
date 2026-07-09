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

interface Props {
  open: boolean
  onClose: () => void
  user?: UserResponse | null
}

const ROLES: Role[] = ['WORKER', 'ADMIN']

export function WorkerDialog({ open, onClose, user }: Props) {
  const t = useTranslations('workers')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const { register, handleSubmit, setValue, watch, reset, formState: { errors } } = useForm<UserRequest>({
    defaultValues: { role: 'WORKER' },
  })

  const role = watch('role')

  const createMutation = useCreateWorker()
  const updateMutation = useUpdateWorker(user?.id ?? '')

  useEffect(() => {
    if (open) {
      reset(user
        ? { name: user.name, email: user.email, phone: user.phone ?? '', role: user.role }
        : { name: '', email: '', phone: '', password: '', role: 'WORKER' }
      )
    }
  }, [open, user, reset])

  const onSubmit = async (data: UserRequest) => {
    try {
      if (user) {
        await updateMutation.mutateAsync(data)
        toast.success(tc('success'))
      } else {
        await createMutation.mutateAsync(data)
        toast.success(tc('success'))
      }
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
            <Label>{t('name')}</Label>
            <Input
              {...register('name', { required: true })}
              disabled={loading}
              className={errors.name ? 'border-destructive' : ''}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>{t('email')}</Label>
            <Input
              type="email"
              {...register('email', { required: true })}
              disabled={loading}
              className={errors.email ? 'border-destructive' : ''}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label>{t('phone')}</Label>
            <Input {...register('phone')} disabled={loading} />
          </div>
          {!user && role !== 'WORKER' && (
            <div className="flex flex-col gap-1.5">
              <Label>{t('password')}</Label>
              <Input
                type="password"
                {...register('password', { required: true })}
                disabled={loading}
                className={errors.password ? 'border-destructive' : ''}
              />
            </div>
          )}
          <div className="flex flex-col gap-1.5">
            <Label>{t('role')}</Label>
            <Select value={role} onValueChange={(v) => setValue('role', v as Role)} disabled={loading}>
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

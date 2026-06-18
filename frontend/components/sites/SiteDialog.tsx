'use client'

import { useEffect, useState } from 'react'
import dynamic from 'next/dynamic'
import { useForm, Controller } from 'react-hook-form'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { MapPin, Loader2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { TimePicker } from './TimePicker'
import { useCreateSite, useUpdateSite } from '@/hooks/useSites'
import { apiErrorMessage } from '@/lib/errors'
import type { SiteResponse, SiteRequest } from '@/types/site'

// Leaflet uses window — must be loaded client-side only
const MapPicker = dynamic(
  () => import('./MapPicker').then((m) => m.MapPicker),
  { ssr: false, loading: () => (
    <div className="h-[280px] rounded-lg bg-muted animate-pulse" />
  )}
)

interface Props {
  open: boolean
  onClose: () => void
  site?: SiteResponse | null
}

function toTimeInput(t: string | null | undefined): string {
  if (!t) return ''
  return t.substring(0, 5)
}

function toLocalTime(t: string): string | null {
  if (!t) return null
  return t.length === 5 ? `${t}:00` : t
}

type FormValues = {
  name: string
  address: string
  lat: number | null
  lng: number | null
  radiusMeters: string
  workStartTime: string
  workEndTime: string
}

export function SiteDialog({ open, onClose, site }: Props) {
  const t = useTranslations('sites')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const { register, handleSubmit, reset, watch, setValue, control, formState: { errors } } = useForm<FormValues>()

  const createMutation = useCreateSite()
  const updateMutation = useUpdateSite(site?.id ?? '')

  const lat = watch('lat')
  const lng = watch('lng')
  const radius = parseInt(watch('radiusMeters') || '200')

  useEffect(() => {
    if (open) {
      reset(site ? {
        name: site.name,
        address: site.address ?? '',
        lat: site.lat,
        lng: site.lng,
        radiusMeters: String(site.radiusMeters),
        workStartTime: toTimeInput(site.workStartTime),
        workEndTime: toTimeInput(site.workEndTime),
      } : {
        name: '', address: '', lat: null, lng: null,
        radiusMeters: '200', workStartTime: '', workEndTime: '',
      })
    }
  }, [open, site, reset])

  const onSubmit = async (data: FormValues) => {
    if (data.lat == null || data.lng == null) {
      toast.error(t('coordinates'))
      return
    }
    const body: SiteRequest = {
      name: data.name,
      address: data.address || undefined,
      lat: data.lat,
      lng: data.lng,
      radiusMeters: data.radiusMeters ? parseInt(data.radiusMeters) : 200,
      workStartTime: toLocalTime(data.workStartTime),
      workEndTime: toLocalTime(data.workEndTime),
    }
    try {
      if (site) {
        await updateMutation.mutateAsync(body)
      } else {
        await createMutation.mutateAsync(body)
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
      <DialogContent className="sm:max-w-xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{site ? t('editSite') : t('addSite')}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4 py-2">
          {/* Name */}
          <div className="flex flex-col gap-1.5">
            <Label>{t('name')}</Label>
            <Input
              {...register('name', { required: true })}
              disabled={loading}
              className={errors.name ? 'border-destructive' : ''}
            />
          </div>

          {/* Address */}
          <div className="flex flex-col gap-1.5">
            <Label>{t('address')}</Label>
            <Input {...register('address')} disabled={loading} />
          </div>

          {/* Map */}
          <div className="flex flex-col gap-1.5">
            <Label className="flex items-center gap-1.5">
              <MapPin size={14} />
              {t('coordinates')}
            </Label>
            <p className="text-xs text-muted-foreground -mt-1">
              {t('clickMapToSelect')}
            </p>
            <MapPicker
              lat={lat ?? null}
              lng={lng ?? null}
              radius={radius}
              onChange={(newLat, newLng) => {
                setValue('lat', newLat, { shouldValidate: true })
                setValue('lng', newLng, { shouldValidate: true })
              }}
            />
            {/* Coordinate display */}
            {lat != null && lng != null && (
              <p className="text-xs text-muted-foreground text-center">
                {lat.toFixed(6)}, {lng.toFixed(6)}
              </p>
            )}
          </div>

          {/* Radius */}
          <div className="flex flex-col gap-1.5">
            <Label>{t('radius')}</Label>
            <Input
              type="number"
              min={50}
              max={5000}
              {...register('radiusMeters')}
              disabled={loading}
            />
          </div>

          {/* Work hours */}
          <div className="grid grid-cols-2 gap-3">
            <div className="flex flex-col gap-1.5">
              <Label>{t('workStart')}</Label>
              <Controller
                control={control}
                name="workStartTime"
                render={({ field }) => (
                  <TimePicker
                    value={field.value}
                    onChange={field.onChange}
                    disabled={loading}
                  />
                )}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>{t('workEnd')}</Label>
              <Controller
                control={control}
                name="workEndTime"
                render={({ field }) => (
                  <TimePicker
                    value={field.value}
                    onChange={field.onChange}
                    disabled={loading}
                  />
                )}
              />
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

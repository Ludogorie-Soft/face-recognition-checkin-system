'use client'

import { useEffect, useState } from 'react'
import dynamic from 'next/dynamic'
import { useForm, Controller } from 'react-hook-form'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { MapPin, Loader2, Trash2 } from 'lucide-react'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { TimePicker } from './TimePicker'
import { useCreateSite, useUpdateSite, useMoveSiteCompany } from '@/hooks/useSites'
import { useCompanies } from '@/hooks/useCompanies'
import { apiErrorMessage } from '@/lib/errors'
import type { SiteResponse, SiteRequest } from '@/types/site'
import type { CheckpointDraft } from './MapPicker'

// Leaflet uses window — must be loaded client-side only
const MapPicker = dynamic(
  () => import('./MapPicker').then((m) => m.MapPicker),
  {
    ssr: false,
    loading: () => (
      <div className="h-[380px] rounded-lg bg-muted animate-pulse" />
    ),
  }
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
  workStartTime: string
  workEndTime: string
}

export function SiteDialog({ open, onClose, site }: Props) {
  const t = useTranslations('sites')
  const tc = useTranslations('common')
  const te = useTranslations('errors')

  const { register, handleSubmit, reset, control, formState: { errors } } = useForm<FormValues>()

  const createMutation = useCreateSite()
  const updateMutation = useUpdateSite(site?.id ?? '')
  const moveCompanyMutation = useMoveSiteCompany()
  const { data: companies = [] } = useCompanies()

  const [checkpoints, setCheckpoints] = useState<CheckpointDraft[]>([])
  const [selectedCpId, setSelectedCpId] = useState<string | null>(null)
  const [companyId, setCompanyId] = useState('')

  useEffect(() => {
    if (!open) return
    reset({
      name: site?.name ?? '',
      address: site?.address ?? '',
      workStartTime: toTimeInput(site?.workStartTime),
      workEndTime: toTimeInput(site?.workEndTime),
    })

    // For edit: pre-select the site's current company. For create: clear.
    // `companies` is intentionally read from the current closure but omitted from deps —
    // we only want to re-initialise when the dialog opens or switches sites, not on every
    // background cache refresh.
    if (site) {
      const currentCo = companies.find((c) => c.sites.some((s) => s.id === site.id))
      setCompanyId(currentCo?.id ?? '')
    } else {
      setCompanyId('')
    }

    if (site?.checkpoints?.length) {
      const drafts: CheckpointDraft[] = site.checkpoints.map((cp) => ({
        localId: cp.id,
        id: cp.id,
        name: cp.name ?? '',
        lat: cp.lat,
        lng: cp.lng,
        radiusMeters: cp.radiusMeters,
      }))
      setCheckpoints(drafts)
      setSelectedCpId(drafts[0].localId)
    } else {
      setCheckpoints([])
      setSelectedCpId(null)
    }
  }, [open, site, reset]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleAddCheckpoint = (lat: number, lng: number) => {
    const localId = crypto.randomUUID()
    const newCp: CheckpointDraft = {
      localId,
      id: null,
      name: '',
      lat,
      lng,
      radiusMeters: 200,
    }
    setCheckpoints((prev) => [...prev, newCp])
    setSelectedCpId(localId)
  }

  const handleMoveCheckpoint = (localId: string, lat: number, lng: number) => {
    setCheckpoints((prev) =>
      prev.map((cp) => (cp.localId === localId ? { ...cp, lat, lng } : cp))
    )
  }

  const handleUpdateCheckpoint = (localId: string, field: 'name' | 'radiusMeters', value: string) => {
    setCheckpoints((prev) =>
      prev.map((cp) =>
        cp.localId === localId
          ? {
              ...cp,
              [field]:
                field === 'radiusMeters'
                  ? value === '' ? cp.radiusMeters : (parseInt(value) || cp.radiusMeters)
                  : value,
            }
          : cp
      )
    )
  }

  const handleDeleteCheckpoint = (localId: string) => {
    setCheckpoints((prev) => {
      const next = prev.filter((cp) => cp.localId !== localId)
      if (selectedCpId === localId) {
        setSelectedCpId(next.length > 0 ? next[0].localId : null)
      }
      return next
    })
  }

  const onSubmit = async (data: FormValues) => {
    if (!site && !companyId) {
      toast.error(t('companyRequired'))
      return
    }
    if (checkpoints.length === 0) {
      toast.error(t('noCheckpointsError'))
      return
    }
    if (checkpoints.some((cp) => !cp.radiusMeters || cp.radiusMeters < 1)) {
      toast.error(t('checkpointRadiusError'))
      return
    }

    const first = checkpoints[0]
    const body: SiteRequest = {
      name: data.name,
      address: data.address || undefined,
      lat: first.lat,
      lng: first.lng,
      radiusMeters: first.radiusMeters,
      workStartTime: toLocalTime(data.workStartTime),
      workEndTime: toLocalTime(data.workEndTime),
      checkpoints: checkpoints.map((cp) => ({
        id: cp.id ?? null,
        name: cp.name || null,
        lat: cp.lat,
        lng: cp.lng,
        radiusMeters: cp.radiusMeters,
      })),
      ...(!site && { companyId }),
    }

    try {
      if (site) {
        await updateMutation.mutateAsync(body)

        // Reassign company if the user selected a different one
        const currentCoId = companies.find((c) => c.sites.some((s) => s.id === site.id))?.id ?? ''
        if (companyId && companyId !== currentCoId) {
          await moveCompanyMutation.mutateAsync({
            siteId: site.id,
            fromCompanyId: currentCoId || null,
            toCompanyId: companyId,
          })
        }
      } else {
        await createMutation.mutateAsync(body)
      }
      toast.success(tc('success'))
      onClose()
    } catch (err) {
      toast.error(apiErrorMessage(te, err))
    }
  }

  const loading = createMutation.isPending || updateMutation.isPending || moveCompanyMutation.isPending

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{site ? t('editSite') : t('addSite')}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4 py-2">
          {/* Company — required on create, optional change on edit */}
          {companies.length > 0 && (
            <div className="flex flex-col gap-1.5">
              <Label>
                {t('company')}
                {!site && <span className="text-destructive ml-0.5">*</span>}
              </Label>
              <Select value={companyId} onValueChange={setCompanyId} disabled={loading}>
                <SelectTrigger className={!companyId && !site ? 'border-muted-foreground/40' : ''}>
                  <SelectValue placeholder={t('selectCompany')} />
                </SelectTrigger>
                <SelectContent>
                  {companies.map((c) => (
                    <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {site && (
                <p className="text-xs text-muted-foreground -mt-0.5">{t('changeCompanyHint')}</p>
              )}
            </div>
          )}

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

          {/* Map — click to add checkpoints */}
          <div className="flex flex-col gap-1.5">
            <Label className="flex items-center gap-1.5">
              <MapPin size={14} />
              {t('checkpoints')}
            </Label>
            <p className="text-xs text-muted-foreground -mt-1">{t('clickMapToAdd')}</p>
            <MapPicker
              key={open ? `map-${site?.id ?? 'new'}` : 'map-closed'}
              checkpoints={checkpoints}
              selectedId={selectedCpId}
              onAdd={handleAddCheckpoint}
              onSelect={setSelectedCpId}
              onMove={handleMoveCheckpoint}
            />
          </div>

          {/* Checkpoint list */}
          <div className="flex flex-col gap-2">
            {checkpoints.length === 0 ? (
              <p className="text-xs text-muted-foreground py-1">{t('noCheckpoints')}</p>
            ) : (
              checkpoints.map((cp, idx) => (
                <div
                  key={cp.localId}
                  onClick={() => setSelectedCpId(cp.localId)}
                  className={`flex items-center gap-2 p-2 rounded-lg border cursor-pointer transition-colors ${
                    cp.localId === selectedCpId
                      ? 'border-primary bg-primary/5'
                      : 'border-border hover:bg-muted/40'
                  }`}
                >
                  {/* Index badge */}
                  <span className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold text-white ${
                    cp.localId === selectedCpId ? 'bg-primary' : 'bg-muted-foreground'
                  }`}>
                    {idx + 1}
                  </span>

                  {/* Name input */}
                  <Input
                    placeholder={t('checkpointName')}
                    value={cp.name}
                    onChange={(e) => {
                      e.stopPropagation()
                      handleUpdateCheckpoint(cp.localId, 'name', e.target.value)
                    }}
                    onClick={(e) => e.stopPropagation()}
                    className="h-7 text-sm flex-1"
                    disabled={loading}
                  />

                  {/* Radius input */}
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <Input
                      type="number"
                      min={50}
                      max={5000}
                      value={cp.radiusMeters}
                      onChange={(e) => {
                        e.stopPropagation()
                        handleUpdateCheckpoint(cp.localId, 'radiusMeters', e.target.value)
                      }}
                      onClick={(e) => e.stopPropagation()}
                      className="h-7 text-sm w-20"
                      disabled={loading}
                    />
                    <span className="text-xs text-muted-foreground">м</span>
                  </div>

                  {/* Coordinates display */}
                  <span className="text-xs text-muted-foreground flex-shrink-0 hidden sm:block">
                    {cp.lat.toFixed(5)}, {cp.lng.toFixed(5)}
                  </span>

                  {/* Delete */}
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    className="h-7 w-7 flex-shrink-0 text-muted-foreground hover:text-destructive"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleDeleteCheckpoint(cp.localId)
                    }}
                    disabled={loading}
                  >
                    <Trash2 size={13} />
                  </Button>
                </div>
              ))
            )}
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

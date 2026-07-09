'use client'

import { useEffect, useRef, useState, useCallback } from 'react'
import { useTranslations } from 'next-intl'
import { toast } from 'sonner'
import { Camera, CheckCircle, Loader2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import axios from 'axios'
import { useFaceApi } from '@/hooks/useFaceApi'
import { useSaveFace } from '@/hooks/useWorkers'
import type { UserResponse } from '@/types/user'

interface Props {
  open: boolean
  onClose: () => void
  worker: UserResponse
}

export function FaceRegisterModal({ open, onClose, worker }: Props) {
  const t = useTranslations('verify')
  const tc = useTranslations('common')
  const tw = useTranslations('workers')

  const videoRef = useRef<HTMLVideoElement>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const detectingRef = useRef(false)

  const [capturing, setCapturing] = useState(false)
  const [detected, setDetected] = useState(false)
  const [saving, setSaving] = useState(false)

  const { state: faceApiState, detectDescriptor } = useFaceApi()
  const saveFace = useSaveFace(worker.id)

  const startCamera = useCallback(async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: 640, height: 480 },
      })
      streamRef.current = stream
      if (videoRef.current) {
        videoRef.current.srcObject = stream
      }
      setCapturing(true)
    } catch {
      toast.error(t('cameraError'))
    }
  }, [])

  const stopCamera = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    streamRef.current?.getTracks().forEach((t) => t.stop())
    streamRef.current = null
    setCapturing(false)
    setDetected(false)
  }, [])

  // Start scanning for face when camera is active and models are ready
  useEffect(() => {
    if (!capturing || faceApiState !== 'ready') return
    intervalRef.current = setInterval(async () => {
      if (!videoRef.current || detectingRef.current) return
      detectingRef.current = true
      try {
        const descriptor = await detectDescriptor(videoRef.current)
        setDetected(!!descriptor)
      } catch {
        // inference error — next interval will retry
      } finally {
        detectingRef.current = false
      }
    }, 500)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [capturing, faceApiState, detectDescriptor])

  // Cleanup on close
  useEffect(() => {
    if (!open) stopCamera()
  }, [open, stopCamera])

  const handleCapture = async () => {
    if (!videoRef.current) return
    setSaving(true)
    try {
      const descriptor = await detectDescriptor(videoRef.current)
      if (!descriptor) {
        toast.error(t('faceNotDetected'))
        return
      }
      await saveFace.mutateAsync(Array.from(descriptor))
      toast.success(tw('registerFace') + ' — ' + tc('success'))
      stopCamera()
      onClose()
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.data?.code === 'FACE_ALREADY_REGISTERED') {
        const conflictName: string = err.response.data.message ?? ''
        toast.error(conflictName ? tw('faceConflict', { name: conflictName }) : tc('error'))
      } else {
        toast.error(tc('error'))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(v) => !v && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{tw('registerFace')} — {worker.name}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col items-center gap-4 py-2">
          {/* Camera viewport */}
          <div className="relative w-full aspect-video bg-muted rounded-lg overflow-hidden">
            <video
              ref={videoRef}
              autoPlay
              playsInline
              muted
              className="w-full h-full object-cover scale-x-[-1]"
            />
            {!capturing && (
              <div className="absolute inset-0 flex items-center justify-center">
                <Camera size={48} className="text-muted-foreground opacity-40" />
              </div>
            )}
            {/* Detection indicator */}
            {capturing && (
              <div className={`absolute top-3 right-3 flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${
                detected
                  ? 'bg-green-500/90 text-white'
                  : 'bg-black/50 text-white'
              }`}>
                {detected ? (
                  <><CheckCircle size={12} /> {t('faceDetected')}</>
                ) : (
                  <><Loader2 size={12} className="animate-spin" /> {t('scanning')}</>
                )}
              </div>
            )}
          </div>

          {/* Status text */}
          {faceApiState === 'loading' && (
            <p className="text-sm text-muted-foreground flex items-center gap-2">
              <Loader2 size={14} className="animate-spin" />
              {t('modelsLoading')}
            </p>
          )}
          {faceApiState === 'error' && (
            <p className="text-sm text-destructive">
              {t('modelsError')}
            </p>
          )}

          {/* Actions */}
          <div className="flex gap-3 w-full">
            {!capturing ? (
              <Button onClick={startCamera} className="flex-1" disabled={faceApiState === 'error'}>
                <Camera size={16} />
                {t('startCamera')}
              </Button>
            ) : (
              <>
                <Button
                  variant="outline"
                  onClick={stopCamera}
                  className="flex-1"
                  disabled={saving}
                >
                  {tc('cancel')}
                </Button>
                <Button
                  onClick={handleCapture}
                  className="flex-1"
                  disabled={!detected || saving || faceApiState !== 'ready'}
                >
                  {saving ? <Loader2 size={16} className="animate-spin" /> : <CheckCircle size={16} />}
                  {tc('confirm')}
                </Button>
              </>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

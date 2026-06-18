import { useCallback, useEffect, useState } from 'react'
import api from '@/lib/axios'

// Convert VAPID Base64URL public key to Uint8Array required by pushManager.subscribe()
function urlBase64ToUint8Array(base64url: string): Uint8Array {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - base64.length % 4) % 4)
  const binary = atob(padded)
  return Uint8Array.from(binary, (c) => c.charCodeAt(0))
}

function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    'Notification' in window
  )
}

export function usePushNotifications() {
  const [permission, setPermission] = useState<NotificationPermission>('default')
  const [subscribed, setSubscribed] = useState(false)
  const [loading, setLoading] = useState(false)
  const supported = isPushSupported()

  // Read current permission + check existing subscription on mount
  useEffect(() => {
    if (!supported) return
    setPermission(Notification.permission)

    navigator.serviceWorker.ready.then(async (reg) => {
      const sub = await reg.pushManager.getSubscription()
      setSubscribed(!!sub)
    }).catch(() => {})
  }, [supported])

  const subscribe = useCallback(async () => {
    if (!supported || loading) return
    setLoading(true)
    try {
      // 1. Request browser permission
      const perm = await Notification.requestPermission()
      setPermission(perm)
      if (perm !== 'granted') return

      // 2. Fetch VAPID public key from backend
      const { data } = await api.get<{ publicKey: string }>('/api/push/vapid-public-key')
      if (!data.publicKey) return

      // 3. Subscribe via browser PushManager
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(data.publicKey).buffer as ArrayBuffer,
      })

      // 4. Send subscription to backend
      const json = sub.toJSON()
      await api.post('/api/push/subscribe', {
        endpoint: sub.endpoint,
        keys: {
          p256dh: json.keys?.p256dh ?? '',
          auth: json.keys?.auth ?? '',
        },
      })

      setSubscribed(true)
    } catch (err) {
      console.error('Push subscribe failed:', err)
    } finally {
      setLoading(false)
    }
  }, [supported, loading])

  const unsubscribe = useCallback(async () => {
    if (!supported || loading) return
    setLoading(true)
    try {
      const reg = await navigator.serviceWorker.ready
      const sub = await reg.pushManager.getSubscription()
      if (!sub) return

      const json = sub.toJSON()
      await api.delete('/api/push/subscribe', {
        data: {
          endpoint: sub.endpoint,
          keys: {
            p256dh: json.keys?.p256dh ?? '',
            auth: json.keys?.auth ?? '',
          },
        },
      })

      await sub.unsubscribe()
      setSubscribed(false)
    } catch (err) {
      console.error('Push unsubscribe failed:', err)
    } finally {
      setLoading(false)
    }
  }, [supported, loading])

  return { supported, permission, subscribed, loading, subscribe, unsubscribe }
}

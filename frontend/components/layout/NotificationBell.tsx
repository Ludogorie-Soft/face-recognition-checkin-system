'use client'

import { Bell, BellOff, Loader2 } from 'lucide-react'
import { usePushNotifications } from '@/hooks/usePushNotifications'

export function NotificationBell() {
  const { supported, permission, subscribed, loading, subscribe, unsubscribe } = usePushNotifications()

  if (!supported || permission === 'denied') return null

  const handleClick = () => {
    if (subscribed) {
      unsubscribe()
    } else {
      subscribe()
    }
  }

  return (
    <button
      onClick={handleClick}
      disabled={loading}
      title={subscribed ? 'Disable notifications' : 'Enable notifications'}
      className={`flex items-center justify-center w-8 h-8 rounded-lg transition-colors ${
        subscribed
          ? 'text-primary hover:bg-primary/10'
          : 'text-muted-foreground hover:text-foreground hover:bg-muted'
      }`}
    >
      {loading ? (
        <Loader2 size={16} className="animate-spin" />
      ) : subscribed ? (
        <Bell size={16} />
      ) : (
        <BellOff size={16} />
      )}
    </button>
  )
}

/// <reference lib="webworker" />
export {}
declare const self: ServiceWorkerGlobalScope

// Handle incoming push notifications
self.addEventListener('push', (event) => {
  let title = 'AttendTrack'
  let body = ''

  try {
    const data = event.data?.json()
    title = data?.title ?? title
    body = data?.body ?? body
  } catch {
    body = event.data?.text() ?? ''
  }

  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      tag: 'attendtrack-notification',
    })
  )
})

// Open app on notification click
self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  event.waitUntil(
    self.clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((clients) => {
        const existing = clients.find((c) => c.url.includes(self.location.origin))
        if (existing) return existing.focus()
        return self.clients.openWindow('/')
      })
  )
})

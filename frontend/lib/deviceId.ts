// Stable per-device identifier for attendance audit metadata. Generated once and
// persisted in localStorage so the same physical terminal reports the same id across
// sessions. Used together with the server-captured IP to attribute each check-in/out.

const DEVICE_ID_KEY = 'attend-device-id'

export function getDeviceId(): string {
  if (typeof window === 'undefined') return ''
  let id = localStorage.getItem(DEVICE_ID_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(DEVICE_ID_KEY, id)
  }
  return id
}

// Frontend build version recorded with each event. Override via NEXT_PUBLIC_APP_VERSION.
export const APP_VERSION = process.env.NEXT_PUBLIC_APP_VERSION ?? '1.0.0'

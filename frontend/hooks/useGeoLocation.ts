import { useEffect, useState } from 'react'

export interface GeoState {
  lat: number
  lng: number
  accuracy: number
  loading: boolean
  error: string | null
  permissionDenied: boolean
}

export function useGeoLocation() {
  const [geo, setGeo] = useState<GeoState>({
    lat: 0,
    lng: 0,
    accuracy: 0,
    loading: true,
    error: null,
    permissionDenied: false,
  })

  useEffect(() => {
    if (!navigator.geolocation) {
      setGeo((g) => ({ ...g, loading: false, error: 'Geolocation not supported' }))
      return
    }

    // Pre-check permission state so we can show the blocked UI immediately,
    // before watchPosition fires its own error (which may take up to `timeout` ms).
    if (navigator.permissions) {
      navigator.permissions
        .query({ name: 'geolocation' as PermissionName })
        .then((result) => {
          if (result.state === 'denied') {
            setGeo((g) => ({ ...g, loading: false, permissionDenied: true, error: 'Permission denied' }))
          }
          // React to the user changing permission in OS settings while the app is open
          result.onchange = () => {
            if (result.state === 'denied') {
              setGeo((g) => ({ ...g, permissionDenied: true }))
            } else if (result.state === 'granted') {
              setGeo((g) => ({ ...g, permissionDenied: false, error: null }))
            }
          }
        })
        .catch(() => {
          // navigator.permissions.query for geolocation is unsupported on some platforms
          // (e.g. older iOS Safari). The watchPosition error handler covers this case.
        })
    }

    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const { latitude, longitude, accuracy } = pos.coords
        setGeo({
          lat: latitude,
          lng: longitude,
          accuracy,
          loading: false,
          error: null,
          permissionDenied: false,
        })
      },
      (err) => {
        // GeolocationPositionError.PERMISSION_DENIED === 1
        const denied = err.code === 1
        setGeo((g) => ({
          ...g,
          loading: false,
          error: err.message,
          permissionDenied: denied,
        }))
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 10000 },
    )

    return () => navigator.geolocation.clearWatch(watchId)
  }, [])

  return geo
}

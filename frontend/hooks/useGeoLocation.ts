import { useEffect, useState } from 'react'
import { isWithinRadius } from '@/lib/geo'
import type { SiteInfo } from '@/lib/db'

export interface GeoState {
  lat: number
  lng: number
  accuracy: number
  locationValid: boolean
  loading: boolean
  error: string | null
}

export function useGeoLocation(site: SiteInfo | null) {
  const [geo, setGeo] = useState<GeoState>({
    lat: 0,
    lng: 0,
    accuracy: 0,
    locationValid: false,
    loading: true,
    error: null,
  })

  useEffect(() => {
    if (!site) return
    if (!navigator.geolocation) {
      setGeo((g) => ({ ...g, loading: false, error: 'Geolocation not supported' }))
      return
    }

    const watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const { latitude, longitude, accuracy } = pos.coords
        setGeo({
          lat: latitude,
          lng: longitude,
          accuracy,
          locationValid: isWithinRadius(latitude, longitude, site.lat, site.lng, site.radiusMeters),
          loading: false,
          error: null,
        })
      },
      (err) => {
        setGeo((g) => ({ ...g, loading: false, error: err.message }))
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 10000 }
    )

    return () => navigator.geolocation.clearWatch(watchId)
  }, [site?.id]) // re-subscribe only when site changes

  return geo
}

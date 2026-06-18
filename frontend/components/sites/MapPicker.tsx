'use client'

import { useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Circle, useMapEvents, useMap } from 'react-leaflet'
import { divIcon } from 'leaflet'
import 'leaflet/dist/leaflet.css'

interface Props {
  lat: number | null
  lng: number | null
  radius: number
  onChange: (lat: number, lng: number) => void
}

// Bulgaria center — fallback if geolocation is unavailable
const DEFAULT_CENTER: [number, number] = [42.7339, 25.4858]

// Blue dot marker — avoids the broken default Leaflet icon in webpack
const markerIcon = divIcon({
  html: `<div style="
    width:20px;height:20px;
    background:#2563EB;
    border:3px solid #fff;
    border-radius:50%;
    box-shadow:0 2px 6px rgba(0,0,0,0.35)
  "></div>`,
  iconSize: [20, 20],
  iconAnchor: [10, 10],
  className: '',
})

// Registers click + drag on the map to update coordinates
function Interactions({ onChange }: { onChange: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onChange(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}

// Re-centers map when coordinates change
function Recenter({ lat, lng }: { lat: number; lng: number }) {
  const map = useMap()
  useEffect(() => {
    map.setView([lat, lng], map.getZoom(), { animate: true })
  }, [lat, lng, map])
  return null
}

export function MapPicker({ lat, lng, radius, onChange }: Props) {
  const hasCoords = lat != null && lng != null && lat !== 0 && lng !== 0
  const [geoCenter, setGeoCenter] = useState<[number, number]>(DEFAULT_CENTER)

  useEffect(() => {
    if (hasCoords) return
    navigator.geolocation?.getCurrentPosition(
      (pos) => setGeoCenter([pos.coords.latitude, pos.coords.longitude]),
      () => {} // silently fall back to Bulgaria center
    )
  }, [hasCoords])

  const center: [number, number] = hasCoords ? [lat!, lng!] : geoCenter

  return (
    <MapContainer
      center={center}
      zoom={11}
      style={{ height: '280px', width: '100%', borderRadius: '8px' }}
      className="z-0"
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      />
      <Interactions onChange={onChange} />
      <Recenter lat={center[0]} lng={center[1]} />
      {hasCoords && (
        <>
          <Marker position={[lat!, lng!]} icon={markerIcon} />
          <Circle
            center={[lat!, lng!]}
            radius={radius || 200}
            pathOptions={{ color: '#2563EB', fillColor: '#2563EB', fillOpacity: 0.1, weight: 2 }}
          />
        </>
      )}
    </MapContainer>
  )
}

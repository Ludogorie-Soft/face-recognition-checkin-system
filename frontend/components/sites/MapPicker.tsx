'use client'

import { Fragment, useEffect, useState } from 'react'
import { MapContainer, TileLayer, Marker, Circle, useMapEvents, useMap } from 'react-leaflet'
import { divIcon, type Marker as LeafletMarker } from 'leaflet'
import 'leaflet/dist/leaflet.css'

export interface CheckpointDraft {
  localId: string       // client-side key (crypto.randomUUID or server id)
  id?: string | null    // server id when editing an existing checkpoint
  name: string
  lat: number
  lng: number
  radiusMeters: number
}

interface Props {
  checkpoints: CheckpointDraft[]
  selectedId: string | null
  onAdd: (lat: number, lng: number) => void
  onSelect: (localId: string) => void
  onMove: (localId: string, lat: number, lng: number) => void
}

// Bulgaria center — fallback when no checkpoints and geolocation unavailable
const DEFAULT_CENTER: [number, number] = [42.7339, 25.4858]

function makeIcon(selected: boolean, index: number) {
  const bg = selected ? '#2563EB' : '#64748b'
  return divIcon({
    html: `<div style="
      width:26px;height:26px;
      background:${bg};
      border:3px solid #fff;
      border-radius:50%;
      box-shadow:0 2px 6px rgba(0,0,0,0.4);
      display:flex;align-items:center;justify-content:center;
      color:#fff;font-size:11px;font-weight:700;
    ">${index + 1}</div>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
    className: '',
  })
}

function Interactions({ onAdd }: { onAdd: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onAdd(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}

// Sets the initial map view once on mount — does not re-run on prop changes
function InitialView({
  checkpoints,
  geoCenter,
}: {
  checkpoints: CheckpointDraft[]
  geoCenter: [number, number]
}) {
  const map = useMap()
  useEffect(() => {
    if (checkpoints.length === 1) {
      map.setView([checkpoints[0].lat, checkpoints[0].lng], 15, { animate: false })
    } else if (checkpoints.length > 1) {
      const latLngs: [number, number][] = checkpoints.map((cp) => [cp.lat, cp.lng])
      map.fitBounds(latLngs, { padding: [50, 50], animate: false })
    } else {
      map.setView(geoCenter, 11, { animate: false })
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  return null
}

export function MapPicker({ checkpoints, selectedId, onAdd, onSelect, onMove }: Props) {
  const [geoCenter, setGeoCenter] = useState<[number, number]>(DEFAULT_CENTER)

  useEffect(() => {
    if (checkpoints.length > 0) return
    navigator.geolocation?.getCurrentPosition(
      (pos) => setGeoCenter([pos.coords.latitude, pos.coords.longitude]),
      () => {}
    )
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const initialCenter: [number, number] =
    checkpoints.length > 0 ? [checkpoints[0].lat, checkpoints[0].lng] : geoCenter

  return (
    <MapContainer
      center={initialCenter}
      zoom={11}
      style={{ height: '280px', width: '100%', borderRadius: '8px' }}
      className="z-0"
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      />
      <Interactions onAdd={onAdd} />
      <InitialView checkpoints={checkpoints} geoCenter={geoCenter} />
      {checkpoints.map((cp, idx) => (
        <Fragment key={cp.localId}>
          <Circle
            center={[cp.lat, cp.lng]}
            radius={cp.radiusMeters || 200}
            pathOptions={{
              color: cp.localId === selectedId ? '#2563EB' : '#64748b',
              fillColor: cp.localId === selectedId ? '#2563EB' : '#64748b',
              fillOpacity: 0.1,
              weight: 2,
            }}
          />
          <Marker
            position={[cp.lat, cp.lng]}
            icon={makeIcon(cp.localId === selectedId, idx)}
            draggable
            eventHandlers={{
              click: () => onSelect(cp.localId),
              dragend: (e) => {
                const latlng = (e.target as LeafletMarker).getLatLng()
                onMove(cp.localId, latlng.lat, latlng.lng)
              },
            }}
          />
        </Fragment>
      ))}
    </MapContainer>
  )
}

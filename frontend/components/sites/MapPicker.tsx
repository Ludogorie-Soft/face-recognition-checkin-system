'use client'

import { Fragment, useEffect, useRef, useState } from 'react'
import {
  MapContainer, TileLayer, Marker, Circle, Polyline, useMapEvents, useMap,
} from 'react-leaflet'
import { divIcon, type Marker as LeafletMarker } from 'leaflet'
import { Search, Loader2 } from 'lucide-react'
import 'leaflet/dist/leaflet.css'

export type CheckpointType = 'POINT' | 'LINE'

export interface CheckpointDraft {
  localId: string       // client-side key (crypto.randomUUID or server id)
  id?: string | null    // server id when editing an existing checkpoint
  name: string
  lat: number
  lng: number
  radiusMeters: number
  checkpointType: CheckpointType
  lat2?: number | null
  lng2?: number | null
}

interface Props {
  checkpoints: CheckpointDraft[]
  selectedId: string | null
  drawMode: CheckpointType     // controlled by SiteDialog
  lineRadius: number           // corridor half-width for new LINE checkpoints
  onAdd: (lat: number, lng: number) => void
  onSelect: (localId: string) => void
  onMove: (localId: string, lat: number, lng: number) => void
  onMoveSecond: (localId: string, lat: number, lng: number) => void  // drag second LINE endpoint
  onLineSecondPoint: (lat: number, lng: number) => void  // second click in LINE mode
  pendingLineStart: { lat: number; lng: number } | null  // first LINE point awaiting second
}

// Bulgaria center — fallback when no checkpoints and geolocation unavailable
const DEFAULT_CENTER: [number, number] = [42.7339, 25.4858]

function makeIcon(selected: boolean, index: number, isLine = false) {
  const bg = selected ? '#2563EB' : isLine ? '#7c3aed' : '#64748b'
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

function makeEndIcon(selected: boolean) {
  const bg = selected ? '#2563EB' : '#7c3aed'
  return divIcon({
    html: `<div style="
      width:20px;height:20px;
      background:${bg};
      border:3px solid #fff;
      border-radius:3px;
      box-shadow:0 2px 6px rgba(0,0,0,0.4);
    "></div>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10],
    className: '',
  })
}

function makePendingIcon() {
  return divIcon({
    html: `<div style="
      width:20px;height:20px;
      background:#f59e0b;
      border:3px solid #fff;
      border-radius:50%;
      box-shadow:0 2px 6px rgba(0,0,0,0.4);
      animation: pulse 1s infinite;
    "></div>`,
    iconSize: [20, 20],
    iconAnchor: [10, 10],
    className: '',
  })
}

function Interactions({
  drawMode,
  pendingLineStart,
  onAdd,
  onLineSecondPoint,
}: {
  drawMode: CheckpointType
  pendingLineStart: { lat: number; lng: number } | null
  onAdd: (lat: number, lng: number) => void
  onLineSecondPoint: (lat: number, lng: number) => void
}) {
  const map = useMap()

  useMapEvents({
    click(e) {
      if (drawMode === 'LINE') {
        if (!pendingLineStart) {
          onAdd(e.latlng.lat, e.latlng.lng)
        } else {
          onLineSecondPoint(e.latlng.lat, e.latlng.lng)
        }
      } else {
        onAdd(e.latlng.lat, e.latlng.lng)
      }
    },
  })

  // Change cursor to crosshair in LINE draw mode
  useEffect(() => {
    const container = map.getContainer()
    container.style.cursor = drawMode === 'LINE' ? 'crosshair' : ''
    return () => { container.style.cursor = '' }
  }, [map, drawMode])

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
      const latLngs: [number, number][] = checkpoints.flatMap((cp) =>
        cp.checkpointType === 'LINE' && cp.lat2 != null && cp.lng2 != null
          ? [[cp.lat, cp.lng], [cp.lat2, cp.lng2]] as [number, number][]
          : [[cp.lat, cp.lng]] as [number, number][]
      )
      map.fitBounds(latLngs, { padding: [50, 50], animate: false })
    } else {
      map.setView(geoCenter, 11, { animate: false })
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  return null
}

// Flies the map to a target position when it changes
function FlyTo({ target }: { target: [number, number] | null }) {
  const map = useMap()
  const prevTarget = useRef<[number, number] | null>(null)
  useEffect(() => {
    if (!target) return
    if (
      prevTarget.current &&
      prevTarget.current[0] === target[0] &&
      prevTarget.current[1] === target[1]
    ) return
    prevTarget.current = target
    map.flyTo(target, 17, { animate: true, duration: 1 })
  }, [map, target])
  return null
}

interface NominatimResult {
  lat: string
  lon: string
  display_name: string
}

export function MapPicker({
  checkpoints, selectedId, drawMode, lineRadius,
  onAdd, onSelect, onMove, onMoveSecond, onLineSecondPoint, pendingLineStart,
}: Props) {
  const [geoCenter, setGeoCenter] = useState<[number, number]>(DEFAULT_CENTER)
  const [searchQuery, setSearchQuery] = useState('')
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [flyTarget, setFlyTarget] = useState<[number, number] | null>(null)

  useEffect(() => {
    if (checkpoints.length > 0) return
    navigator.geolocation?.getCurrentPosition(
      (pos) => setGeoCenter([pos.coords.latitude, pos.coords.longitude]),
      () => {}
    )
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const initialCenter: [number, number] =
    checkpoints.length > 0 ? [checkpoints[0].lat, checkpoints[0].lng] : geoCenter

  const handleSearch = async () => {
    const q = searchQuery.trim()
    if (!q) return
    setSearching(true)
    setSearchError('')
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=1`,
        { headers: { 'Accept-Language': 'bg,en' } }
      )
      const data: NominatimResult[] = await res.json()
      if (data.length === 0) {
        setSearchError('Адресът не е намерен')
        return
      }
      setFlyTarget([parseFloat(data[0].lat), parseFloat(data[0].lon)])
    } catch {
      setSearchError('Грешка при търсене')
    } finally {
      setSearching(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {/* Address search */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search
            size={14}
            className="absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none"
          />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => { setSearchQuery(e.target.value); setSearchError('') }}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            placeholder="Търси адрес..."
            className="w-full pl-8 pr-3 h-9 text-sm rounded-md border border-input bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-0"
          />
        </div>
        <button
          type="button"
          onClick={handleSearch}
          disabled={searching || !searchQuery.trim()}
          className="flex items-center gap-1.5 px-3 h-9 rounded-md border border-input bg-background text-sm font-medium text-foreground hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed transition-colors shrink-0"
        >
          {searching ? <Loader2 size={14} className="animate-spin" /> : <Search size={14} />}
          Търси
        </button>
      </div>
      {searchError && <p className="text-xs text-destructive -mt-1">{searchError}</p>}

      {/* Line mode instruction */}
      {drawMode === 'LINE' && (
        <p className="text-xs text-violet-600 dark:text-violet-400 -mt-1">
          {!pendingLineStart
            ? '1. Кликни на картата за начална точка на линията'
            : '2. Кликни за крайна точка — ще се начертае коридорът'}
        </p>
      )}

      {/* Map */}
      <MapContainer
        center={initialCenter}
        zoom={11}
        style={{ height: '380px', width: '100%', borderRadius: '8px' }}
        className="z-0"
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        />
        <Interactions
          drawMode={drawMode}
          pendingLineStart={pendingLineStart}
          onAdd={onAdd}
          onLineSecondPoint={onLineSecondPoint}
        />
        <InitialView checkpoints={checkpoints} geoCenter={geoCenter} />
        <FlyTo target={flyTarget} />

        {/* Pending first LINE point */}
        {pendingLineStart && (
          <Marker
            position={[pendingLineStart.lat, pendingLineStart.lng]}
            icon={makePendingIcon()}
          />
        )}

        {/* Existing checkpoints */}
        {checkpoints.map((cp, idx) => {
          const selected = cp.localId === selectedId
          const isLine = cp.checkpointType === 'LINE'
          const color = selected ? '#2563EB' : isLine ? '#7c3aed' : '#64748b'

          if (isLine && cp.lat2 != null && cp.lng2 != null) {
            return (
              <Fragment key={cp.localId}>
                {/* Line stroke */}
                <Polyline
                  positions={[[cp.lat, cp.lng], [cp.lat2, cp.lng2]]}
                  pathOptions={{ color, weight: selected ? 4 : 3, opacity: 0.9 }}
                  eventHandlers={{ click: () => onSelect(cp.localId) }}
                />
                {/* Corridor circles at endpoints to visualise the buffer */}
                <Circle
                  center={[cp.lat, cp.lng]}
                  radius={cp.radiusMeters}
                  pathOptions={{ color, fillColor: color, fillOpacity: 0.08, weight: 1, dashArray: '4 4' }}
                />
                <Circle
                  center={[cp.lat2, cp.lng2]}
                  radius={cp.radiusMeters}
                  pathOptions={{ color, fillColor: color, fillOpacity: 0.08, weight: 1, dashArray: '4 4' }}
                />
                {/* Draggable start endpoint */}
                <Marker
                  position={[cp.lat, cp.lng]}
                  icon={makeIcon(selected, idx, true)}
                  draggable
                  eventHandlers={{
                    click: () => onSelect(cp.localId),
                    dragend: (e) => {
                      const ll = (e.target as LeafletMarker).getLatLng()
                      onMove(cp.localId, ll.lat, ll.lng)
                    },
                  }}
                />
                {/* Draggable end endpoint */}
                <Marker
                  position={[cp.lat2, cp.lng2]}
                  icon={makeEndIcon(selected)}
                  draggable
                  eventHandlers={{
                    click: () => onSelect(cp.localId),
                    dragend: (e) => {
                      const ll = (e.target as LeafletMarker).getLatLng()
                      onMoveSecond(cp.localId, ll.lat, ll.lng)
                    },
                  }}
                />
              </Fragment>
            )
          }

          // POINT checkpoint
          return (
            <Fragment key={cp.localId}>
              <Circle
                center={[cp.lat, cp.lng]}
                radius={cp.radiusMeters || 50}
                pathOptions={{
                  color,
                  fillColor: color,
                  fillOpacity: 0.1,
                  weight: 2,
                }}
              />
              <Marker
                position={[cp.lat, cp.lng]}
                icon={makeIcon(selected, idx)}
                draggable
                eventHandlers={{
                  click: () => onSelect(cp.localId),
                  dragend: (e) => {
                    const ll = (e.target as LeafletMarker).getLatLng()
                    onMove(cp.localId, ll.lat, ll.lng)
                  },
                }}
              />
            </Fragment>
          )
        })}
      </MapContainer>
    </div>
  )
}

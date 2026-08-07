const R = 6371000 // Earth radius in metres

export function haversineDistance(
  lat1: number, lng1: number,
  lat2: number, lng2: number
): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export function isWithinRadius(
  userLat: number, userLng: number,
  siteLat: number, siteLng: number,
  radiusMeters: number
): boolean {
  return haversineDistance(userLat, userLng, siteLat, siteLng) <= radiusMeters
}

/**
 * Minimum distance (metres) from point P to line segment AB.
 * Uses a planar (Cartesian) approximation valid for short distances (< ~1 km).
 */
export function distanceToSegmentMeters(
  pLat: number, pLng: number,
  aLat: number, aLng: number,
  bLat: number, bLng: number
): number {
  const cosLat = Math.cos(((aLat + bLat) / 2) * (Math.PI / 180))
  const toRad = (d: number) => d * (Math.PI / 180)

  // Local Cartesian coordinates relative to A (metres)
  const bx = toRad(bLng - aLng) * R * cosLat
  const by = toRad(bLat - aLat) * R
  const px = toRad(pLng - aLng) * R * cosLat
  const py = toRad(pLat - aLat) * R

  const len2 = bx * bx + by * by
  if (len2 < 1e-10) return Math.sqrt(px * px + py * py)  // degenerate segment (endpoints identical)

  const t = Math.max(0, Math.min(1, (px * bx + py * by) / len2))
  const dx = px - t * bx
  const dy = py - t * by
  return Math.sqrt(dx * dx + dy * dy)
}

export function isWithinAnyCheckpoint(
  userLat: number, userLng: number,
  checkpoints: Array<{
    lat: number; lng: number; radiusMeters: number
    lat2?: number | null; lng2?: number | null
    checkpointType?: 'POINT' | 'LINE'
  }>
): boolean {
  return checkpoints.some((cp) => {
    if (cp.checkpointType === 'LINE' && cp.lat2 != null && cp.lng2 != null) {
      return distanceToSegmentMeters(
        userLat, userLng,
        cp.lat, cp.lng,
        cp.lat2, cp.lng2
      ) <= cp.radiusMeters
    }
    return haversineDistance(userLat, userLng, cp.lat, cp.lng) <= cp.radiusMeters
  })
}

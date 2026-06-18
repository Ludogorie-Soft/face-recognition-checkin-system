import type { UserResponse } from './user'

export interface SiteResponse {
  id: string
  name: string
  address: string | null
  lat: number
  lng: number
  radiusMeters: number
  workStartTime: string | null  // "HH:mm:ss"
  workEndTime: string | null
  active: boolean
  createdAt: string
  managers: UserResponse[]
  workers: UserResponse[]
}

export interface SiteRequest {
  name: string
  address?: string
  lat: number
  lng: number
  radiusMeters?: number
  workStartTime?: string | null  // "HH:mm:ss"
  workEndTime?: string | null
}

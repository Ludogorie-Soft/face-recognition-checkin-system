import type { UserResponse } from './user'

export interface CheckpointResponse {
  id: string
  name: string | null
  lat: number
  lng: number
  radiusMeters: number
}

export interface CheckpointRequest {
  id?: string | null
  name?: string | null
  lat: number
  lng: number
  radiusMeters: number
}

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
  checkpoints: CheckpointResponse[]
}

export interface SiteRequest {
  name: string
  address?: string
  lat: number
  lng: number
  radiusMeters?: number
  workStartTime?: string | null  // "HH:mm:ss"
  workEndTime?: string | null
  checkpoints?: CheckpointRequest[]
  companyId?: string  // required on create
}

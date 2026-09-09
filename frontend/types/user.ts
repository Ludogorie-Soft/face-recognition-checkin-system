export type Role = 'ADMIN' | 'WORKER'
export type ShiftType = 'DAY' | 'SHIFT_24H'

export interface CompanyRef {
  id: string
  name: string
}

export interface UserResponse {
  id: string
  name: string
  email: string
  phone: string | null
  companies: CompanyRef[]
  role: Role
  shiftType: ShiftType
  active: boolean
  faceRegistered: boolean
  createdAt: string
}

export interface UserRequest {
  name: string
  email?: string
  phone?: string
  companyIds?: string[]
  password?: string
  role: Role
  shiftType?: ShiftType
}

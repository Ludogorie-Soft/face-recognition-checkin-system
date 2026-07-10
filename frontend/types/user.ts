export type Role = 'ADMIN' | 'WORKER'

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
}

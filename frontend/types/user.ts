export type Role = 'ADMIN' | 'WORKER'

export interface UserResponse {
  id: string
  name: string
  email: string
  phone: string | null
  company: string | null
  role: Role
  active: boolean
  faceRegistered: boolean
  createdAt: string
}

export interface UserRequest {
  name: string
  email?: string
  phone?: string
  company?: string
  password?: string
  role: Role
}

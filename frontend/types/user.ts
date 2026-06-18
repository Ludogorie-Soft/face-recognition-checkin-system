export type Role = 'ADMIN' | 'MANAGER' | 'WORKER'

export interface UserResponse {
  id: string
  name: string
  email: string
  phone: string | null
  role: Role
  active: boolean
  faceRegistered: boolean
  createdAt: string
}

export interface UserRequest {
  name: string
  email: string
  phone?: string
  password?: string
  role: Role
}

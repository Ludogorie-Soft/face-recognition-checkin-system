export interface CompanyResponse {
  id: string
  name: string
  address: string | null
  phone: string | null
  email: string | null
  registrationNumber: string | null
  mol: string | null
  active: boolean
  createdAt: string
  sites: { id: string; name: string }[]
  workers: { id: string; name: string }[]
}

export interface CompanyRequest {
  name: string
  address?: string
  phone?: string
  email?: string
  registrationNumber?: string
  mol?: string
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/axios'
import type { CompanyRequest, CompanyResponse } from '@/types/company'

const KEY = ['companies']

export function useCompanies() {
  return useQuery<CompanyResponse[]>({
    queryKey: KEY,
    queryFn: () => api.get('/api/companies').then((r) => r.data),
  })
}

export function useCreateCompany() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CompanyRequest) =>
      api.post<CompanyResponse>('/api/companies', data).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateCompany(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CompanyRequest) =>
      api.put<CompanyResponse>(`/api/companies/${id}`, data).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useDeactivateCompany() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/companies/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useAssignSite(companyId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (siteId: string) =>
      api.post(`/api/companies/${companyId}/sites/${siteId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useRemoveSite(companyId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (siteId: string) =>
      api.delete(`/api/companies/${companyId}/sites/${siteId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useAssignWorker(companyId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (workerId: string) =>
      api.post(`/api/companies/${companyId}/workers/${workerId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY })
      qc.invalidateQueries({ queryKey: ['workers'] })
    },
  })
}

export function useRemoveWorker(companyId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (workerId: string) =>
      api.delete(`/api/companies/${companyId}/workers/${workerId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY })
      qc.invalidateQueries({ queryKey: ['workers'] })
    },
  })
}

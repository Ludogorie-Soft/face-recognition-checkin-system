import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/axios'
import type { SiteResponse, SiteRequest } from '@/types/site'

const QK = 'sites'

interface MoveSiteCompanyArgs {
  siteId: string
  fromCompanyId: string | null  // null = site had no company
  toCompanyId: string
}

export function useSites() {
  return useQuery<SiteResponse[]>({
    queryKey: [QK],
    queryFn: () => api.get('/api/sites').then((r) => r.data),
  })
}

export function useSite(id: string) {
  return useQuery<SiteResponse>({
    queryKey: [QK, id],
    queryFn: () => api.get(`/api/sites/${id}`).then((r) => r.data),
    enabled: !!id,
  })
}

export function useCreateSite() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SiteRequest) => api.post<SiteResponse>('/api/sites', body).then((r) => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [QK] })
      qc.invalidateQueries({ queryKey: ['companies'] })
    },
  })
}

export function useUpdateSite(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SiteRequest) => api.put<SiteResponse>(`/api/sites/${id}`, body).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK] }),
  })
}

export function useMoveSiteCompany() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ siteId, fromCompanyId, toCompanyId }: MoveSiteCompanyArgs) => {
      if (fromCompanyId) {
        await api.delete(`/api/companies/${fromCompanyId}/sites/${siteId}`)
      }
      await api.post(`/api/companies/${toCompanyId}/sites/${siteId}`)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['companies'] })
      qc.invalidateQueries({ queryKey: [QK] })
    },
  })
}

export function useDeactivateSite() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/sites/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK] }),
  })
}

export function useAssignWorker(siteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.post(`/api/sites/${siteId}/workers/${userId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [QK] })
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] })
    },
  })
}

export function useRemoveWorker(siteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.delete(`/api/sites/${siteId}/workers/${userId}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [QK] })
      qc.invalidateQueries({ queryKey: ['dashboard', 'stats'] })
    },
  })
}

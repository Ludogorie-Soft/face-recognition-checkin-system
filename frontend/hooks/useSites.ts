import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/axios'
import type { SiteResponse, SiteRequest } from '@/types/site'

const QK = 'sites'

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
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK] }),
  })
}

export function useUpdateSite(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SiteRequest) => api.put<SiteResponse>(`/api/sites/${id}`, body).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK] }),
  })
}

export function useDeactivateSite() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/sites/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK] }),
  })
}

export function useAssignManager(siteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.post(`/api/sites/${siteId}/managers/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK, siteId] }),
  })
}

export function useRemoveManager(siteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.delete(`/api/sites/${siteId}/managers/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK, siteId] }),
  })
}

export function useAssignWorker(siteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.post(`/api/sites/${siteId}/workers/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK, siteId] }),
  })
}

export function useRemoveWorker(siteId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api.delete(`/api/sites/${siteId}/workers/${userId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QK, siteId] }),
  })
}

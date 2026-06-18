import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/axios'
import type { UserResponse, UserRequest, Role } from '@/types/user'

const QUERY_KEY = 'workers'

export function useWorkers(role?: Role) {
  return useQuery<UserResponse[]>({
    queryKey: [QUERY_KEY, role],
    queryFn: async () => {
      const params = role ? { role } : {}
      const { data } = await api.get('/api/users', { params })
      return data
    },
  })
}

export function useCreateWorker() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UserRequest) => api.post<UserResponse>('/api/users', body).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QUERY_KEY] }),
  })
}

export function useUpdateWorker(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: UserRequest) =>
      api.put<UserResponse>(`/api/users/${id}`, body).then((r) => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QUERY_KEY] }),
  })
}

export function useDeactivateWorker() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.delete(`/api/users/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QUERY_KEY] }),
  })
}

export function useSaveFace(userId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (descriptor: number[]) =>
      api.post(`/api/users/${userId}/face`, { descriptor }),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QUERY_KEY] }),
  })
}

export function useDeleteFace(userId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.delete(`/api/users/${userId}/face`),
    onSuccess: () => qc.invalidateQueries({ queryKey: [QUERY_KEY] }),
  })
}

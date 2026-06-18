import { useQuery } from '@tanstack/react-query'
import api from '@/lib/axios'

interface DashboardStats {
  totalSites: number
  totalWorkers: number
  presentToday: number
  missingToday: number
}

export function useDashboardStats() {
  return useQuery<DashboardStats>({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => api.get('/api/dashboard/stats').then((r) => r.data),
    refetchInterval: 60_000, // refresh every minute
  })
}

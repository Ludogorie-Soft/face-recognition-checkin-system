import { useQuery } from '@tanstack/react-query'
import api from '@/lib/axios'

interface DashboardStats {
  totalSites: number
  totalWorkers: number
  presentToday: number
  missingToday: number
}

export interface DayAttendance {
  date: string
  count: number
}

export interface SiteAttendance {
  siteId: string
  siteName: string
  presentCount: number
  totalWorkers: number
}

export interface ActivityEntry {
  workerName: string
  siteName: string
  type: 'CHECK_IN' | 'CHECK_OUT'
  recordedAt: string
}

export interface OutOfZoneEntry {
  workerName: string
  siteName: string
  recordedAt: string
}

export interface DashboardExtended {
  thisWeek: DayAttendance[]
  lastWeek: DayAttendance[]
  sites: SiteAttendance[]
  recentActivity: ActivityEntry[]
  autoCheckoutsLastNight: number
  outOfZoneToday: OutOfZoneEntry[]
}

export function useDashboardStats() {
  return useQuery<DashboardStats>({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => api.get('/api/dashboard/stats').then((r) => r.data),
    refetchInterval: 60_000,
  })
}

export function useDashboardExtended() {
  return useQuery<DashboardExtended>({
    queryKey: ['dashboard', 'extended'],
    queryFn: () => api.get('/api/dashboard/extended').then((r) => r.data),
    refetchInterval: 60_000,
  })
}

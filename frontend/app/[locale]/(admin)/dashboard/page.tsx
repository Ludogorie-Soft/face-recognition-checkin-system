'use client'

import { useTranslations } from 'next-intl'
import { Building2, Users, UserCheck, UserX, Loader2 } from 'lucide-react'
import { useDashboardStats } from '@/hooks/useDashboard'

interface StatCardProps {
  label: string
  value: number | undefined
  loading: boolean
  icon: React.ReactNode
  color: string
}

function StatCard({ label, value, loading, icon, color }: StatCardProps) {
  return (
    <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-6">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-muted-foreground">{label}</span>
        <span className={`rounded-lg p-2 ${color}`}>{icon}</span>
      </div>
      {loading ? (
        <Loader2 size={24} className="animate-spin text-muted-foreground" />
      ) : (
        <span className="text-3xl font-bold text-foreground">{value ?? 0}</span>
      )}
    </div>
  )
}

export default function DashboardPage() {
  const t = useTranslations('dashboard')
  const { data, isLoading } = useDashboardStats()

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-bold text-foreground">{t('title')}</h1>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          label={t('totalSites')}
          value={data?.totalSites}
          loading={isLoading}
          icon={<Building2 size={18} className="text-blue-600 dark:text-blue-400" />}
          color="bg-blue-100 dark:bg-blue-900/30"
        />
        <StatCard
          label={t('totalWorkers')}
          value={data?.totalWorkers}
          loading={isLoading}
          icon={<Users size={18} className="text-violet-600 dark:text-violet-400" />}
          color="bg-violet-100 dark:bg-violet-900/30"
        />
        <StatCard
          label={t('presentToday')}
          value={data?.presentToday}
          loading={isLoading}
          icon={<UserCheck size={18} className="text-green-600 dark:text-green-400" />}
          color="bg-green-100 dark:bg-green-900/30"
        />
        <StatCard
          label={t('missingToday')}
          value={data?.missingToday}
          loading={isLoading}
          icon={<UserX size={18} className="text-red-600 dark:text-red-400" />}
          color="bg-red-100 dark:bg-red-900/30"
        />
      </div>
    </div>
  )
}

'use client'

import { SyncBanner } from '@/components/offline/SyncBanner'
import { useAuth } from '@/hooks/useAuth'
import { ThemeToggle } from '@/components/layout/ThemeToggle'
import { LanguageToggle } from '@/components/layout/LanguageToggle'
import { LogOut, ClipboardCheck } from 'lucide-react'
import { NotificationBell } from '@/components/layout/NotificationBell'

export default function ManagerLayout({ children }: { children: React.ReactNode }) {
  const { ready, logout } = useAuth('MANAGER')

  if (!ready) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="w-8 h-8 rounded-full border-2 border-primary border-t-transparent animate-spin" />
      </div>
    )
  }

  return (
    <div className="h-dvh flex flex-col bg-background overflow-hidden">
      <header className="flex items-center justify-between px-4 py-3 bg-card border-b border-border">
        <div className="flex items-center gap-2">
          <ClipboardCheck size={20} className="text-primary" />
          <span className="text-sm font-bold tracking-tight text-foreground">AttendTrack</span>
        </div>
        <div className="flex items-center gap-2">
          <NotificationBell />
          <LanguageToggle />
          <ThemeToggle />
          <button
            onClick={logout}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
            aria-label="Logout"
          >
            <LogOut size={16} />
          </button>
        </div>
      </header>
      <SyncBanner />
      <main className="flex-1 flex flex-col min-h-0 overflow-auto">{children}</main>
    </div>
  )
}

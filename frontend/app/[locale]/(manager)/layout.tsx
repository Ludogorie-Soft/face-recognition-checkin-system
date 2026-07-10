'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useParams, useRouter } from 'next/navigation'
import { SyncBanner } from '@/components/offline/SyncBanner'
import { ThemeToggle } from '@/components/layout/ThemeToggle'
import { LanguageToggle } from '@/components/layout/LanguageToggle'
import { LogOut, ClipboardCheck, LayoutDashboard, LogIn } from 'lucide-react'
import { NotificationBell } from '@/components/layout/NotificationBell'
import { prefetchModels } from '@/lib/prefetchModels'
import { getToken, isTokenValid, getUserRole, removeToken } from '@/lib/auth'

export default function VerifyLayout({ children }: { children: React.ReactNode }) {
  const params = useParams()
  const router = useRouter()
  const locale = (params?.locale as string) ?? 'bg'

  const [isAdmin, setIsAdmin] = useState(false)

  useEffect(() => {
    const token = getToken()
    if (isTokenValid(token) && getUserRole() === 'ADMIN') {
      setIsAdmin(true)
    }
    prefetchModels()
  }, [])

  const logout = () => {
    removeToken()
    setIsAdmin(false)
    router.push(`/${locale}/login`)
  }

  return (
    <div className="h-dvh flex flex-col bg-background overflow-hidden">
      <header className="flex items-center justify-between px-4 py-3 bg-card border-b border-border">
        <div className="flex items-center gap-3">
          {isAdmin && (
            <Link
              href={`/${locale}/dashboard`}
              className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
              aria-label="Back to dashboard"
            >
              <LayoutDashboard size={16} />
            </Link>
          )}
          <div className="flex items-center gap-2">
            <ClipboardCheck size={20} className="text-primary" />
            <span className="text-sm font-bold tracking-tight text-foreground">AttendTrack</span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {isAdmin && <NotificationBell />}
          <LanguageToggle />
          <ThemeToggle />
          {isAdmin ? (
            <button
              onClick={logout}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
              aria-label="Logout"
            >
              <LogOut size={16} />
            </button>
          ) : (
            <Link
              href={`/${locale}/login`}
              className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"
              aria-label="Login"
            >
              <LogIn size={16} />
            </Link>
          )}
        </div>
      </header>
      <SyncBanner />
      <main className="flex-1 flex flex-col min-h-0 overflow-auto">{children}</main>
    </div>
  )
}

'use client'

import Link from 'next/link'
import { useParams, usePathname, useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import {
  LayoutDashboard,
  MapPin,
  Users,
  BarChart2,
  LogOut,
  ClipboardCheck,
  X,
  ScanFace,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { ThemeToggle } from './ThemeToggle'
import { LanguageToggle } from './LanguageToggle'
import { NotificationBell } from './NotificationBell'
import { removeToken } from '@/lib/auth'

interface AdminSidebarProps {
  onClose?: () => void
}

export function AdminSidebar({ onClose }: AdminSidebarProps) {
  const t = useTranslations('nav')
  const params = useParams()
  const pathname = usePathname()
  const locale = params.locale as string
  const router = useRouter()

  const logout = () => {
    removeToken()
    router.push(`/${locale}/login`)
  }

  const links = [
    { href: `/${locale}/dashboard`, label: t('dashboard'), icon: LayoutDashboard },
    { href: `/${locale}/sites`, label: t('sites'), icon: MapPin },
    { href: `/${locale}/workers`, label: t('workers'), icon: Users },
    { href: `/${locale}/reports`, label: t('reports'), icon: BarChart2 },
    { href: `/${locale}/verify`, label: t('verify'), icon: ScanFace },
  ]

  return (
    <aside className="flex flex-col w-60 min-h-screen bg-card border-r border-border">
      <div className="flex items-center justify-between gap-2 px-4 py-6 border-b border-border">
        <div className="flex items-center gap-2">
          <ClipboardCheck size={22} className="text-primary shrink-0" />
          <span className="text-base font-bold tracking-tight text-foreground">AttendTrack</span>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-muted-foreground hover:text-foreground hover:bg-accent transition-colors lg:hidden"
            aria-label="Close menu"
          >
            <X size={18} />
          </button>
        )}
      </div>
      <nav className="flex-1 flex flex-col gap-1 p-3">
        {links.map(({ href, label, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            className={cn(
              'flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors',
              pathname.startsWith(href)
                ? 'bg-primary/10 text-primary'
                : 'text-muted-foreground hover:text-foreground hover:bg-accent'
            )}
          >
            <Icon size={18} />
            {label}
          </Link>
        ))}
      </nav>
      <div className="flex items-center justify-between px-3 py-4 border-t border-border">
        <div className="flex items-center gap-1">
          <NotificationBell />
          <LanguageToggle />
          <ThemeToggle />
        </div>
        <button
          onClick={logout}
          className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-sm font-medium text-muted-foreground hover:text-destructive hover:bg-destructive/10 transition-colors"
          aria-label="Logout"
        >
          <LogOut size={16} />
        </button>
      </div>
    </aside>
  )
}

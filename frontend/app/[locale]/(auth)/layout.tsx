'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { ClipboardCheck, ScanFace } from 'lucide-react'
import { LanguageToggle } from '@/components/layout/LanguageToggle'
import { ThemeToggle } from '@/components/layout/ThemeToggle'

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const params = useParams()
  const locale = (params?.locale as string) ?? 'bg'
  const t = useTranslations('verify')

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <header className="flex items-center justify-between px-4 py-3 bg-card border-b border-border">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <ClipboardCheck size={20} className="text-primary" />
            <span className="text-sm font-bold tracking-tight text-foreground">AttendTrack</span>
          </div>
          <div className="h-4 w-px bg-border" />
          <Link
            href={`/${locale}/verify`}
            className="flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-primary transition-colors"
          >
            <ScanFace size={15} />
            <span>{t('title')}</span>
          </Link>
        </div>
        <div className="flex items-center gap-2">
          <LanguageToggle />
          <ThemeToggle />
        </div>
      </header>
      <main className="flex-1 flex items-center justify-center px-4 py-12">
        {children}
      </main>
    </div>
  )
}

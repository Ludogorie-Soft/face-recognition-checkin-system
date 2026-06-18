'use client'

import { useEffect } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { Loader2 } from 'lucide-react'
import { getToken, isTokenValid, getUserRole, getDashboardPath } from '@/lib/auth'

export default function LocalePage() {
  const router = useRouter()
  const params = useParams()
  const locale = params.locale as string

  useEffect(() => {
    const token = getToken()
    if (isTokenValid(token)) {
      const role = getUserRole()
      if (role) {
        router.replace(getDashboardPath(locale, role))
        return
      }
    }
    router.replace(`/${locale}/login`)
  }, [locale, router])

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <Loader2 size={32} className="animate-spin text-primary" />
    </div>
  )
}

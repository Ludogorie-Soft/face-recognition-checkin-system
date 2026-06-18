'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { getToken, isTokenValid, getUserRole, removeToken } from '@/lib/auth'

export function useAuth(requiredRole?: 'ADMIN' | 'MANAGER') {
  const router = useRouter()
  const params = useParams()
  const locale = (params?.locale as string) ?? 'bg'

  const [ready, setReady] = useState(false)
  const [role, setRole] = useState<'ADMIN' | 'MANAGER' | 'WORKER' | null>(null)

  useEffect(() => {
    const token = getToken()
    if (!isTokenValid(token)) {
      removeToken()
      router.replace(`/${locale}/login`)
      return
    }
    const userRole = getUserRole()
    if (requiredRole && userRole !== requiredRole) {
      router.replace(`/${locale}/login`)
      return
    }
    setRole(userRole)
    setReady(true)
  }, [locale, requiredRole, router])

  const logout = () => {
    removeToken()
    router.push(`/${locale}/login`)
  }

  return { ready, role, logout }
}

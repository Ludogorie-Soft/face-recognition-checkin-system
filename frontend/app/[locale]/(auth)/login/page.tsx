'use client'

import { useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { ClipboardCheck, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import api from '@/lib/axios'
import { setToken, decodeToken, getDashboardPath } from '@/lib/auth'

export default function LoginPage() {
  const t = useTranslations('auth')
  const router = useRouter()
  const params = useParams()
  const locale = params.locale as string

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    try {
      const { data } = await api.post<{ token: string }>('/api/auth/login', {
        email,
        password,
      })
      setToken(data.token)
      const payload = decodeToken(data.token)
      if (payload) {
        router.push(getDashboardPath(locale, payload.role))
      }
    } catch {
      toast.error(t('loginError'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="w-full max-w-sm shadow-lg">
      <CardHeader className="flex flex-col items-center gap-3 pb-2">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-primary/10">
            <ClipboardCheck size={28} className="text-primary" />
          </div>
          <span className="text-2xl font-bold tracking-tight text-foreground">AttendTrack</span>
        </div>
        <p className="text-muted-foreground text-sm">{t('welcomeBack')}</p>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="email">{t('email')}</Label>
            <Input
              id="email"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="password">{t('password')}</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
          </div>
          <Button type="submit" className="w-full mt-2 gap-2" disabled={loading}>
            {loading && <Loader2 size={16} className="animate-spin" />}
            {t('loginButton')}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}

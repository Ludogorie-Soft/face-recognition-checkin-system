export const TOKEN_KEY = 'garant_token'

export function getToken(): string | null {
  if (typeof window === 'undefined') return null
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export interface JwtPayload {
  sub: string
  role: 'ADMIN' | 'WORKER'
  exp: number
}

export function decodeToken(token: string): JwtPayload | null {
  try {
    // JWT uses Base64URL (- and _ instead of + and /). atob() requires standard Base64.
    const base64url = token.split('.')[1]
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - base64.length % 4) % 4)
    return JSON.parse(atob(padded)) as JwtPayload
  } catch {
    return null
  }
}

export function isTokenValid(token: string | null): boolean {
  if (!token) return false
  const payload = decodeToken(token)
  if (!payload) return false
  return payload.exp * 1000 > Date.now()
}

export function getUserRole(): 'ADMIN' | 'WORKER' | null {
  const token = getToken()
  if (!token) return null
  const payload = decodeToken(token)
  return payload?.role ?? null
}

export function getDashboardPath(locale: string, role: string): string {
  if (role === 'ADMIN') return `/${locale}/dashboard`
  return `/${locale}/login`
}

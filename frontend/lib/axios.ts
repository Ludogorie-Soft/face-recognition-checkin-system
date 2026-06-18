import axios from 'axios'
import { TOKEN_KEY } from './auth'

const api = axios.create({
  baseURL: '/',
  headers: { 'Content-Type': 'application/json' },
})

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Redirect to login on 401 — but not for the login endpoint itself
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginRequest = error.config?.url?.includes('/api/auth/login')
    if (error.response?.status === 401 && !isLoginRequest) {
      localStorage.removeItem(TOKEN_KEY)
      // Preserve current locale prefix when redirecting
      const locale = window.location.pathname.split('/')[1] || 'bg'
      window.location.href = `/${locale}/login`
    }
    return Promise.reject(error)
  }
)

export default api

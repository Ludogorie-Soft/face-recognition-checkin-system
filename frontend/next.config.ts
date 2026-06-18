import type { NextConfig } from 'next'
import createNextIntlPlugin from 'next-intl/plugin'
import withPWA from '@ducanh2912/next-pwa'

const withNextIntl = createNextIntlPlugin('./i18n/request.ts')

const pwaConfig = withPWA({
  dest: 'public',
  cacheOnFrontEndNav: true,
  aggressiveFrontEndNavCaching: true,
  reloadOnOnline: true,
  disable: process.env.NODE_ENV === 'development',
  workboxOptions: {
    disableDevLogs: true,
    runtimeCaching: [
      {
        urlPattern: /\/models\/.*/,
        handler: 'CacheFirst',
        options: {
          cacheName: 'face-models',
          expiration: { maxAgeSeconds: 30 * 24 * 60 * 60 },
        },
      },
    ],
  },
})

const nextConfig: NextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  serverExternalPackages: ['canvas'],
  webpack: (config, { isServer }) => {
    if (!isServer) {
      config.resolve.fallback = {
        ...config.resolve.fallback,
        fs: false,
        encoding: false,
      }
    }
    return config
  },
}

export default withNextIntl(pwaConfig(nextConfig))

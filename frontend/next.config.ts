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
        // ONNX models + MediaPipe face_landmarker.task (2 files, ~17MB)
        urlPattern: /\/models\/.*/,
        handler: 'CacheFirst',
        options: {
          cacheName: 'face-models',
          expiration: { maxEntries: 10, maxAgeSeconds: 30 * 24 * 60 * 60 },
        },
      },
      {
        // MediaPipe WASM + JS glue (6 files, served from /mediapipe/)
        urlPattern: /\/mediapipe\/.*/,
        handler: 'CacheFirst',
        options: {
          cacheName: 'mediapipe-wasm',
          expiration: { maxEntries: 20, maxAgeSeconds: 30 * 24 * 60 * 60 },
        },
      },
      {
        // ONNX Runtime WASM + MJS glue files (4 files, served from /)
        urlPattern: /\/ort-wasm-simd-threaded.*\.(wasm|mjs)$/,
        handler: 'CacheFirst',
        options: {
          cacheName: 'ort-wasm',
          expiration: { maxEntries: 10, maxAgeSeconds: 30 * 24 * 60 * 60 },
        },
      },
    ],
  },
})

const nextConfig: NextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  serverExternalPackages: ['canvas', 'onnxruntime-node'],
  async headers() {
    return [
      {
        source: '/(.*)\\.wasm',
        headers: [{ key: 'Content-Type', value: 'application/wasm' }],
      },
      {
        source: '/ort-wasm-simd-threaded(.*)\\.mjs',
        headers: [{ key: 'Content-Type', value: 'text/javascript' }],
      },
    ]
  },
  webpack: (config, { isServer }) => {
    if (!isServer) {
      config.resolve.fallback = {
        ...config.resolve.fallback,
        fs: false,
        encoding: false,
      }
      // Prevent onnxruntime-node (Node.js native) from being bundled in browser
      config.externals = [
        ...(Array.isArray(config.externals) ? config.externals : []),
        { 'onnxruntime-node': 'commonjs onnxruntime-node' },
      ]
    }
    return config
  },
}

export default withNextIntl(pwaConfig(nextConfig))

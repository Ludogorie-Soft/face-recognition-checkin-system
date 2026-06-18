import { type NextRequest, NextResponse } from 'next/server'

const BACKEND = process.env.API_URL ?? 'http://localhost:8080'

async function proxy(request: NextRequest, { params }: { params: Promise<{ path: string[] }> }) {
  const { path } = await params
  const url = `${BACKEND}/api/${path.join('/')}${request.nextUrl.search}`

  console.log(`[proxy] ${request.method} ${url}`)

  const headers = new Headers(request.headers)
  headers.delete('host')
  headers.delete('origin')
  headers.delete('referer')

  const hasBody = !['GET', 'HEAD'].includes(request.method)
  const body = hasBody ? await request.arrayBuffer() : undefined

  try {
    const response = await fetch(url, {
      method: request.method,
      headers,
      body,
    })

    console.log(`[proxy] → ${response.status}`)

    return new NextResponse(response.body, {
      status: response.status,
      headers: response.headers,
    })
  } catch (err) {
    console.error(`[proxy] fetch failed:`, err)
    return new NextResponse(JSON.stringify({ message: 'Proxy error' }), { status: 502 })
  }
}

export const GET = proxy
export const POST = proxy
export const PUT = proxy
export const DELETE = proxy
export const PATCH = proxy
export const OPTIONS = proxy

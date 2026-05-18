import { NextResponse } from 'next/server'

const BACKEND_URL = process.env.BACKEND_URL || 'http://10.255.78.89:9008/api/v1'

// Increase Next.js API route timeout to 10 minutes for large file uploads
export const maxDuration = 600 // 600 seconds = 10 minutes

export async function GET(request, { params }) {
  const resolvedParams = await params
  return proxyRequest(request, resolvedParams.path, 'GET')
}

export async function POST(request, { params }) {
  const resolvedParams = await params
  return proxyRequest(request, resolvedParams.path, 'POST')
}

export async function PUT(request, { params }) {
  const resolvedParams = await params
  return proxyRequest(request, resolvedParams.path, 'PUT')
}

export async function DELETE(request, { params }) {
  const resolvedParams = await params
  return proxyRequest(request, resolvedParams.path, 'DELETE')
}

async function proxyRequest(request, pathSegments, method) {
  const path = Array.isArray(pathSegments) ? pathSegments.join('/') : pathSegments

  // Preserve query parameters from the original request
  const requestUrl = new URL(request.url)
  const queryString = requestUrl.search // includes the '?' if params exist
  const url = `${BACKEND_URL}/${path}${queryString}`

  const headers = {}
  request.headers.forEach((value, key) => {
    const lowerKey = key.toLowerCase()
    // Skip browser-specific headers
    if (lowerKey !== 'host' &&
        lowerKey !== 'connection' &&
        lowerKey !== 'content-length' &&
        !lowerKey.startsWith('sec-')) {
      headers[key] = value
    }
  })

  const options = {
    method,
    headers,
    credentials: 'include', // Forward cookies
  }

  if (method !== 'GET' && method !== 'HEAD') {
    try {
      // Check if this is a multipart/form-data request (file upload)
      const contentType = request.headers.get('content-type') || ''
      if (contentType.includes('multipart/form-data')) {
        // For file uploads, pass the body stream directly to preserve multipart boundaries
        options.body = request.body
        options.duplex = 'half' // Required for streaming request bodies
      } else {
        // For JSON and other text-based requests
        const body = await request.text()
        if (body) options.body = body
      }
    } catch (e) {
      // No body
    }
  }

  try {
    console.log('[Proxy] Request:', method, url)
    console.log('[Proxy] Content-Type:', headers['content-type'] || 'none')
    if (options.body) {
      console.log('[Proxy] Body type:', options.body.constructor.name)
    }

    const response = await fetch(url, options)
    const contentType = response.headers.get('content-type')

    console.log('[Proxy] Response status:', response.status)

    let data
    if (contentType?.includes('application/json')) {
      data = await response.json()
    } else {
      data = await response.text()
    }

    // Log error responses for debugging
    if (response.status >= 400) {
      console.error('[Proxy] Error response:', data)
    }

    // Forward Set-Cookie headers from backend
    const responseHeaders = {
      'Content-Type': contentType || 'application/json',
    }

    const setCookie = response.headers.get('set-cookie')
    if (setCookie) {
      responseHeaders['Set-Cookie'] = setCookie
    }

    return NextResponse.json(data, {
      status: response.status,
      headers: responseHeaders,
    })
  } catch (error) {
    console.error('[Proxy] Error:', error)
    return NextResponse.json(
      { error: 'Failed to connect to backend server', details: error.message },
      { status: 503 }
    )
  }
}

// Use Next.js API proxy to avoid CORS issues
export const BASE_URL = '/api/proxy'

/**
 * Parses API error response bodies (e.g. RFC 7807 Problem Details) into a single message for UI.
 */
export function parseApiErrorBody(text, status) {
  const fallback = status ? `Request failed (${status})` : 'Request failed'
  const raw = text != null ? String(text).trim() : ''
  if (!raw) return fallback
  try {
    const data = JSON.parse(raw)
    if (data && typeof data === 'object') {
      if (typeof data.detail === 'string' && data.detail.trim()) return data.detail.trim()
      if (typeof data.message === 'string' && data.message.trim()) return data.message.trim()
      if (typeof data.error === 'string' && data.error.trim()) return data.error.trim()
      if (typeof data.title === 'string' && data.title.trim()) return data.title.trim()
    }
  } catch {
    /* plain text body */
  }
  if (raw.length > 400) return `${raw.slice(0, 397)}…`
  return raw
}

export function buildHeaders(token, extra = {}) {
  return {
    accept: '*/*',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extra,
  }
}

export const AUTH_BASE_URL = process.env.BACKEND_URL ?? 'http://localhost:8080/api/v1'

/**
 * POST /auths/login
 * Returns { res, payload } so callers can handle errors and response shapes themselves.
 * Used by src/auth.js (NextAuth credentials provider).
 */
export async function loginRaw({ email, password }) {
  const res = await fetch(`${AUTH_BASE_URL}/auths/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      accept: 'application/json',
    },
    body: JSON.stringify({ email, password }),
  })

  let payload = null
  const ct = res.headers.get('content-type') ?? ''
  if (ct.includes('application/json')) {
    payload = await res.json().catch(() => null)
  } else {
    const text = await res.text().catch(() => '')
    if (text.trim()) {
      try { payload = JSON.parse(text) } catch { payload = { message: text } }
    }
  }

  return { res, payload }
}

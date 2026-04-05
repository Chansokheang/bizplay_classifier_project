export const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

export function buildHeaders(token, extra = {}) {
  return {
    accept: '*/*',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extra,
  }
}

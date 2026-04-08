import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * GET /api/v1/companies/allCompanies
 */
export async function getAllCompanies(token) {
  const res = await fetch(`${BASE_URL}/companies/allCompanies`, {
    headers: buildHeaders(token),
  })
  const text = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(text, res.status))
  try {
    return text ? JSON.parse(text) : {}
  } catch {
    return {}
  }
}

/**
 * POST /api/v1/companies/create
 * @param {{ companyName: string, businessNumber: string }} body
 */
export async function createCompany({ companyName, businessNumber }, token) {
  const res = await fetch(`${BASE_URL}/companies/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ companyName, businessNumber }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

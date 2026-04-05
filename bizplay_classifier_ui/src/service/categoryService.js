import { BASE_URL, buildHeaders } from './api'

/**
 * GET /api/v1/categories — list categories for a company
 */
export async function getCategoriesByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/categories?companyId=${companyId}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * POST /api/v1/categories/create
 */
export async function createCategory({ companyId, name, description, color }, token) {
  const res = await fetch(`${BASE_URL}/api/v1/categories/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ companyId, name, description, color }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * PUT /api/v1/categories/{categoryId}
 */
export async function updateCategory(categoryId, { name, description, color }, token) {
  const res = await fetch(`${BASE_URL}/api/v1/categories/${categoryId}`, {
    method: 'PUT',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ name, description, color }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * DELETE /api/v1/categories/{categoryId}
 */
export async function deleteCategory(categoryId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/categories/${categoryId}`, {
    method: 'DELETE',
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
}

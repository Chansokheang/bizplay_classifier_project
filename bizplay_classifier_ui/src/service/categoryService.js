import { BASE_URL, buildHeaders } from './api'

/**
 * GET /api/v1/categories/{companyId} — list categories for a company
 */
export async function getCategoriesByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/categories/${companyId}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * POST /api/v1/categories/create
 * @param {{ companyId: string, code: string, category: string }} param0
 */
export async function createCategory({ companyId, code, category }, token) {
  const res = await fetch(`${BASE_URL}/api/v1/categories/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ companyId, code, category }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * POST /api/v1/categories/upload — bulk import categories from Excel
 * @param {File} file
 * @param {string} companyId
 * @param {string|null} sheetName
 * @param {string} token
 */
export async function uploadCategories(file, companyId, token, sheetName = null) {
  const form = new FormData()
  form.append('file', file)
  const url = new URL(`${BASE_URL}/api/v1/categories/upload`)
  url.searchParams.set('companyId', companyId)
  if (sheetName) url.searchParams.set('sheetName', sheetName)
  const res = await fetch(url.toString(), {
    method: 'POST',
    headers: buildHeaders(token),
    body: form,
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

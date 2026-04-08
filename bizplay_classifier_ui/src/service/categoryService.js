import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * GET /api/v1/categories/{companyId} — list categories for a company
 */
export async function getCategoriesByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/categories/${companyId}`, {
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
  const res = await fetch(`${BASE_URL}/categories/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ companyId, code, category }),
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
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

  // Build URL with query parameters
  const params = new URLSearchParams({ companyId })
  if (sheetName) params.set('sheetName', sheetName)
  const url = `${BASE_URL}/categories/upload?${params.toString()}`

  // For FormData, don't set Content-Type - let browser set it with boundary
  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: form,
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
  }
  return res.json()
}

/**
 * PUT /api/v1/categories/{categoryId}
 */
export async function updateCategory(categoryId, { code, category }, token) {
  const res = await fetch(`${BASE_URL}/categories/${categoryId}`, {
    method: 'PUT',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ code, category }),
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
  }
  return res.json()
}

/**
 * DELETE /api/v1/categories/{categoryId}
 */
export async function deleteCategory(categoryId, token) {
  const res = await fetch(`${BASE_URL}/categories/${categoryId}`, {
    method: 'DELETE',
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
}

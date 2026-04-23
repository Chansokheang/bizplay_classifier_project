import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * GET /api/v1/categories/{corpNo} — list categories for a corp.
 * A 404 means "no categories yet for this corp" — return an empty payload so the page renders an empty state.
 */
export async function getCategoriesByCorp(corpNo, token) {
  const res = await fetch(`${BASE_URL}/categories/${encodeURIComponent(corpNo)}`, {
    headers: buildHeaders(token),
  })
  if (res.status === 404) return { payload: [] }
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
  }
  return res.json()
}

/**
 * POST /api/v1/categories/create
 * @param {{ corpNo: string, code: string, category: string }} param0
 */
export async function createCategory({ corpNo, code, category }, token) {
  const res = await fetch(`${BASE_URL}/categories/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ corpNo, code, category }),
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
 * @param {string} corpNo
 * @param {string} token
 * @param {string|null} sheetName
 */
export async function uploadCategories(file, corpNo, token, sheetName = null) {
  const form = new FormData()
  form.append('file', file)

  const params = new URLSearchParams({ corpNo })
  if (sheetName) params.set('sheetName', sheetName)
  const url = `${BASE_URL}/categories/upload?${params.toString()}`

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

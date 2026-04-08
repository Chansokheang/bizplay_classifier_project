import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * POST /api/v1/transactions/upload — upload Excel file of transactions for classification
 * @param {File} file
 * @param {string} companyId
 */
export async function uploadTransactions(file, companyId, token, sheetName = null) {
  const formData = new FormData()
  formData.append('file', file)

  // Build URL with query parameters (backend expects them as query params, not in body)
  const params = new URLSearchParams({ companyId })
  if (sheetName) params.set('sheetName', sheetName)
  const url = `${BASE_URL}/transactions/upload?${params.toString()}`

  // For FormData, don't set Content-Type - let browser set it with boundary
  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(url, {
    method: 'POST',
    headers,
    body: formData,
  })
  if (!res.ok) {
    let msg = await res.text().catch(() => '')
    try {
      // Try to parse the JSON error format e.g. problem detail JSON
      const json = JSON.parse(msg)
      msg = json.detail || json.message || msg
    } catch (e) {
      // Ignore if not valid JSON
    }
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * GET /api/v1/transactions — list transaction files for a company
 */
export async function getTransactionsByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/transactions?companyId=${companyId}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * GET /api/v1/transactions/{fileId}/rows — get rows for a specific uploaded file
 */
export async function getTransactionRows(fileId, token) {
  const res = await fetch(`${BASE_URL}/transactions/${fileId}/rows`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * GET /api/v1/storage/files/company/{companyId}/filter
 * List all processed files for a company with their classify summaries
 */
export async function getOutputFiles(companyId, token) {
  const res = await fetch(
    `${BASE_URL}/storage/files/company/${encodeURIComponent(companyId)}/filter?fileType=OUTPUT`,
    { headers: buildHeaders(token) }
  )
  const text = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(text, res.status))
  try {
    return text ? JSON.parse(text) : {}
  } catch {
    return {}
  }
}

/**
 * GET /api/v1/transactions/files/{fileId}/transactions
 * Get paginated transactions for a specific file
 */
export async function getFileTransactions(fileId, page = 1, limit = 100, token) {
  const res = await fetch(`${BASE_URL}/transactions/files/${fileId}/transactions?page=${page}&limit=${limit}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

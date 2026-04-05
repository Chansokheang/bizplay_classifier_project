import { BASE_URL, buildHeaders } from './api'

/**
 * POST /api/v1/transactions/upload — upload Excel file of transactions for classification
 * @param {File} file
 * @param {string} companyId
 */
export async function uploadTransactions(file, companyId, token) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('companyId', companyId)
  const res = await fetch(`${BASE_URL}/api/v1/transactions/upload`, {
    method: 'POST',
    headers: buildHeaders(token),
    body: formData,
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * GET /api/v1/transactions — list transaction files for a company
 */
export async function getTransactionsByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/transactions?companyId=${companyId}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * GET /api/v1/transactions/{fileId}/rows — get rows for a specific uploaded file
 */
export async function getTransactionRows(fileId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/transactions/${fileId}/rows`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

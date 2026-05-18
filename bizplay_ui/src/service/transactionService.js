import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * POST /api/v1/transactions/upload — upload Excel file of transactions for classification
 * @param {File} file
 * @param {string} corpNo
 * @param {string} token
 * @param {string|null} sheetName
 */
export async function uploadTransactions(file, corpNo, token, sheetName = null) {
  const formData = new FormData()
  formData.append('file', file)

  const params = new URLSearchParams({ corpNo })
  if (sheetName) params.set('sheetName', sheetName)
  const url = `${BASE_URL}/transactions/upload?${params.toString()}`

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
 * POST /api/v1/transactions/test-single-transaction/create
 * Create a single transaction (for testing).
 */
export async function createSingleTransactionForTesting(transaction, token) {
  const res = await fetch(`${BASE_URL}/transactions/test-single-transaction/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(transaction),
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
  }
  return res.json()
}

/**
 * GET /api/v1/transactions/files/corp/{corpNo}/classify-summaries
 * List all processed files for a corp with their classify summaries.
 * A 404 means "no files yet for this corp" — return an empty payload.
 */
export async function getClassifySummariesByCorp(corpNo, token) {
  const res = await fetch(
    `${BASE_URL}/transactions/files/corp/${encodeURIComponent(corpNo)}/classify-summaries`,
    { headers: buildHeaders(token) },
  )
  if (res.status === 404) return { payload: [] }
  const text = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(text, res.status))
  try {
    return text ? JSON.parse(text) : { payload: [] }
  } catch {
    return { payload: [] }
  }
}

/**
 * GET /api/v1/storage/files/corp/{corpNo}/filter?fileType=OUTPUT
 * List output files for a corp.
 * A 404 means "no files yet for this corp" — return an empty payload.
 */
export async function getOutputFiles(corpNo, token) {
  const res = await fetch(
    `${BASE_URL}/storage/files/corp/${encodeURIComponent(corpNo)}/filter?fileType=OUTPUT`,
    { headers: buildHeaders(token) },
  )
  if (res.status === 404) return { payload: [] }
  const text = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(text, res.status))
  try {
    return text ? JSON.parse(text) : { payload: [] }
  } catch {
    return { payload: [] }
  }
}

/**
 * GET /api/v1/transactions/files/{fileId}/transactions
 * Paginated transactions for a specific file.
 */
export async function getFileTransactions(fileId, page = 1, limit = 100, token) {
  const res = await fetch(
    `${BASE_URL}/transactions/files/${encodeURIComponent(fileId)}/transactions?page=${page}&limit=${limit}`,
    { headers: buildHeaders(token) },
  )
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * PUT /api/v1/transactions/files/{fileId}/rows
 * Patch one or more rows (usageCode) for a file.
 * @param {string} fileId
 * @param {{ corpNo: string, updates: Array<{ rowIndex: number, usageCode: string }> }} payload
 */
export async function patchFileRows(fileId, { corpNo, updates }, token) {
  const res = await fetch(`${BASE_URL}/transactions/files/${encodeURIComponent(fileId)}/rows`, {
    method: 'PUT',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ corpNo, updates }),
  })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
  }
  return res.json()
}

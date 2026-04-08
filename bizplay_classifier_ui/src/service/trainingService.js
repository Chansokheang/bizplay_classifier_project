import { BASE_URL, buildHeaders } from './api'

/**
 * POST /api/v1/storage/training-files/upload — store a training file
 * @param {File} file
 * @param {string} companyId
 * @param {string} token
 */
export async function uploadTrainingFile(file, companyId, token) {
  const formData = new FormData()
  formData.append('file', file)

  // For FormData, don't set Content-Type - let browser set it with boundary
  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(
    `${BASE_URL}/storage/training-files/upload?companyId=${companyId}`,
    {
      method: 'POST',
      headers,
      body: formData,
    }
  )
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * POST /api/v1/data/train — train rules from an uploaded file
 * @param {File} file
 * @param {string} companyId
 * @param {string|null} sheetName
 * @param {string} token
 */
export async function trainFromFile(file, companyId, sheetName, token) {
  const formData = new FormData()
  formData.append('file', file)
  const params = new URLSearchParams({ companyId })
  if (sheetName) params.append('sheetName', sheetName)

  // For FormData, don't set Content-Type - let browser set it with boundary
  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(`${BASE_URL}/data/train?${params.toString()}`, {
    method: 'POST',
    headers,
    body: formData,
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * GET /api/v1/storage/files/company/{companyId} — list stored files for a company
 * @param {string} companyId
 * @param {string} token
 */
export async function getTrainingFiles(companyId, token) {
  const res = await fetch(`${BASE_URL}/storage/files/company/${companyId}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}


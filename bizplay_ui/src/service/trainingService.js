import { BASE_URL, buildHeaders } from './api'

/**
 * POST /api/v1/storage/training-files/upload — store a training file
 * @param {File} file
 * @param {string} corpNo
 * @param {string} token
 */
export async function uploadTrainingFile(file, corpNo, token) {
  const formData = new FormData()
  formData.append('file', file)

  const headers = {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }

  const res = await fetch(
    `${BASE_URL}/storage/training-files/upload?corpNo=${encodeURIComponent(corpNo)}`,
    {
      method: 'POST',
      headers,
      body: formData,
    },
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
 * @param {string} corpNo
 * @param {string|null} sheetName
 * @param {string} token
 * @param {number|null} sampleRows
 */
export async function trainFromFile(file, corpNo, sheetName, token, sampleRows = null) {
  const formData = new FormData()
  formData.append('file', file)
  const params = new URLSearchParams({ corpNo })
  if (sheetName) params.append('sheetName', sheetName)
  if (sampleRows != null) params.append('sampleRows', String(sampleRows))

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
 * GET /api/v1/storage/files/corp/{corpNo} — list stored files for a corp
 */
export async function getTrainingFiles(corpNo, token) {
  const res = await fetch(`${BASE_URL}/storage/files/corp/${encodeURIComponent(corpNo)}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * GET /api/v1/storage/files/by-id/{fileId} — download a file by id
 * Returns the raw Response so the caller can pull the binary blob.
 */
export async function downloadFileById(fileId, token) {
  const res = await fetch(`${BASE_URL}/storage/files/by-id/${encodeURIComponent(fileId)}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res
}

/**
 * GET /api/v1/storage/files/by-name/{storedFileName} — download a file by stored name
 */
export async function downloadFileByName(storedFileName, token) {
  const res = await fetch(
    `${BASE_URL}/storage/files/by-name/${encodeURIComponent(storedFileName)}`,
    { headers: buildHeaders(token) },
  )
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res
}

/**
 * DELETE /api/v1/storage/files/{fileId} — delete a stored file
 */
export async function deleteStoredFile(fileId, token) {
  const res = await fetch(`${BASE_URL}/storage/files/${encodeURIComponent(fileId)}`, {
    method: 'DELETE',
    headers: buildHeaders(token),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

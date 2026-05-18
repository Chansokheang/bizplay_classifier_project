import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * GET /api/v1/corps — list all corps
 */
export async function getAllCorps(token) {
  const res = await fetch(`${BASE_URL}/corps`, {
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
 * GET /api/v1/corps/{corpNo}
 */
export async function getCorpByCorpNo(corpNo, token) {
  const res = await fetch(`${BASE_URL}/corps/${encodeURIComponent(corpNo)}`, {
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
 * POST /api/v1/corps
 * @param {{ corpName: string, corpNo?: string, corpGroupId: number }} body
 */
export async function createCorp({ corpName, corpNo, corpGroupId }, token) {
  const res = await fetch(`${BASE_URL}/corps`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ corpName, corpNo, corpGroupId }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(msg, res.status))
  }
  return res.json()
}

/**
 * DELETE /api/v1/corps/{corpNo}
 */
export async function deleteCorp(corpNo, token) {
  const res = await fetch(`${BASE_URL}/corps/${encodeURIComponent(corpNo)}`, {
    method: 'DELETE',
    headers: buildHeaders(token),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(msg, res.status))
  }
  return res.json()
}

/**
 * GET /api/v1/corps/corp-groups — list all corp groups
 */
export async function getAllCorpGroups(token) {
  const res = await fetch(`${BASE_URL}/corps/corp-groups`, {
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
 * GET /api/v1/corps/corp-groups/{corpGroupId}
 */
export async function getCorpGroupById(corpGroupId, token) {
  const res = await fetch(`${BASE_URL}/corps/corp-groups/${encodeURIComponent(corpGroupId)}`, {
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
 * POST /api/v1/corps/corp-groups
 * @param {{ corpGroupCode: string }} body
 */
export async function createCorpGroup({ corpGroupCode }, token) {
  const res = await fetch(`${BASE_URL}/corps/corp-groups`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ corpGroupCode }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(msg, res.status))
  }
  return res.json()
}

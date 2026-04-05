import { BASE_URL, buildHeaders } from './api'

/**
 * GET /api/v1/rules — list rules for a company
 */
export async function getRulesByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/rules?companyId=${companyId}`, {
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
  return res.json()
}

/**
 * POST /api/v1/rules/create
 */
export async function createRule({ companyId, name, conditionType, pattern, categoryId, priority }, token) {
  const res = await fetch(`${BASE_URL}/api/v1/rules/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ companyId, name, conditionType, pattern, categoryId, priority }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * PUT /api/v1/rules/{ruleId}
 */
export async function updateRule(ruleId, { name, conditionType, pattern, categoryId, priority, status }, token) {
  const res = await fetch(`${BASE_URL}/api/v1/rules/${ruleId}`, {
    method: 'PUT',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({ name, conditionType, pattern, categoryId, priority, status }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * DELETE /api/v1/rules/{ruleId}
 */
export async function deleteRule(ruleId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/rules/${ruleId}`, {
    method: 'DELETE',
    headers: buildHeaders(token),
  })
  if (!res.ok) throw new Error(`Request failed (${res.status})`)
}

/**
 * POST /api/v1/rules/train — upload Excel file to train rule-based classifier
 * @param {File} file
 */
export async function trainRulesFromExcel(file, token) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${BASE_URL}/api/v1/rules/train`, {
    method: 'POST',
    headers: buildHeaders(token), // no Content-Type: browser sets multipart boundary automatically
    body: formData,
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

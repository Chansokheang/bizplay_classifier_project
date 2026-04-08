import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * GET /api/v1/rules/{companyId} — list rules for a company (payload: rule array)
 */
export async function getRulesByCompany(companyId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/rules/${companyId}`, {
    headers: buildHeaders(token),
  })
  const body = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(body, res.status))
  try {
    return JSON.parse(body || '{}')
  } catch {
    throw new Error('Invalid response from server')
  }
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
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
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
    const body = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(body, res.status))
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
  const body = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(body, res.status))
}

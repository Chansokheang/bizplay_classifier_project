import { BASE_URL, buildHeaders, parseApiErrorBody } from './api'

/**
 * GET /api/v1/rules/{corpNo} — list rules for a corp.
 * A 404 means "no rules yet for this corp" — return an empty payload.
 */
export async function getRulesByCorp(corpNo, token) {
  const res = await fetch(`${BASE_URL}/rules/${encodeURIComponent(corpNo)}`, {
    headers: buildHeaders(token),
  })
  if (res.status === 404) return { payload: [] }
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
 * @param {{
 *   corpNo: string,
 *   categoryCodes: string[],
 *   merchantIndustryName: string,   // 가맹점업종명
 *   merchantIndustryCode: string,   // 가맹점업종코드
 *   minAmount?: number,
 *   maxAmount?: number,
 *   description?: string,
 * }} payload
 */
export async function createRule(
  { corpNo, categoryCodes, merchantIndustryName, merchantIndustryCode, minAmount, maxAmount, description },
  token,
) {
  const body = {
    corpNo,
    categoryCodes,
    '가맹점업종명': merchantIndustryName,
    '가맹점업종코드': merchantIndustryCode,
    ...(minAmount != null ? { minAmount } : {}),
    ...(maxAmount != null ? { maxAmount } : {}),
    ...(description != null ? { description } : {}),
  }
  const res = await fetch(`${BASE_URL}/rules/create`, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(text, res.status))
  }
  return res.json()
}

/**
 * PUT /api/v1/rules/update/{ruleId}
 * @param {string} ruleId
 * @param {{
 *   categoryCodes: string[],
 *   merchantIndustryName: string,   // 가맹점업종명
 *   merchantIndustryCode: string,   // 가맹점업종코드
 *   usageStatus?: 'Y' | 'N',
 *   minAmount?: number,
 *   maxAmount?: number,
 *   description?: string,
 * }} payload
 */
export async function updateRule(
  ruleId,
  { categoryCodes, merchantIndustryName, merchantIndustryCode, usageStatus, minAmount, maxAmount, description },
  token,
) {
  const body = {
    categoryCodes,
    '가맹점업종명': merchantIndustryName,
    '가맹점업종코드': merchantIndustryCode,
    ...(usageStatus != null ? { usageStatus } : {}),
    ...(minAmount != null ? { minAmount } : {}),
    ...(maxAmount != null ? { maxAmount } : {}),
    ...(description != null ? { description } : {}),
  }
  const res = await fetch(`${BASE_URL}/rules/update/${encodeURIComponent(ruleId)}`, {
    method: 'PUT',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(parseApiErrorBody(text, res.status))
  }
  return res.json()
}

/**
 * DELETE /api/v1/rules/{ruleId}
 */
export async function deleteRule(ruleId, token) {
  const res = await fetch(`${BASE_URL}/rules/${encodeURIComponent(ruleId)}`, {
    method: 'DELETE',
    headers: buildHeaders(token),
  })
  const body = await res.text().catch(() => '')
  if (!res.ok) throw new Error(parseApiErrorBody(body, res.status))
}

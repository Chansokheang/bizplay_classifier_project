import { BASE_URL, buildHeaders } from './api'

/**
 * PUT /api/v1/bot-configs/{companyId} — update or create bot configuration for a company
 * @param {string} companyId
 * @param {{ modelName: string, temperature: number, systemPrompt: string, apiKey: string }} config
 * @param {string} token
 */
export async function saveBotConfig(companyId, config, token) {
  const res = await fetch(`${BASE_URL}/api/v1/bot-configs/${companyId}`, {
    method: 'PUT',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      modelName: config.modelName || config.model || 'EXAONE-3.5-7.8B-Instruct-AWQ',
      temperature: config.temperature ?? 0,
      apiKey: config.apiKey || '',
      systemPrompt: config.systemPrompt || ''
    }),
  })
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * GET /api/v1/bot-configs/{companyId}
 * Fetches the bot configuration for a specific company
 * @param {string} companyId
 * @param {string} token
 */
export async function getBotConfig(companyId, token) {
  const res = await fetch(`${BASE_URL}/api/v1/bot-configs/${companyId}`, {
    method: 'GET',
    headers: buildHeaders(token),
  })
  if (!res.ok) {
    if (res.status === 404) return null // If no config exists yet, handle gracefully
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * GET /api/v1/bot-configs/prompt-enhancement
 * Regenerates the system prompt from stored training data
 * @param {string} companyId
 * @param {number} sampleRows  default 500
 * @param {string} token
 */
export async function enhancePrompt(companyId, sampleRows = 500, token) {
  const params = new URLSearchParams({ companyId, sampleRows: String(sampleRows) })
  const res = await fetch(
    `${BASE_URL}/api/v1/bot-configs/prompt-enhancement?${params.toString()}`,
    {
      method: 'GET',
      headers: buildHeaders(token),
    }
  )
  if (!res.ok) {
    const msg = await res.text().catch(() => '')
    throw new Error(msg || `Request failed (${res.status})`)
  }
  return res.json()
}

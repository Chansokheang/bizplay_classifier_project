import { BASE_URL, buildHeaders } from './api'

/**
 * Parses error response from API and extracts clean error message
 */
async function getErrorMessage(res) {
  try {
    const contentType = res.headers.get('content-type')
    if (contentType && contentType.includes('application/json')) {
      const errorData = await res.json()
      return errorData.message || errorData.error || errorData.detail || 'An error occurred'
    } else {
      const text = await res.text()
      return text || 'An error occurred'
    }
  } catch (e) {
    return 'An error occurred'
  }
}

/**
 * PUT /api/v1/bot-configs/{corpNo} — upsert bot configuration for a corp
 * Provider / modelName / apiKey are sent as query params (per OpenAPI spec).
 * Body carries the BotConfig (temperature, systemPrompt, ...).
 *
 * @param {string} corpNo
 * @param {{ provider?: string, modelName?: string, apiKey?: string, temperature?: number, systemPrompt?: string }} config
 * @param {string} token
 */
export async function saveBotConfig(corpNo, config, token) {
  try {
    const params = new URLSearchParams()
    if (config.provider) params.set('provider', config.provider)
    if (config.modelName || config.model) params.set('modelName', config.modelName || config.model)
    if (config.apiKey) params.set('apiKey', config.apiKey)

    const qs = params.toString()
    const url = `${BASE_URL}/bot-configs/${encodeURIComponent(corpNo)}${qs ? `?${qs}` : ''}`

    const body = {
      temperature: config.temperature ?? 0,
      systemPrompt: config.systemPrompt || '',
    }

    const res = await fetch(url, {
      method: 'PUT',
      headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
      body: JSON.stringify(body),
    })

    if (!res.ok) {
      const errorMsg = await getErrorMessage(res)

      if (res.status === 401 || res.status === 403) {
        throw new Error('Authentication failed. Please log in again.')
      } else if (res.status === 400) {
        throw new Error(errorMsg)
      } else if (res.status === 404) {
        throw new Error(errorMsg || 'Corp not found')
      } else if (res.status >= 500) {
        throw new Error(errorMsg || 'Server error occurred')
      } else {
        throw new Error(errorMsg || 'Failed to save configuration')
      }
    }

    return res.json()
  } catch (err) {
    if (err instanceof TypeError && err.message.includes('fetch')) {
      throw new Error('Network error. Please check your internet connection.')
    }
    throw err
  }
}

/**
 * GET /api/v1/bot-configs/{corpNo}
 * Fetch the latest bot configuration for a corp.
 */
export async function getBotConfig(corpNo, token) {
  try {
    const res = await fetch(`${BASE_URL}/bot-configs/${encodeURIComponent(corpNo)}`, {
      method: 'GET',
      headers: buildHeaders(token),
    })

    if (!res.ok) {
      if (res.status === 404) return null

      const errorMsg = await getErrorMessage(res)

      if (res.status === 401 || res.status === 403) {
        throw new Error('Authentication failed. Please log in again.')
      } else if (res.status >= 500) {
        throw new Error(errorMsg || 'Server error occurred')
      } else {
        throw new Error(errorMsg || 'Failed to load configuration')
      }
    }

    return res.json()
  } catch (err) {
    if (err instanceof TypeError && err.message.includes('fetch')) {
      throw new Error('Network error. Please check your internet connection.')
    }
    throw err
  }
}

/**
 * POST /api/v1/bot-configs/create
 * Create a bot configuration. provider/modelName/apiKey go on the query string.
 */
export async function createBotConfig({ provider, modelName, apiKey, body = {} }, token) {
  const params = new URLSearchParams()
  if (provider) params.set('provider', provider)
  if (modelName) params.set('modelName', modelName)
  if (apiKey) params.set('apiKey', apiKey)

  const qs = params.toString()
  const url = `${BASE_URL}/bot-configs/create${qs ? `?${qs}` : ''}`

  const res = await fetch(url, {
    method: 'POST',
    headers: buildHeaders(token, { 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  })

  if (!res.ok) {
    const errorMsg = await getErrorMessage(res)
    throw new Error(errorMsg || `Request failed (${res.status})`)
  }
  return res.json()
}

/**
 * GET /api/v1/bot-configs/prompt-enhancement
 * Regenerates the system prompt from stored training data.
 */
export async function enhancePrompt(corpNo, sampleRows = 500, token) {
  try {
    const params = new URLSearchParams({ corpNo, sampleRows: String(sampleRows) })
    const res = await fetch(
      `${BASE_URL}/bot-configs/prompt-enhancement?${params.toString()}`,
      {
        method: 'GET',
        headers: buildHeaders(token),
      },
    )

    if (!res.ok) {
      const errorMsg = await getErrorMessage(res)

      if (res.status === 401 || res.status === 403) {
        throw new Error('Authentication failed. Please log in again.')
      } else if (res.status === 400) {
        throw new Error(errorMsg || 'Invalid request')
      } else if (res.status === 404) {
        throw new Error(errorMsg || 'No training data found')
      } else if (res.status >= 500) {
        throw new Error(errorMsg || 'Server error occurred')
      } else {
        throw new Error(errorMsg || 'Failed to enhance prompt')
      }
    }

    return res.json()
  } catch (err) {
    if (err instanceof TypeError && err.message.includes('fetch')) {
      throw new Error('Network error. Please check your internet connection.')
    }
    throw err
  }
}

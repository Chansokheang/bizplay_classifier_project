import { BASE_URL, buildHeaders } from './api'

/**
 * Parses error response from API and extracts clean error message
 */
async function getErrorMessage(res) {
  try {
    const contentType = res.headers.get('content-type')
    if (contentType && contentType.includes('application/json')) {
      const errorData = await res.json()
      // Extract message from common error response structures
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
 * PUT /api/v1/bot-configs/{companyId} — update or create bot configuration for a company
 * @param {string} companyId
 * @param {{ modelName: string, temperature: number, systemPrompt: string, apiKey: string }} config
 * @param {string} token
 */
export async function saveBotConfig(companyId, config, token) {
  try {
    const res = await fetch(`${BASE_URL}/bot-configs/${companyId}`, {
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
      const errorMsg = await getErrorMessage(res)

      // Provide context-specific error messages with API details
      if (res.status === 401 || res.status === 403) {
        throw new Error('Authentication failed. Please log in again.')
      } else if (res.status === 400) {
        throw new Error(errorMsg)
      } else if (res.status === 404) {
        throw new Error(errorMsg || 'Company not found')
      } else if (res.status >= 500) {
        throw new Error(errorMsg || 'Server error occurred')
      } else {
        throw new Error(errorMsg || 'Failed to save configuration')
      }
    }

    return res.json()
  } catch (err) {
    // Handle network errors
    if (err instanceof TypeError && err.message.includes('fetch')) {
      throw new Error('Network error. Please check your internet connection.')
    }
    // Re-throw if already a formatted error
    throw err
  }
}

/**
 * GET /api/v1/bot-configs/{companyId}
 * Fetches the bot configuration for a specific company
 * @param {string} companyId
 * @param {string} token
 */
export async function getBotConfig(companyId, token) {
  try {
    const res = await fetch(`${BASE_URL}/bot-configs/${companyId}`, {
      method: 'GET',
      headers: buildHeaders(token),
    })

    if (!res.ok) {
      if (res.status === 404) return null // If no config exists yet, handle gracefully

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
 * GET /api/v1/bot-configs/prompt-enhancement
 * Regenerates the system prompt from stored training data
 * @param {string} companyId
 * @param {number} sampleRows  default 500
 * @param {string} token
 */
export async function enhancePrompt(companyId, sampleRows = 500, token) {
  try {
    const params = new URLSearchParams({ companyId, sampleRows: String(sampleRows) })
    const res = await fetch(
      `${BASE_URL}/bot-configs/prompt-enhancement?${params.toString()}`,
      {
        method: 'GET',
        headers: buildHeaders(token),
      }
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

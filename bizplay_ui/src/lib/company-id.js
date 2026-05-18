/** Loose UUID match for Spring / Java serialized IDs */
export const COMPANY_UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export function isCompanyUuid(id) {
  return typeof id === 'string' && COMPANY_UUID_RE.test(id.trim())
}

// The Roster API: GET /api/companies and POST /api/roster.

/** The current Roster: every Company with its id, name, and status, sorted by name. */
export async function listCompanies() {
  const response = await fetch('/api/companies')
  if (!response.ok) throw new Error(`Loading the Roster failed with HTTP ${response.status}`)
  return response.json()
}

/**
 * Uploads a CSV file as the new Roster. Resolves to {@code { ok: true, companies }} with the new
 * Roster, or {@code { ok: false, errors }} with one {@code { line, reason }} per rejected row.
 */
export async function uploadRoster(file) {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch('/api/roster', { method: 'POST', body })
  if (response.status === 400) {
    const { errors } = await response.json()
    return { ok: false, errors }
  }
  if (!response.ok) throw new Error(`Uploading the Roster failed with HTTP ${response.status}`)
  const { companies } = await response.json()
  return { ok: true, companies }
}

// The Roster API: GET /api/companies, GET /api/companies/:id and POST /api/roster.

/**
 * The current Roster, sorted by name: every Company with its id, name, status, Open posting count,
 * last scraped time (null until scraped) and truncated flag.
 */
export async function listCompanies() {
  const response = await fetch('/api/companies')
  if (!response.ok) throw new Error(`Loading the Roster failed with HTTP ${response.status}`)
  return response.json()
}

/** One Company with the same header as its Roster row plus its Open postings, or null when no Company has that id. */
export async function getCompany(id) {
  const response = await fetch(`/api/companies/${id}`)
  if (response.status === 404) return null
  if (!response.ok) throw new Error(`Loading the Company failed with HTTP ${response.status}`)
  return response.json()
}

/**
 * Uploads a CSV file as the new Roster, which starts a Scrape Run over it. Resolves to
 * {@code { ok: true, companies, run }} with the new Roster and the started run (null when the
 * Roster is empty), or {@code { ok: false, errors }} with one {@code { line, reason }} per rejected
 * row.
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
  const { companies, run } = await response.json()
  return { ok: true, companies, run }
}

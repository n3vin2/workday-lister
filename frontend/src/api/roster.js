// The Roster API: GET /api/companies, GET /api/companies/:id, POST /api/companies/:id/retry and
// POST /api/roster.

/**
 * The current Roster, sorted by name: every Company with its id, name, status, Open posting count,
 * last scraped time (null until scraped), truncated flag, and the error reason when its status is
 * FAILED (null otherwise).
 */
export async function listCompanies() {
  const response = await fetch('/api/companies')
  if (!response.ok) throw new Error(`Loading the Roster failed with HTTP ${response.status}`)
  return response.json()
}

/**
 * One Company with the same header as its Roster row plus its Open postings, and its Closed ones
 * too when {@code includeClosed} is set. Resolves to {@code { ok: true, company }}, or
 * {@code { ok: false }} when no Company has that id.
 */
export async function getCompany(id, { includeClosed = false } = {}) {
  const query = includeClosed ? '?includeClosed=true' : ''
  const response = await fetch(`/api/companies/${id}${query}`)
  if (response.status === 404) return { ok: false }
  if (!response.ok) throw new Error(`Loading the Company failed with HTTP ${response.status}`)
  const company = await response.json()
  return { ok: true, company }
}

/**
 * Retry: starts a Scrape Run over just one Company, so a failed Company can be scraped again
 * without re-running the whole Roster; it proceeds in the background. Resolves to
 * {@code { ok: true, run }} with the started run; to {@code { ok: false, reason }} when the backend
 * refused because a run is active; or to {@code { ok: false }} when no Company has that id.
 */
export async function retryCompany(id) {
  const response = await fetch(`/api/companies/${id}/retry`, { method: 'POST' })
  if (response.status === 404) return { ok: false }
  if (response.status === 409) {
    const { reason } = await response.json()
    return { ok: false, reason }
  }
  if (!response.ok) throw new Error(`Retrying the Company failed with HTTP ${response.status}`)
  const run = await response.json()
  return { ok: true, run }
}

/**
 * Uploads a CSV file as the new Roster, which starts a Scrape Run over it. Resolves to
 * {@code { ok: true, companies, run }} with the new Roster and the started run (null when the
 * Roster is empty); to {@code { ok: false, errors }} with one {@code { line, reason }} per rejected
 * row; or to {@code { ok: false, reason }} when the upload was refused because a run is active.
 */
export async function uploadRoster(file) {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch('/api/roster', { method: 'POST', body })
  if (response.status === 400) {
    const { errors } = await response.json()
    return { ok: false, errors }
  }
  if (response.status === 409) {
    const { reason } = await response.json()
    return { ok: false, reason }
  }
  if (!response.ok) throw new Error(`Uploading the Roster failed with HTTP ${response.status}`)
  const { companies, run } = await response.json()
  return { ok: true, companies, run }
}

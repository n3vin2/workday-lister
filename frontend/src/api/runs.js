// The Scrape Run API: POST /api/runs.

/**
 * Starts a Scrape Run over the current Roster; it proceeds in the background. Resolves to
 * {@code { ok: true, run }} with the started run, or {@code { ok: false, reason }} when the backend
 * refused to start one.
 */
export async function startRun() {
  const response = await fetch('/api/runs', { method: 'POST' })
  if (response.status === 409) {
    const { reason } = await response.json()
    return { ok: false, reason }
  }
  if (!response.ok) throw new Error(`Starting a Scrape Run failed with HTTP ${response.status}`)
  const run = await response.json()
  return { ok: true, run }
}

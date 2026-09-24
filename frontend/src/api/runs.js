// The Scrape Run API: POST /api/runs, GET /api/runs/current and POST /api/runs/current/cancel.

/**
 * Starts a Scrape Run over the current Roster; it proceeds in the background. Resolves to
 * {@code { ok: true, run }} with the started run, or {@code { ok: false, reason }} when the backend
 * refused to start one: a run is already active, or the Roster is empty.
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

/**
 * The active Scrape Run's progress: Companies done out of the total, when it started, and each
 * Company's outcome status. Resolves to {@code { ok: true, run }}, or {@code { ok: false }} when
 * no run is active (the backend answers 204).
 */
export async function getCurrentRun() {
  const response = await fetch('/api/runs/current')
  if (response.status === 204) return { ok: false }
  if (!response.ok) {
    throw new Error(`Loading the current Scrape Run failed with HTTP ${response.status}`)
  }
  const run = await response.json()
  return { ok: true, run }
}

/**
 * Asks the active Scrape Run to stop; it does so at its next check, between Companies or between
 * pages. Resolves to {@code { ok: true, run }} with the run asked, or {@code { ok: false }} when
 * no run is active (the backend answers 204).
 */
export async function cancelRun() {
  const response = await fetch('/api/runs/current/cancel', { method: 'POST' })
  if (response.status === 204) return { ok: false }
  if (!response.ok) throw new Error(`Cancelling the Scrape Run failed with HTTP ${response.status}`)
  const run = await response.json()
  return { ok: true, run }
}

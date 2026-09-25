// Formatting shared by the screens.

const STATUS_LABELS = {
  NEVER_SCRAPED: 'Never scraped',
  QUEUED: 'Queued',
  IN_PROGRESS: 'In progress',
  SUCCEEDED: 'Succeeded',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled',
}

/**
 * A Company status, or a Company's outcome status within a Scrape Run, as the screens word it; an
 * unknown status is shown as the backend sent it.
 */
export function formatStatus(status) {
  return STATUS_LABELS[status] ?? status
}

const pad = (part) => String(part).padStart(2, '0')

/** An ISO instant as `YYYY-MM-DD HH:MM` in the browser's timezone, the same on every locale. */
export function formatDateTime(iso) {
  const date = new Date(iso)
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** A duration in milliseconds as `m:ss`, or `h:mm:ss` from an hour on. */
export function formatElapsed(ms) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${minutes}:${pad(seconds)}`
}

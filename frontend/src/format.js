// Formatting shared by the screens.

const STATUS_LABELS = {
  NEVER_SCRAPED: 'Never scraped',
  IN_PROGRESS: 'In progress',
  SUCCEEDED: 'Succeeded',
}

/** A Company status as the screens word it; an unknown status is shown as the backend sent it. */
export function formatStatus(status) {
  return STATUS_LABELS[status] ?? status
}

/** An ISO instant as `YYYY-MM-DD HH:MM` in the browser's timezone, the same on every locale. */
export function formatDateTime(iso) {
  const date = new Date(iso)
  const pad = (part) => String(part).padStart(2, '0')
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

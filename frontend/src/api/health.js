// Backend health, served by Spring Boot Actuator under the /api prefix.

/** Resolves to true when the backend answers the health check with status UP. */
export async function isBackendUp() {
  try {
    const response = await fetch('/api/health')
    if (!response.ok) return false
    const body = await response.json()
    return body.status === 'UP'
  } catch {
    return false
  }
}

import { useEffect, useState } from 'react'
import { isBackendUp } from '../api/health.js'

/**
 * The Roster screen. For now it only reports whether the backend is reachable; the Roster
 * table, CSV upload, and Scrape Run controls arrive in later tickets.
 */
export default function RosterPage() {
  const [backendUp, setBackendUp] = useState(null)

  useEffect(() => {
    let cancelled = false
    isBackendUp().then((up) => {
      if (!cancelled) setBackendUp(up)
    })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="mx-auto max-w-4xl p-6">
      <h1 className="text-2xl font-semibold">Workday Lister</h1>
      <p className="mt-2 text-sm text-gray-600">Your Roster of Companies and their Today's Postings.</p>
      <BackendStatus up={backendUp} />
    </main>
  )
}

function BackendStatus({ up }) {
  if (up === null) {
    return <p className="mt-6 text-gray-500">Checking backend…</p>
  }
  return up ? (
    <p className="mt-6 rounded bg-green-50 px-3 py-2 text-green-800">Backend reachable</p>
  ) : (
    <p className="mt-6 rounded bg-red-50 px-3 py-2 text-red-800">
      Backend unreachable. Is the Spring server running on port 8080?
    </p>
  )
}

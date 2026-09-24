import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getCompany } from '../api/roster.js'
import { formatDateTime, formatStatus } from '../format.js'

/**
 * The Company screen at /companies/:id: the Company's header (status, last scraped time, Open
 * count, truncated flag) and every Open posting as a card linking to Workday. Today's Postings
 * and the "show all" toggle arrive in a later ticket.
 */
export default function CompanyPage() {
  const { id } = useParams()
  const [company, setCompany] = useState(null)
  const [notFound, setNotFound] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    getCompany(id)
      .then((found) => {
        if (cancelled) return
        if (found === null) {
          setNotFound(true)
        } else {
          setCompany(found)
        }
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [id])

  return (
    <main className="mx-auto max-w-4xl p-6">
      <Link to="/" className="text-sm text-blue-700 hover:underline">
        ← Roster
      </Link>
      {notFound && (
        <p className="mt-6 rounded bg-gray-50 px-3 py-6 text-center text-gray-600">
          This Company is not in the Roster.
        </p>
      )}
      {loadFailed && (
        <p className="mt-6 rounded bg-red-50 px-3 py-2 text-red-800">
          Could not load this Company. Is the Spring server running on port 8080?
        </p>
      )}
      {company !== null && (
        <>
          <CompanyHeader company={company} />
          <Postings company={company} />
        </>
      )}
    </main>
  )
}

function CompanyHeader({ company }) {
  const count = company.truncated ? `${company.openCount}+` : company.openCount
  return (
    <header className="mt-4">
      <h1 className="text-2xl font-semibold">{company.name}</h1>
      <p className="mt-1 text-sm text-gray-600">
        Open postings: {count} · {formatStatus(company.status)}
        {company.lastScrapedAt && ` · Last scraped ${formatDateTime(company.lastScrapedAt)}`}
      </p>
      {company.truncated && (
        <p className="mt-2 rounded bg-amber-50 px-3 py-2 text-sm text-amber-900">
          Truncated: Workday lists at most 2,000 postings per Career Site, so this is a floor, not
          everything the Company has Open.
        </p>
      )}
    </header>
  )
}

function Postings({ company }) {
  if (company.status === 'NEVER_SCRAPED') {
    return (
      <p className="mt-6 rounded bg-gray-50 px-3 py-6 text-center text-gray-600">
        This Company has not been scraped yet. Start a Scrape Run from the Roster.
      </p>
    )
  }
  if (company.postings.length === 0) {
    return (
      <p className="mt-6 rounded bg-gray-50 px-3 py-6 text-center text-gray-600">
        No Open postings.
      </p>
    )
  }
  return (
    <ul aria-label="Open postings" className="mt-6 grid gap-3 sm:grid-cols-2">
      {company.postings.map((posting) => (
        <li key={posting.requisitionId} className="rounded border border-gray-200 p-4">
          <a
            href={posting.publicUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="font-medium text-blue-700 hover:underline"
          >
            {posting.title}
          </a>
          <p className="mt-1 text-sm text-gray-600">{posting.locationText}</p>
          <p className="mt-1 text-xs text-gray-500">{posting.requisitionId}</p>
        </li>
      ))}
    </ul>
  )
}

import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getCompany } from '../api/roster.js'
import Notice from '../components/Notice.jsx'
import { formatDateTime, formatStatus } from '../format.js'

/**
 * The Company screen at /companies/:id: the Company's header (status, last scraped time, Open
 * count, truncated flag) and every Open posting as a card linking to Workday. Closed postings are
 * hidden until "Include Closed postings" is ticked, which asks the backend for them again and then
 * shows them with a Closed marker. Today's Postings and the "show all" toggle arrive in a later
 * ticket.
 */
export default function CompanyPage() {
  const { id } = useParams()
  const [company, setCompany] = useState(null)
  const [includeClosed, setIncludeClosed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getCompany(id, { includeClosed })
      .then((result) => {
        if (cancelled) return
        if (result.ok) {
          setCompany(result.company)
        } else {
          setNotFound(true)
        }
        setLoading(false)
      })
      .catch(() => {
        if (cancelled) return
        setLoadFailed(true)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, includeClosed])

  return (
    <main className="mx-auto max-w-4xl p-6">
      <Link to="/" className="text-sm text-blue-700 hover:underline">
        ← Roster
      </Link>
      {company === null && !notFound && !loadFailed && <Notice>Loading the Company…</Notice>}
      {notFound && <Notice>This Company is not in the Roster.</Notice>}
      {loadFailed && (
        <p className="mt-6 rounded bg-red-50 px-3 py-2 text-red-800">
          Could not load this Company. Is the Spring server running on port 8080?
        </p>
      )}
      {company !== null && (
        <>
          <CompanyHeader company={company} />
          <Postings
            company={company}
            includeClosed={includeClosed}
            loading={loading}
            onIncludeClosedChange={setIncludeClosed}
          />
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

/**
 * The postings section: the include-closed option, then the cards, an empty notice, or a loading
 * notice while the postings are being fetched again after the option changed.
 */
function Postings({ company, includeClosed, loading, onIncludeClosedChange }) {
  if (company.status === 'NEVER_SCRAPED') {
    return (
      <Notice>This Company has not been scraped yet. Start a Scrape Run from the Roster.</Notice>
    )
  }
  const empty = company.postings.length === 0
  return (
    <section className="mt-6">
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={includeClosed}
          onChange={(event) => onIncludeClosedChange(event.target.checked)}
        />
        <span>Include Closed postings</span>
      </label>
      {loading && <Notice>Loading postings…</Notice>}
      {!loading && empty && (
        <Notice>{includeClosed ? 'No postings, Open or Closed.' : 'No Open postings.'}</Notice>
      )}
      {!loading && !empty && (
        <ul
          aria-label={includeClosed ? 'Open and Closed postings' : 'Open postings'}
          className="mt-4 grid gap-3 sm:grid-cols-2"
        >
          {company.postings.map((posting) => (
            <PostingCard key={posting.requisitionId} posting={posting} />
          ))}
        </ul>
      )}
    </section>
  )
}

/** One posting: its title links to Workday; a Closed posting is muted and marked as such. */
function PostingCard({ posting }) {
  const closed = posting.state === 'CLOSED'
  const border = closed ? 'border-dashed border-gray-300 bg-gray-50' : 'border-gray-200'
  return (
    <li className={`rounded border p-4 ${border}`}>
      <div className="flex items-start justify-between gap-2">
        <a
          href={posting.publicUrl}
          target="_blank"
          rel="noopener noreferrer"
          className={`font-medium hover:underline ${closed ? 'text-gray-500' : 'text-blue-700'}`}
        >
          {posting.title}
        </a>
        {closed && (
          <span className="shrink-0 rounded bg-gray-200 px-1.5 py-0.5 text-xs uppercase text-gray-700">
            Closed
          </span>
        )}
      </div>
      <p className="mt-1 text-sm text-gray-600">{posting.locationText}</p>
      <p className="mt-1 text-xs text-gray-500">{posting.requisitionId}</p>
    </li>
  )
}

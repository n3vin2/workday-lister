import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getCompany } from '../api/roster.js'
import Notice from '../components/Notice.jsx'
import { formatDateTime, formatStatus } from '../format.js'

/**
 * The Company screen at /companies/:id: the Company's header (today's count, Open count, status,
 * last scraped time, truncated flag) and its Today's Postings as cards linking to Workday, or every
 * Open posting once "Show all Open postings" is ticked. Closed postings are hidden until "Include
 * Closed postings" is ticked; either option asks the backend for the postings again, and a Closed
 * posting's card carries a Closed marker.
 */
export default function CompanyPage() {
  const { id } = useParams()
  const [showAll, setShowAll] = useState(false)
  const [includeClosed, setIncludeClosed] = useState(false)
  const [company, setCompany] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getCompany(id, { scope: showAll ? 'all' : 'today', includeClosed })
      .then((result) => {
        if (cancelled) return
        if (result.ok) {
          setCompany(result.company)
          setNotFound(false)
          setLoadFailed(false)
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
  }, [id, showAll, includeClosed])

  return (
    <main className="mx-auto max-w-4xl p-6">
      <Link to="/" className="text-sm text-blue-700 hover:underline">
        ← Roster
      </Link>
      {loading && company === null && <Notice>Loading the Company…</Notice>}
      {notFound && <Notice>This Company is not in the Roster.</Notice>}
      {loadFailed && (
        <p className="mt-6 rounded bg-red-50 px-3 py-2 text-red-800">
          Could not load this Company. Is the Spring server running on port 8080?
        </p>
      )}
      {company !== null && !notFound && !loadFailed && (
        <>
          <CompanyHeader company={company} />
          {company.status !== 'NEVER_SCRAPED' && (
            <PostingFilters
              showAll={showAll}
              includeClosed={includeClosed}
              onShowAllChange={setShowAll}
              onIncludeClosedChange={setIncludeClosed}
            />
          )}
          {loading ? (
            <Notice>Loading postings…</Notice>
          ) : (
            <Postings company={company} showAll={showAll} includeClosed={includeClosed} />
          )}
        </>
      )}
    </main>
  )
}

function CompanyHeader({ company }) {
  return (
    <header className="mt-4">
      <h1 className="text-2xl font-semibold">{company.name}</h1>
      <p className="mt-1 text-sm text-gray-600">
        Today's Postings: {company.todayCount} · Open postings: {openCount(company)} ·{' '}
        {formatStatus(company.status)}
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
 * Which postings the screen shows: just Today's Postings or every Open posting, and whether the
 * Closed ones among them are included.
 */
function PostingFilters({ showAll, includeClosed, onShowAllChange, onIncludeClosedChange }) {
  return (
    <div className="mt-6 flex flex-wrap gap-4 text-sm">
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={showAll}
          onChange={(event) => onShowAllChange(event.target.checked)}
        />
        <span>Show all Open postings</span>
      </label>
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={includeClosed}
          onChange={(event) => onIncludeClosedChange(event.target.checked)}
        />
        <span>Include Closed postings</span>
      </label>
    </div>
  )
}

/** The cards for the postings of the chosen scope, or a notice saying why there are none. */
function Postings({ company, showAll, includeClosed }) {
  if (company.status === 'NEVER_SCRAPED') {
    return (
      <Notice>This Company has not been scraped yet. Start a Scrape Run from the Roster.</Notice>
    )
  }
  if (company.postings.length === 0) {
    if (showAll) {
      return <Notice>{includeClosed ? 'No postings, Open or Closed.' : 'No Open postings.'}</Notice>
    }
    if (company.openCount === 0) return <Notice>No Open postings.</Notice>
    return (
      <Notice>
        No Today's Postings: none of this Company's {openCount(company)} Open postings has today's
        Posting Date. Tick "Show all Open postings" to see them.
      </Notice>
    )
  }
  return (
    <ul aria-label={listName(showAll, includeClosed)} className="mt-6 grid gap-3 sm:grid-cols-2">
      {company.postings.map((posting) => (
        <PostingCard key={posting.requisitionId} posting={posting} />
      ))}
    </ul>
  )
}

/** The list's accessible name, saying which postings it holds. */
function listName(showAll, includeClosed) {
  if (showAll) return includeClosed ? 'Open and Closed postings' : 'Open postings'
  return includeClosed ? "Today's Postings, Open and Closed" : "Today's Postings"
}

/**
 * One posting: its title linking to Workday, location, Posting Date when known, requisition ID. A
 * Closed posting is muted and marked as such.
 */
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
      {posting.postingDate && (
        <p className="mt-1 text-sm text-gray-600">Posted {posting.postingDate}</p>
      )}
      <p className="mt-1 text-xs text-gray-500">{posting.requisitionId}</p>
    </li>
  )
}

/** The Open count, marked as a floor when the Career Site hit Workday's cap. */
function openCount(company) {
  return company.truncated ? `${company.openCount}+` : company.openCount
}

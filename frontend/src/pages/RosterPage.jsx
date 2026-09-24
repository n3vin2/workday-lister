import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listCompanies, uploadRoster } from '../api/roster.js'
import { startRun } from '../api/runs.js'
import Notice from '../components/Notice.jsx'
import { formatDateTime, formatStatus } from '../format.js'

const TRUNCATED_TITLE =
  'Truncated: Workday lists at most 2,000 postings per Career Site, so this count is a floor.'

/**
 * The Roster screen: upload a CSV of Companies, start a Scrape Run over it, and see what the latest
 * run left for each Company. Live progress while a run is active arrives in a later ticket; until
 * then, reload to see a run's results.
 */
export default function RosterPage() {
  const [companies, setCompanies] = useState(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [rejectedRows, setRejectedRows] = useState([])

  useEffect(() => {
    let cancelled = false
    listCompanies()
      .then((roster) => {
        if (!cancelled) setCompanies(roster)
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  function reloadRoster() {
    listCompanies()
      .then((roster) => {
        setCompanies(roster)
        setLoadFailed(false)
      })
      .catch(() => setLoadFailed(true))
  }

  return (
    <main className="mx-auto max-w-4xl p-6">
      <h1 className="text-2xl font-semibold">Workday Lister</h1>
      <p className="mt-2 text-sm text-gray-600">Your Roster of Companies and their Today's Postings.</p>
      <UploadForm
        onUploaded={(result) => {
          if (result.ok) {
            setCompanies(result.companies)
            setLoadFailed(false)
            setRejectedRows([])
          } else {
            setRejectedRows(result.errors)
          }
        }}
      />
      <ScrapeNowButton
        disabled={companies === null || companies.length === 0}
        onStarted={reloadRoster}
      />
      {rejectedRows.length > 0 && <RejectedRows rows={rejectedRows} />}
      {loadFailed && (
        <p className="mt-6 rounded bg-red-50 px-3 py-2 text-red-800">
          Could not load the Roster. Is the Spring server running on port 8080?
        </p>
      )}
      {companies === null && !loadFailed && <Notice>Loading the Roster…</Notice>}
      {companies !== null &&
        (companies.length === 0 ? <EmptyRoster /> : <CompanyTable companies={companies} />)}
    </main>
  )
}

function UploadForm({ onUploaded }) {
  const [file, setFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [failure, setFailure] = useState(null)

  async function handleSubmit(event) {
    event.preventDefault()
    if (!file) return
    setUploading(true)
    setFailure(null)
    try {
      const result = await uploadRoster(file)
      if (result.ok) {
        event.target.reset()
        setFile(null)
      }
      onUploaded(result)
    } catch (error) {
      setFailure(error.message)
    } finally {
      setUploading(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mt-6 flex flex-wrap items-end gap-3 rounded border border-gray-200 p-4"
    >
      <label className="flex flex-col gap-1 text-sm">
        <span className="font-medium">Roster CSV</span>
        <input
          type="file"
          name="file"
          accept=".csv,text/csv"
          className="text-sm"
          onChange={(event) => setFile(event.target.files[0] ?? null)}
        />
      </label>
      <button
        type="submit"
        disabled={!file || uploading}
        className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        {uploading ? 'Uploading…' : 'Upload'}
      </button>
      <p className="basis-full text-xs text-gray-500">
        A header row <code>company,url</code>, then one Company per line. The upload replaces the
        whole Roster and starts a Scrape Run over it.
      </p>
      {failure && (
        <p role="alert" className="basis-full text-sm text-red-800">
          Upload failed: {failure}
        </p>
      )}
    </form>
  )
}

function ScrapeNowButton({ disabled, onStarted }) {
  const [starting, setStarting] = useState(false)
  const [failure, setFailure] = useState(null)

  async function handleClick() {
    setStarting(true)
    setFailure(null)
    try {
      const result = await startRun()
      if (result.ok) {
        onStarted()
      } else {
        setFailure(result.reason)
      }
    } catch (error) {
      setFailure(error.message)
    } finally {
      setStarting(false)
    }
  }

  return (
    <div className="mt-4 flex flex-wrap items-center gap-3">
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled || starting}
        className="rounded bg-blue-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        {starting ? 'Starting…' : 'Scrape now'}
      </button>
      <span className="text-xs text-gray-500">
        Reads every Career Site in the Roster again, one Company at a time. Reload to see results.
      </span>
      {failure && (
        <p role="alert" className="basis-full text-sm text-red-800">
          Could not start a Scrape Run: {failure}
        </p>
      )}
    </div>
  )
}

function RejectedRows({ rows }) {
  return (
    <section className="mt-6 rounded bg-red-50 px-4 py-3 text-red-800">
      <h2 className="font-medium">Upload rejected. Nothing was changed. Fix these rows and try again:</h2>
      <ul aria-label="Upload rejected" className="mt-2 list-disc pl-5 text-sm">
        {rows.map((row) => (
          <li key={`${row.line}:${row.reason}`}>
            Line {row.line}: {row.reason}
          </li>
        ))}
      </ul>
    </section>
  )
}

function EmptyRoster() {
  return (
    <Notice>
      Your Roster is empty. Upload a CSV of Companies and their Career Site URLs to get started.
    </Notice>
  )
}

function CompanyTable({ companies }) {
  return (
    <table className="mt-6 w-full text-left text-sm">
      <thead>
        <tr className="border-b border-gray-200 text-xs uppercase text-gray-500">
          <th className="py-2 pr-4">Company</th>
          <th className="py-2 pr-4">Open</th>
          <th className="py-2 pr-4">Last scraped</th>
          <th className="py-2">Status</th>
        </tr>
      </thead>
      <tbody>
        {companies.map((company) => (
          <tr key={company.id} className="border-b border-gray-100">
            <td className="py-2 pr-4 font-medium">
              <Link
                to={`/companies/${company.id}`}
                className="text-blue-700 hover:underline"
              >
                {company.name}
              </Link>
            </td>
            <td className="py-2 pr-4 tabular-nums">
              <OpenCount company={company} />
            </td>
            <td className="py-2 pr-4 text-gray-600">
              {company.lastScrapedAt ? formatDateTime(company.lastScrapedAt) : '—'}
            </td>
            <td className="py-2 text-gray-600">{formatStatus(company.status)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/** The Open posting count, a dash before the first scrape, and a marked floor when truncated. */
function OpenCount({ company }) {
  if (company.status === 'NEVER_SCRAPED') return '—'
  if (company.truncated) return <span title={TRUNCATED_TITLE}>{company.openCount}+</span>
  return company.openCount
}

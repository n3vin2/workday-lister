import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listCompanies, uploadRoster } from '../api/roster.js'
import { cancelRun, getCurrentRun, startRun } from '../api/runs.js'
import Notice from '../components/Notice.jsx'
import { formatDateTime, formatElapsed, formatStatus } from '../format.js'

const TRUNCATED_TITLE =
  'Truncated: Workday lists at most 2,000 postings per Career Site, so this count is a floor.'

/** How often the screen asks after the active run, and only then. */
const POLL_INTERVAL_MS = 2000

/**
 * The active run (null when none is) and then the Roster, in that order, so that when a poll finds
 * the run over, the Roster it shows is the one the run left behind, not one read a moment earlier.
 */
async function loadRunThenRoster() {
  const current = await getCurrentRun()
  const roster = await listCompanies()
  return { run: current.ok ? current.run : null, roster }
}

/**
 * The Roster screen: upload a CSV of Companies, start a Scrape Run over it, and see what the latest
 * run left for each Company. While a run is active the upload form and Scrape now give way to the
 * run's progress and a Cancel button, and each Company's row shows where the run is with it.
 */
export default function RosterPage() {
  const [companies, setCompanies] = useState(null)
  // The active run; null when none is, which is also how the screen starts until it has asked.
  const [activeRun, setActiveRun] = useState(null)
  const [loadFailed, setLoadFailed] = useState(false)
  const [rejectedRows, setRejectedRows] = useState([])
  const [refusal, setRefusal] = useState(null)

  function showLoaded({ run, roster }) {
    setActiveRun(run)
    setCompanies(roster)
    setLoadFailed(false)
    // A "run in progress" refusal is no longer news once the run has ended.
    if (run === null) setRefusal(null)
  }

  useEffect(() => {
    let cancelled = false
    loadRunThenRoster()
      .then((loaded) => {
        if (!cancelled) showLoaded(loaded)
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // While a run is active, poll it and the Roster; the interval is cleared as soon as a poll finds
  // no run active, so an idle tab does not keep asking.
  const polling = activeRun !== null
  useEffect(() => {
    if (!polling) return undefined
    let cancelled = false
    const timer = setInterval(() => {
      loadRunThenRoster()
        .then((loaded) => {
          if (!cancelled) showLoaded(loaded)
        })
        .catch(() => {
          if (!cancelled) setLoadFailed(true)
        })
    }, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [polling])

  /** A refused start or upload means a run this screen did not know about is active: show it. */
  function showRefusal(message) {
    setRefusal(message)
    getCurrentRun()
      .then((current) => setActiveRun(current.ok ? current.run : null))
      .catch(() => setLoadFailed(true))
  }

  function handleUploaded(result) {
    if (result.ok) {
      setCompanies(result.companies)
      setActiveRun(result.run)
      setLoadFailed(false)
      setRejectedRows([])
      setRefusal(null)
    } else if (result.errors) {
      setRejectedRows(result.errors)
    } else {
      showRefusal(`Upload refused: ${result.reason}`)
    }
  }

  function handleStarted(run) {
    setActiveRun(run)
    setRefusal(null)
  }

  return (
    <main className="mx-auto max-w-4xl p-6">
      <h1 className="text-2xl font-semibold">Workday Lister</h1>
      <p className="mt-2 text-sm text-gray-600">Your Roster of Companies and their Today's Postings.</p>
      {refusal && (
        <p role="alert" className="mt-4 rounded bg-red-50 px-3 py-2 text-sm text-red-800">
          {refusal}
        </p>
      )}
      {activeRun !== null ? (
        <RunProgress run={activeRun} />
      ) : (
        <>
          <UploadForm onUploaded={handleUploaded} />
          <ScrapeNowButton
            disabled={companies === null || companies.length === 0}
            onStarted={handleStarted}
            onRefused={(reason) => showRefusal(`Could not start a Scrape Run: ${reason}`)}
          />
        </>
      )}
      {rejectedRows.length > 0 && <RejectedRows rows={rejectedRows} />}
      {loadFailed && (
        <p className="mt-6 rounded bg-red-50 px-3 py-2 text-red-800">
          Could not load the Roster. Is the Spring server running on port 8080?
        </p>
      )}
      {companies === null && !loadFailed && <Notice>Loading the Roster…</Notice>}
      {companies !== null &&
        (companies.length === 0 ? (
          <EmptyRoster />
        ) : (
          <CompanyTable companies={companies} run={activeRun} />
        ))}
    </main>
  )
}

/**
 * The active run: how many Companies it is done with, how long it has been going, and Cancel. The
 * run stops at its next check between Companies or pages, so the button only asks; the next poll
 * shows the run gone.
 */
function RunProgress({ run }) {
  const [cancelling, setCancelling] = useState(false)
  const [failure, setFailure] = useState(null)
  const elapsed = formatElapsed(Date.now() - Date.parse(run.startedAt))

  async function handleCancel() {
    setCancelling(true)
    setFailure(null)
    try {
      await cancelRun()
    } catch (error) {
      setFailure(error.message)
      setCancelling(false)
    }
  }

  return (
    <section
      aria-label="Scrape Run in progress"
      className="mt-6 flex flex-wrap items-center gap-3 rounded border border-blue-200 bg-blue-50 p-4"
    >
      <div className="grow">
        <h2 className="font-medium">Scrape Run in progress</h2>
        <p className="mt-1 text-sm text-gray-700">
          {run.done} of {run.total} Companies done · {elapsed} elapsed
        </p>
      </div>
      <button
        type="button"
        onClick={handleCancel}
        disabled={cancelling}
        className="rounded border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium disabled:opacity-50"
      >
        {cancelling ? 'Cancelling…' : 'Cancel'}
      </button>
      {failure && (
        <p role="alert" className="basis-full text-sm text-red-800">
          Could not cancel the Scrape Run: {failure}
        </p>
      )}
    </section>
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

function ScrapeNowButton({ disabled, onStarted, onRefused }) {
  const [starting, setStarting] = useState(false)
  const [failure, setFailure] = useState(null)

  async function handleClick() {
    setStarting(true)
    setFailure(null)
    try {
      const result = await startRun()
      if (result.ok) {
        onStarted(result.run)
      } else {
        onRefused(result.reason)
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
        Reads every Career Site in the Roster again, one Company at a time.
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

/**
 * The Roster as a table. While a run is active, a Company's status is where the run is with it
 * (queued, in progress, done); otherwise it is what the latest run left behind.
 */
function CompanyTable({ companies, run }) {
  function statusOf(company) {
    const outcome = run?.outcomes.find((candidate) => candidate.companyId === company.id)
    return outcome ? outcome.status : company.status
  }

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
            <td className="py-2 text-gray-600">{formatStatus(statusOf(company))}</td>
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

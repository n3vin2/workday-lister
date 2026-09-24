import { fireEvent, render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router'
import { expect, test } from 'vitest'
import { server } from '../test/server.js'
import CompanyPage from './CompanyPage.jsx'

const engineer = {
  requisitionId: 'JR1990000',
  title: 'Senior Software Engineer',
  locationText: 'US, CA, Santa Clara',
  postedOnLabel: 'Posted Today',
  postingDate: '2026-09-21',
  state: 'OPEN',
  publicUrl:
    'https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/job/US-CA-Santa-Clara/Senior-Software-Engineer_JR1990000',
  firstSeenRunId: 1,
  lastSeenRunId: 1,
}
const tester = {
  requisitionId: 'JR1990001',
  title: 'Software QA Engineer',
  locationText: '2 Locations',
  postedOnLabel: 'Posted 30+ Days Ago',
  postingDate: null,
  state: 'OPEN',
  publicUrl:
    'https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/job/US-CA-Santa-Clara/Software-QA-Engineer_JR1990001',
  firstSeenRunId: 1,
  lastSeenRunId: 1,
}
const analyst = {
  requisitionId: 'JR1980000',
  title: 'Data Analyst',
  locationText: 'US, TX, Austin',
  postedOnLabel: 'Posted 30+ Days Ago',
  postingDate: null,
  state: 'CLOSED',
  publicUrl:
    'https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/job/US-TX-Austin/Data-Analyst_JR1980000',
  firstSeenRunId: 1,
  lastSeenRunId: 1,
}
const nvidia = {
  id: 2,
  name: 'NVIDIA',
  status: 'SUCCEEDED',
  todayCount: 1,
  openCount: 2,
  lastScrapedAt: '2026-09-21T15:00:00Z',
  truncated: false,
  postings: [engineer, tester],
}

/** Serves the same Company whatever scope is asked for. */
function companyIs(company) {
  server.use(http.get('/api/companies/2', () => HttpResponse.json(company)))
}

/**
 * Serves NVIDIA with one list of postings for scope=today and another for scope=all, failing a
 * request when `failing(scope)` says so; returns the scopes asked for so far, in order.
 */
function companyServes({ today, all, failing = () => false }) {
  const scopes = []
  server.use(
    http.get('/api/companies/2', ({ request }) => {
      const scope = new URL(request.url).searchParams.get('scope')
      scopes.push(scope)
      if (failing(scope)) return HttpResponse.error()
      return HttpResponse.json({ ...nvidia, postings: scope === 'all' ? all : today })
    }),
  )
  return scopes
}

// The backend hides Closed postings unless asked with includeClosed=true.
function companyWithClosedIs(company, closed) {
  server.use(
    http.get('/api/companies/2', ({ request }) => {
      const includeClosed = new URL(request.url).searchParams.get('includeClosed') === 'true'
      return HttpResponse.json(
        includeClosed ? { ...company, postings: [...company.postings, ...closed] } : company,
      )
    }),
  )
}

function toggleShowAll() {
  fireEvent.click(screen.getByLabelText(/show all open postings/i))
}

function toggleIncludeClosed() {
  fireEvent.click(screen.getByLabelText(/include closed postings/i))
}

function renderCompany() {
  return render(
    <MemoryRouter initialEntries={['/companies/2']}>
      <Routes>
        <Route path="/companies/:id" element={<CompanyPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

test("shows Today's Postings by default, each card with title, location, Posting Date and requisition ID", async () => {
  const scopes = companyServes({ today: [engineer], all: [engineer, tester] })

  renderCompany()

  const list = await screen.findByRole('list', { name: /today's postings/i })
  const cards = within(list).getAllByRole('listitem')
  expect(cards.map((card) => card.textContent)).toEqual([
    'Senior Software EngineerUS, CA, Santa ClaraPosted 2026-09-21JR1990000',
  ])
  expect(scopes).toEqual(['today'])
  expect(screen.getByLabelText(/show all open postings/i)).not.toBeChecked()
})

test("show all reveals every Open posting with its Posting Date where known; unticking returns to today's", async () => {
  companyServes({ today: [engineer], all: [engineer, tester] })
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })

  toggleShowAll()

  const all = await screen.findByRole('list', { name: /^open postings$/i })
  expect(within(all).getAllByRole('listitem').map((card) => card.textContent)).toEqual([
    'Senior Software EngineerUS, CA, Santa ClaraPosted 2026-09-21JR1990000',
    'Software QA Engineer2 LocationsJR1990001',
  ])
  expect(screen.getByLabelText(/show all open postings/i)).toBeChecked()

  toggleShowAll()

  const today = await screen.findByRole('list', { name: /today's postings/i })
  expect(within(today).getAllByRole('listitem')).toHaveLength(1)
})

test('a failed scope switch reports the failure instead of the wrong list, and the next switch recovers', async () => {
  let allFails = true
  companyServes({
    today: [engineer],
    all: [engineer, tester],
    failing: (scope) => scope === 'all' && allFails,
  })
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })

  toggleShowAll()

  expect(await screen.findByRole('alert')).toHaveTextContent(/could not load this company/i)
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument()

  allFails = false
  toggleShowAll()

  await screen.findByRole('list', { name: /today's postings/i })
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('a Company with nothing today explains why instead of a blank page, and show all still works', async () => {
  companyServes({ today: [], all: [engineer, tester] })

  renderCompany()

  expect(
    await screen.findByText(/none of this company's 2 open postings has today's posting date/i),
  ).toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()

  toggleShowAll()

  const all = await screen.findByRole('list', { name: /^open postings$/i })
  expect(within(all).getAllByRole('listitem')).toHaveLength(2)
})

test('a Company with nothing today, Closed ones included, says so and points at Show all', async () => {
  companyServes({ today: [], all: [engineer, tester] })
  renderCompany()
  await screen.findByText(/none of this company's 2 open postings has today's posting date/i)

  toggleIncludeClosed()

  expect(await screen.findByText(/no today's postings, open or closed/i)).toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
})

test("a card's title links to the posting on Workday in a new tab", async () => {
  companyIs(nvidia)

  renderCompany()

  const link = await screen.findByRole('link', { name: 'Senior Software Engineer' })
  expect(link).toHaveAttribute('href', engineer.publicUrl)
  expect(link).toHaveAttribute('target', '_blank')
  expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
})

test('shows the Company header with status, last scraped time, Open count, and a back link', async () => {
  companyIs(nvidia)

  renderCompany()

  expect(await screen.findByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument()
  expect(screen.getByText(/today's postings: 1/i)).toBeInTheDocument()
  expect(screen.getByText(/open postings: 2/i)).toBeInTheDocument()
  expect(screen.getByText(/succeeded/i)).toBeInTheDocument()
  expect(screen.getByText(/last scraped 2026-09-21 09:00/i)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /roster/i })).toHaveAttribute('href', '/')
  expect(screen.queryByText(/truncated/i)).not.toBeInTheDocument()
})

test('a truncated Company says its count is a floor', async () => {
  companyIs({ ...nvidia, openCount: 2000, truncated: true })

  renderCompany()

  expect(await screen.findByText(/open postings: 2000\+/i)).toBeInTheDocument()
  expect(screen.getByText(/truncated/i)).toBeInTheDocument()
})

test('a Company that has never been scraped says so instead of showing an empty list or a toggle', async () => {
  companyIs({
    ...nvidia,
    status: 'NEVER_SCRAPED',
    todayCount: 0,
    openCount: 0,
    lastScrapedAt: null,
    postings: [],
  })

  renderCompany()

  expect(await screen.findByText(/has not been scraped yet/i)).toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
  expect(screen.queryByLabelText(/show all open postings/i)).not.toBeInTheDocument()
  expect(screen.queryByText(/last scraped/i)).not.toBeInTheDocument()
  expect(screen.queryByLabelText(/include closed postings/i)).not.toBeInTheDocument()
})

test('a scraped Company with nothing Open says so', async () => {
  companyIs({ ...nvidia, todayCount: 0, openCount: 0, postings: [] })

  renderCompany()

  expect(await screen.findByText(/no open postings/i)).toBeInTheDocument()
})

test('Closed postings are hidden by default, behind an unticked "Include Closed postings"', async () => {
  companyWithClosedIs(nvidia, [analyst])
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })

  toggleShowAll()

  const list = await screen.findByRole('list', { name: /^open postings$/i })
  expect(within(list).getAllByRole('listitem')).toHaveLength(2)
  expect(screen.getByLabelText(/include closed postings/i)).not.toBeChecked()
})

test('Closed postings appear, marked Closed, once "Include Closed postings" is ticked', async () => {
  companyWithClosedIs(nvidia, [analyst])
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })
  toggleShowAll()
  await screen.findByRole('list', { name: /^open postings$/i })

  toggleIncludeClosed()

  const all = await screen.findByRole('list', { name: /open and closed postings/i })
  expect(within(all).getAllByRole('listitem').map((card) => card.textContent)).toEqual([
    'Senior Software EngineerUS, CA, Santa ClaraPosted 2026-09-21JR1990000',
    'Software QA Engineer2 LocationsJR1990001',
    'Data AnalystClosedUS, TX, AustinJR1980000',
  ])
  expect(screen.getByLabelText(/include closed postings/i)).toBeChecked()
})

test("ticking \"Include Closed postings\" on Today's Postings keeps that scope and adds the Closed ones", async () => {
  const queries = []
  server.use(
    http.get('/api/companies/2', ({ request }) => {
      const params = new URL(request.url).searchParams
      queries.push(`${params.get('scope')} ${params.get('includeClosed')}`)
      const closed = params.get('includeClosed') === 'true' ? [analyst] : []
      return HttpResponse.json({ ...nvidia, postings: [engineer, ...closed] })
    }),
  )
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })

  toggleIncludeClosed()

  const list = await screen.findByRole('list', { name: /today's postings, open and closed/i })
  expect(within(list).getAllByRole('listitem')).toHaveLength(2)
  expect(queries).toEqual(['today null', 'today true'])
})

test('a Company with nothing Open still offers its Closed postings, and its Open count stays 0', async () => {
  companyWithClosedIs({ ...nvidia, todayCount: 0, openCount: 0, postings: [] }, [analyst])
  renderCompany()
  await screen.findByText(/no open postings/i)
  toggleShowAll()

  toggleIncludeClosed()

  const all = await screen.findByRole('list', { name: /open and closed postings/i })
  expect(within(all).getAllByRole('listitem').map((card) => card.textContent)).toEqual([
    'Data AnalystClosedUS, TX, AustinJR1980000',
  ])
  expect(screen.getByText(/open postings: 0/i)).toBeInTheDocument()
  expect(screen.queryByText(/no open postings/i)).not.toBeInTheDocument()
})

test('while Closed postings are fetched, the cards give way to a loading notice', async () => {
  let release
  const held = new Promise((resolve) => {
    release = resolve
  })
  server.use(
    http.get('/api/companies/2', async ({ request }) => {
      if (new URL(request.url).searchParams.get('includeClosed') !== 'true') {
        return HttpResponse.json(nvidia)
      }
      await held
      return HttpResponse.json({ ...nvidia, postings: [...nvidia.postings, analyst] })
    }),
  )
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })
  toggleShowAll()
  await screen.findByRole('list', { name: /^open postings$/i })

  toggleIncludeClosed()

  expect(await screen.findByText(/loading postings/i)).toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'NVIDIA' })).toBeInTheDocument()
  release()
  const all = await screen.findByRole('list', { name: /open and closed postings/i })
  expect(within(all).getAllByRole('listitem')).toHaveLength(3)
})

test('a Company removed from the Roster while its screen is open says so on the next fetch', async () => {
  server.use(
    http.get('/api/companies/2', ({ request }) =>
      new URL(request.url).searchParams.get('includeClosed') === 'true'
        ? HttpResponse.json({}, { status: 404 })
        : HttpResponse.json(nvidia),
    ),
  )
  renderCompany()
  await screen.findByRole('list', { name: /today's postings/i })

  toggleIncludeClosed()

  expect(await screen.findByText(/not in the roster/i)).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'NVIDIA' })).not.toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
})

test('a Company with no postings at all says so once Closed ones are included', async () => {
  companyWithClosedIs({ ...nvidia, todayCount: 0, openCount: 0, postings: [] }, [])
  renderCompany()
  await screen.findByText(/no open postings/i)
  toggleShowAll()

  toggleIncludeClosed()

  expect(await screen.findByText(/no postings, open or closed/i)).toBeInTheDocument()
})

test('reports a Company that is not in the Roster, with the way back', async () => {
  server.use(http.get('/api/companies/2', () => HttpResponse.json({}, { status: 404 })))

  renderCompany()

  expect(await screen.findByText(/not in the roster/i)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /roster/i })).toHaveAttribute('href', '/')
})

test('reports when the Company cannot be loaded', async () => {
  server.use(http.get('/api/companies/2', () => HttpResponse.error()))

  renderCompany()

  expect(await screen.findByText(/could not load this company/i)).toBeInTheDocument()
})

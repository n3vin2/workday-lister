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
  openCount: 2,
  lastScrapedAt: '2026-09-21T15:00:00Z',
  truncated: false,
  postings: [engineer, tester],
}

function companyIs(company) {
  server.use(http.get('/api/companies/2', () => HttpResponse.json(company)))
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

function renderCompany() {
  return render(
    <MemoryRouter initialEntries={['/companies/2']}>
      <Routes>
        <Route path="/companies/:id" element={<CompanyPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

test('renders a card per Open posting with its title, location and requisition ID', async () => {
  companyIs(nvidia)

  renderCompany()

  const list = await screen.findByRole('list', { name: /open postings/i })
  const cards = within(list).getAllByRole('listitem')
  expect(cards.map((card) => card.textContent)).toEqual([
    'Senior Software EngineerUS, CA, Santa ClaraJR1990000',
    'Software QA Engineer2 LocationsJR1990001',
  ])
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

test('a Company that has never been scraped says so instead of showing an empty list', async () => {
  companyIs({ ...nvidia, status: 'NEVER_SCRAPED', openCount: 0, lastScrapedAt: null, postings: [] })

  renderCompany()

  expect(await screen.findByText(/has not been scraped yet/i)).toBeInTheDocument()
  expect(screen.queryByRole('list', { name: /open postings/i })).not.toBeInTheDocument()
  expect(screen.queryByText(/last scraped/i)).not.toBeInTheDocument()
  expect(screen.queryByLabelText(/include closed postings/i)).not.toBeInTheDocument()
})

test('a scraped Company with nothing Open says so', async () => {
  companyIs({ ...nvidia, openCount: 0, postings: [] })

  renderCompany()

  expect(await screen.findByText(/no open postings/i)).toBeInTheDocument()
})

test('Closed postings are hidden by default, behind an unticked "Include Closed postings"', async () => {
  companyWithClosedIs(nvidia, [analyst])

  renderCompany()

  const list = await screen.findByRole('list', { name: /open postings/i })
  expect(within(list).getAllByRole('listitem')).toHaveLength(2)
  expect(screen.getByLabelText(/include closed postings/i)).not.toBeChecked()
})

test('Closed postings appear, marked Closed, once "Include Closed postings" is ticked', async () => {
  companyWithClosedIs(nvidia, [analyst])
  renderCompany()
  await screen.findByRole('list', { name: /open postings/i })

  fireEvent.click(screen.getByLabelText(/include closed postings/i))

  const all = await screen.findByRole('list', { name: /open and closed postings/i })
  expect(within(all).getAllByRole('listitem').map((card) => card.textContent)).toEqual([
    'Senior Software EngineerUS, CA, Santa ClaraJR1990000',
    'Software QA Engineer2 LocationsJR1990001',
    'Data AnalystClosedUS, TX, AustinJR1980000',
  ])
  expect(screen.getByLabelText(/include closed postings/i)).toBeChecked()
})

test('a Company with nothing Open still offers its Closed postings, and its Open count stays 0', async () => {
  companyWithClosedIs({ ...nvidia, openCount: 0, postings: [] }, [analyst])
  renderCompany()
  await screen.findByText(/no open postings/i)

  fireEvent.click(screen.getByLabelText(/include closed postings/i))

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
  await screen.findByRole('list', { name: /open postings/i })

  fireEvent.click(screen.getByLabelText(/include closed postings/i))

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
  await screen.findByRole('list', { name: /open postings/i })

  fireEvent.click(screen.getByLabelText(/include closed postings/i))

  expect(await screen.findByText(/not in the roster/i)).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'NVIDIA' })).not.toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
})

test('a Company with no postings at all says so once Closed ones are included', async () => {
  companyWithClosedIs({ ...nvidia, openCount: 0, postings: [] }, [])
  renderCompany()
  await screen.findByText(/no open postings/i)

  fireEvent.click(screen.getByLabelText(/include closed postings/i))

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

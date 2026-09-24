import { fireEvent, render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router'
import { expect, test } from 'vitest'
import { server } from '../test/server.js'
import RosterPage from './RosterPage.jsx'

const acme = {
  id: 1,
  name: 'Acme',
  status: 'NEVER_SCRAPED',
  openCount: 0,
  lastScrapedAt: null,
  truncated: false,
}
const nvidia = {
  id: 2,
  name: 'NVIDIA',
  status: 'SUCCEEDED',
  openCount: 2000,
  lastScrapedAt: '2026-09-21T15:00:00Z',
  truncated: true,
}
const startedRun = { id: 7, status: 'RUNNING', companies: [] }

function rosterIs(companies) {
  server.use(http.get('/api/companies', () => HttpResponse.json(companies)))
}

function renderRoster() {
  return render(
    <MemoryRouter>
      <RosterPage />
    </MemoryRouter>,
  )
}

function chooseAndUpload(csv) {
  const file = new File([csv], 'roster.csv', { type: 'text/csv' })
  fireEvent.change(screen.getByLabelText(/roster csv/i), { target: { files: [file] } })
  fireEvent.click(screen.getByRole('button', { name: /^upload$/i }))
}

test('an empty Roster shows an upload prompt instead of a table', async () => {
  rosterIs([])

  renderRoster()

  expect(await screen.findByText(/your roster is empty/i)).toBeInTheDocument()
  expect(screen.getByLabelText(/roster csv/i)).toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test("lists each Company's Open count, last scraped time (in local time), and status", async () => {
  rosterIs([acme, nvidia])

  renderRoster()

  const rows = await screen.findAllByRole('row', { name: /never scraped|succeeded/i })
  expect(rows.map((row) => row.textContent)).toEqual([
    'Acme——Never scraped',
    'NVIDIA2000+2026-09-21 09:00Succeeded',
  ])
  expect(screen.queryByText(/your roster is empty/i)).not.toBeInTheDocument()
})

test("a truncated Company's Open count is marked as a floor", async () => {
  rosterIs([nvidia])

  renderRoster()

  const count = await screen.findByTitle(/truncated/i)
  expect(count).toHaveTextContent('2000+')
})

test("each Company's name links to its own screen", async () => {
  rosterIs([acme, nvidia])

  renderRoster()

  expect(await screen.findByRole('link', { name: 'Acme' })).toHaveAttribute('href', '/companies/1')
  expect(screen.getByRole('link', { name: 'NVIDIA' })).toHaveAttribute('href', '/companies/2')
})

test('Scrape now starts a run and refreshes the Roster', async () => {
  let runStarted = false
  server.use(
    http.get('/api/companies', () =>
      HttpResponse.json([runStarted ? { ...acme, status: 'IN_PROGRESS' } : acme]),
    ),
    http.post('/api/runs', () => {
      runStarted = true
      return HttpResponse.json(startedRun, { status: 202 })
    }),
  )
  renderRoster()
  await screen.findByText('Never scraped')

  fireEvent.click(screen.getByRole('button', { name: /scrape now/i }))

  expect(await screen.findByText('In progress')).toBeInTheDocument()
  expect(runStarted).toBe(true)
})

test('Scrape now is disabled while the Roster is empty', async () => {
  rosterIs([])

  renderRoster()

  await screen.findByText(/your roster is empty/i)
  expect(screen.getByRole('button', { name: /scrape now/i })).toBeDisabled()
})

test('reports why a run could not be started', async () => {
  rosterIs([acme])
  server.use(
    http.post('/api/runs', () =>
      HttpResponse.json({ reason: 'The Roster is empty; upload a CSV first' }, { status: 409 }),
    ),
  )
  renderRoster()
  await screen.findByText('Never scraped')

  fireEvent.click(screen.getByRole('button', { name: /scrape now/i }))

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Could not start a Scrape Run: The Roster is empty; upload a CSV first',
  )
})

test('a successful upload replaces the Roster with the returned Companies', async () => {
  rosterIs([])
  let uploadBody = null
  server.use(
    http.post('/api/roster', async ({ request }) => {
      uploadBody = await request.text()
      return HttpResponse.json({ companies: [acme, nvidia], run: startedRun })
    }),
  )
  renderRoster()
  await screen.findByText(/your roster is empty/i)

  chooseAndUpload('company,url\nNVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite\n')

  const rows = await screen.findAllByRole('row', { name: /never scraped|succeeded/i })
  expect(rows.map((row) => row.textContent)).toEqual([
    'Acme——Never scraped',
    'NVIDIA2000+2026-09-21 09:00Succeeded',
  ])
  expect(screen.queryByText(/your roster is empty/i)).not.toBeInTheDocument()
  expect(uploadBody).toContain('name="file"; filename="roster.csv"')
  expect(uploadBody).toContain('NVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite')
  expect(screen.getByRole('button', { name: /^upload$/i })).toBeDisabled()
})

test('a rejected upload lists every bad row with its line and reason and keeps the Roster', async () => {
  rosterIs([acme])
  server.use(
    http.post('/api/roster', () =>
      HttpResponse.json(
        {
          errors: [
            { line: 3, reason: 'Company name is blank' },
            { line: 5, reason: 'URL is malformed' },
          ],
        },
        { status: 400 },
      ),
    ),
  )
  renderRoster()
  await screen.findByText('Acme')

  chooseAndUpload('company,url\n')

  const errorList = await screen.findByRole('list', { name: /upload rejected/i })
  const items = within(errorList).getAllByRole('listitem')
  expect(items.map((item) => item.textContent)).toEqual([
    'Line 3: Company name is blank',
    'Line 5: URL is malformed',
  ])
  expect(screen.getByText('Acme')).toBeInTheDocument()
})

test('a successful upload clears the errors of an earlier rejected one', async () => {
  rosterIs([])
  let attempt = 0
  server.use(
    http.post('/api/roster', () => {
      attempt += 1
      return attempt === 1
        ? HttpResponse.json({ errors: [{ line: 2, reason: 'URL is malformed' }] }, { status: 400 })
        : HttpResponse.json({ companies: [acme], run: startedRun })
    }),
  )
  renderRoster()
  await screen.findByText(/your roster is empty/i)

  chooseAndUpload('company,url\nAcme,nope\n')
  await screen.findByText('Line 2: URL is malformed')
  chooseAndUpload('company,url\nAcme,https://acme.wd1.myworkdayjobs.com/Careers\n')

  await screen.findByText('Acme')
  expect(screen.queryByRole('list', { name: /upload rejected/i })).not.toBeInTheDocument()
})

test('reports when the Roster cannot be loaded', async () => {
  server.use(http.get('/api/companies', () => HttpResponse.error()))

  renderRoster()

  expect(await screen.findByText(/could not load the roster/i)).toBeInTheDocument()
})

test('reports when an upload fails for a reason other than a rejected file', async () => {
  rosterIs([acme])
  server.use(http.post('/api/roster', () => HttpResponse.error()))
  renderRoster()
  await screen.findByText('Acme')

  chooseAndUpload('company,url\n')

  expect(await screen.findByText(/upload failed/i)).toBeInTheDocument()
  expect(screen.getByText('Acme')).toBeInTheDocument()
})

test('shows the upload in flight until the backend answers', async () => {
  rosterIs([])
  let answer
  const answered = new Promise((resolve) => {
    answer = resolve
  })
  server.use(
    http.post('/api/roster', async () => {
      await answered
      return HttpResponse.json({ companies: [acme], run: startedRun })
    }),
  )
  renderRoster()
  await screen.findByText(/your roster is empty/i)

  chooseAndUpload('company,url\n')

  const inFlight = await screen.findByRole('button', { name: /uploading/i })
  expect(inFlight).toBeDisabled()
  answer()
  await screen.findByText('Acme')
  expect(screen.getByRole('button', { name: /^upload$/i })).toBeInTheDocument()
})

test('a successful upload replaces the message about the Roster failing to load', async () => {
  server.use(
    http.get('/api/companies', () => HttpResponse.error()),
    http.post('/api/roster', () => HttpResponse.json({ companies: [acme], run: startedRun })),
  )
  renderRoster()
  await screen.findByText(/could not load the roster/i)

  chooseAndUpload('company,url\nAcme,https://acme.wd1.myworkdayjobs.com/Careers\n')

  await screen.findByText('Acme')
  expect(screen.queryByText(/could not load the roster/i)).not.toBeInTheDocument()
})

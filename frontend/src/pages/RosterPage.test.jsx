import { fireEvent, render, screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { server } from '../test/server.js'
import RosterPage from './RosterPage.jsx'

const acme = { id: 1, name: 'Acme', status: 'NEVER_SCRAPED' }
const nvidia = { id: 2, name: 'NVIDIA', status: 'NEVER_SCRAPED' }

function rosterIs(companies) {
  server.use(http.get('/api/companies', () => HttpResponse.json(companies)))
}

function chooseAndUpload(csv) {
  const file = new File([csv], 'roster.csv', { type: 'text/csv' })
  fireEvent.change(screen.getByLabelText(/roster csv/i), { target: { files: [file] } })
  fireEvent.click(screen.getByRole('button', { name: /upload/i }))
}

test('an empty Roster shows an upload prompt instead of a table', async () => {
  rosterIs([])

  render(<RosterPage />)

  expect(await screen.findByText(/your roster is empty/i)).toBeInTheDocument()
  expect(screen.getByLabelText(/roster csv/i)).toBeInTheDocument()
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
})

test('lists every Company with its name and status', async () => {
  rosterIs([acme, nvidia])

  render(<RosterPage />)

  const rows = await screen.findAllByRole('row', { name: /never scraped/i })
  expect(rows.map((row) => row.textContent)).toEqual(['AcmeNever scraped', 'NVIDIANever scraped'])
  expect(screen.queryByText(/your roster is empty/i)).not.toBeInTheDocument()
})

test('a successful upload replaces the Roster with the returned Companies', async () => {
  rosterIs([])
  let uploadBody = null
  server.use(
    http.post('/api/roster', async ({ request }) => {
      uploadBody = await request.text()
      return HttpResponse.json({ companies: [acme, nvidia] })
    }),
  )
  render(<RosterPage />)
  await screen.findByText(/your roster is empty/i)

  chooseAndUpload('company,url\nNVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite\n')

  const rows = await screen.findAllByRole('row', { name: /never scraped/i })
  expect(rows.map((row) => row.textContent)).toEqual(['AcmeNever scraped', 'NVIDIANever scraped'])
  expect(screen.queryByText(/your roster is empty/i)).not.toBeInTheDocument()
  expect(uploadBody).toContain('name="file"; filename="roster.csv"')
  expect(uploadBody).toContain('NVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite')
  expect(screen.getByRole('button', { name: /upload/i })).toBeDisabled()
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
  render(<RosterPage />)
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
        : HttpResponse.json({ companies: [acme] })
    }),
  )
  render(<RosterPage />)
  await screen.findByText(/your roster is empty/i)

  chooseAndUpload('company,url\nAcme,nope\n')
  await screen.findByText('Line 2: URL is malformed')
  chooseAndUpload('company,url\nAcme,https://acme.wd1.myworkdayjobs.com/Careers\n')

  await screen.findByText('Acme')
  expect(screen.queryByRole('list', { name: /upload rejected/i })).not.toBeInTheDocument()
})

test('reports when the Roster cannot be loaded', async () => {
  server.use(http.get('/api/companies', () => HttpResponse.error()))

  render(<RosterPage />)

  expect(await screen.findByText(/could not load the roster/i)).toBeInTheDocument()
})

test('reports when an upload fails for a reason other than a rejected file', async () => {
  rosterIs([acme])
  server.use(http.post('/api/roster', () => HttpResponse.error()))
  render(<RosterPage />)
  await screen.findByText('Acme')

  chooseAndUpload('company,url\n')

  expect(await screen.findByText(/upload failed/i)).toBeInTheDocument()
  expect(screen.getByText('Acme')).toBeInTheDocument()
})

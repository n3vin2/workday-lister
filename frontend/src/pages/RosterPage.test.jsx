import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, test } from 'vitest'
import { server } from '../test/server.js'
import RosterPage from './RosterPage.jsx'

test('shows the backend is reachable when the health check reports UP', async () => {
  server.use(http.get('/api/health', () => HttpResponse.json({ status: 'UP' })))

  render(<RosterPage />)

  expect(await screen.findByText(/backend reachable/i)).toBeInTheDocument()
})

test('shows the backend is unreachable when the health check fails', async () => {
  server.use(http.get('/api/health', () => HttpResponse.error()))

  render(<RosterPage />)

  expect(await screen.findByText(/backend unreachable/i)).toBeInTheDocument()
})

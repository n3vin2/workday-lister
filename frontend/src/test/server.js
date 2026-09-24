import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

// Mock Service Worker server intercepting /api. Tests add handlers with server.use(...). By default
// no Scrape Run is active, as the Roster screen asks on every mount; a test that wants one active
// overrides this.
export const server = setupServer(
  http.get('/api/runs/current', () => new HttpResponse(null, { status: 204 })),
)

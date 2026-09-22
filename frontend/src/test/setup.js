import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { server } from './server.js'

// Node's fetch rejects relative URLs; the app uses them because Vite proxies /api in
// development. Resolve them against jsdom's origin so MSW can intercept the request.
const nodeFetch = globalThis.fetch
globalThis.fetch = (input, init) =>
  nodeFetch(
    typeof input === 'string' && input.startsWith('/')
      ? new URL(input, window.location.origin)
      : input,
    init,
  )

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
})
afterAll(() => server.close())

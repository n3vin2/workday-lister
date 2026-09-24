import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { encodeMultipart } from './multipart.js'
import { server } from './server.js'

// Times are shown in the browser's timezone; pin it so the tests' expected strings hold anywhere.
// Node re-reads TZ when process.env.TZ is assigned.
process.env.TZ = 'America/Regina'

// The app talks to the backend with relative URLs and browser FormData, both of which Vite and a
// real browser handle but Node's fetch does not:
// - Node's fetch rejects relative URLs; resolve them against jsdom's origin so MSW intercepts them.
// - Node's fetch does not recognise jsdom's FormData (it would send "[object FormData]"), so encode
//   it as multipart/form-data bytes (see multipart.js). Handlers read the upload with
//   request.text(); undici's request.formData() cannot run under jsdom because it builds jsdom File
//   objects and then rejects them.
// MSW replaces globalThis.fetch when it starts listening, so this wrapper is installed afterwards
// to sit in front of it.
function bridgeBrowserFetch() {
  const interceptedFetch = globalThis.fetch
  globalThis.fetch = async (input, init) => {
    const url =
      typeof input === 'string' && input.startsWith('/')
        ? new URL(input, window.location.origin)
        : input
    if (init?.body instanceof FormData) {
      const { body, contentType } = await encodeMultipart(init.body)
      const headers = new Headers(init.headers)
      headers.set('content-type', contentType)
      init = { ...init, body, headers }
    }
    return interceptedFetch(url, init)
  }
}

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' })
  bridgeBrowserFetch()
})
afterEach(() => {
  server.resetHandlers()
  cleanup()
})
afterAll(() => server.close())

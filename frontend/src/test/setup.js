import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { cleanup } from '@testing-library/react'
import { server } from './server.js'

// The app talks to the backend with relative URLs and browser FormData, both of which Vite and a
// real browser handle but Node's fetch does not:
// - Node's fetch rejects relative URLs; resolve them against jsdom's origin so MSW intercepts them.
// - Node's fetch does not recognise jsdom's FormData (it would send "[object FormData]"), so encode
//   it as multipart/form-data bytes here. Handlers read the upload with request.text(); undici's
//   request.formData() cannot run under jsdom because it builds jsdom File objects and rejects them.
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
      init = { ...init, body, headers: { ...init.headers, 'content-type': contentType } }
    }
    return interceptedFetch(url, init)
  }
}

async function encodeMultipart(form) {
  const boundary = `----jsdom-bridge-${Math.random().toString(16).slice(2)}`
  const encoder = new TextEncoder()
  const parts = []
  for (const [name, value] of form) {
    parts.push(encoder.encode(`--${boundary}\r\n`))
    if (typeof value === 'string') {
      parts.push(encoder.encode(`Content-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`))
    } else {
      parts.push(
        encoder.encode(
          `Content-Disposition: form-data; name="${name}"; filename="${value.name}"\r\n` +
            `Content-Type: ${value.type || 'application/octet-stream'}\r\n\r\n`,
        ),
      )
      parts.push(await readBytes(value))
      parts.push(encoder.encode('\r\n'))
    }
  }
  parts.push(encoder.encode(`--${boundary}--\r\n`))
  const body = new Uint8Array(parts.reduce((total, part) => total + part.byteLength, 0))
  let offset = 0
  for (const part of parts) {
    body.set(part, offset)
    offset += part.byteLength
  }
  return { body, contentType: `multipart/form-data; boundary=${boundary}` }
}

// jsdom's Blob has no arrayBuffer(); FileReader is the way to read it.
function readBytes(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(new Uint8Array(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsArrayBuffer(blob)
  })
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

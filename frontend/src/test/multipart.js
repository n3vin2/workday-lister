// Encodes a jsdom FormData as multipart/form-data bytes for Node's fetch. See setup.js for why.

/** Resolves to the encoded body and the content-type header (with boundary) to send it under. */
export async function encodeMultipart(form) {
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

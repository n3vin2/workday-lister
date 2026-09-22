import { setupServer } from 'msw/node'

// Mock Service Worker server intercepting /api. Tests add handlers with server.use(...).
export const server = setupServer()

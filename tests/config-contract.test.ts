import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

describe('local frontend/backend contract', () => {
  it('uses the same backend port in Spring and the Vite proxy', () => {
    const viteConfig = readFileSync(resolve(process.cwd(), 'vite.config.ts'), 'utf8')
    const applicationConfig = readFileSync(
      resolve(process.cwd(), 'backend/src/main/resources/application.yml'),
      'utf8'
    )
    const proxyPort = viteConfig.match(/target:\s*['"]http:\/\/127\.0\.0\.1:(\d+)['"]/)?.[1]
    const backendPort = applicationConfig.match(/server:\s*[\r\n]+\s+port:\s*(\d+)/)?.[1]

    expect(backendPort).toBeDefined()
    expect(proxyPort).toBe(backendPort)
  })

  it('passes every asset endpoint through in partial mock mode', () => {
    const handlers = readFileSync(resolve(process.cwd(), 'src/mocks/handlers.ts'), 'utf8')
    const assetRoutes = [
      "http.get('/api/assets'",
      "http.get('/api/assets/batch'",
      "http.get('/api/assets/:id'",
      "http.post('/api/assets'",
      "http.put('/api/assets/:id'",
      "http.post('/api/assets/:id/actions'"
    ]

    for (const route of assetRoutes) {
      const routeIndex = handlers.indexOf(route)
      expect(routeIndex, `${route} should exist`).toBeGreaterThanOrEqual(0)
      expect(handlers.slice(routeIndex, routeIndex + 180)).toContain(
        'if (shouldPassthroughTicketBackend) return passthrough()'
      )
    }
  })
})

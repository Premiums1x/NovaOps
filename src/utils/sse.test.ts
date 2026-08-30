import { afterEach, describe, expect, it, vi } from 'vitest'
import { streamSse } from './sse'

vi.mock('@/utils/request/token', () => ({ getAccessToken: () => 'test-token' }))

describe('streamSse', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('forwards route and original evidence events', async () => {
    const frames = [
      'event: route\ndata: {"route":"RAG","reason":"具体问题"}\n\n',
      'event: evidence\ndata: {"evidence":[{"chunkId":"chunk-12","content":"raw"}]}\n\n',
      'event: done\ndata: {}\n\n',
    ].join('')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(frames, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    })))
    const events: string[] = []

    await streamSse('/agent/chat', { content: 'question' }, (event) => events.push(event), new AbortController().signal)

    expect(events).toEqual(['route', 'evidence', 'done'])
  })
})

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

  it('throws the API message when HTTP 200 contains a JSON error envelope', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 500, message: '服务异常，请稍后重试' }), {
          status: 200,
          headers: { 'Content-Type': 'application/json;charset=UTF-8' },
        }),
      ),
    )

    await expect(
      streamSse('/agent/chat', { content: '如何使用当前知识库？' }, vi.fn(), new AbortController().signal),
    ).rejects.toThrow('服务异常，请稍后重试')
  })

  it('dispatches plan and step SSE frames', async () => {
    const stream = [
      'event: plan\ndata: {"steps":[{"action":"search_kb","status":"pending"}]}\n\n',
      'event: step\ndata: {"action":"search_kb","status":"running"}\n\n',
    ].join('')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream;charset=UTF-8' } }),
      ),
    )
    const onEvent = vi.fn()

    await streamSse('/agent/chat', { content: '问题' }, onEvent, new AbortController().signal)

    expect(onEvent).toHaveBeenNthCalledWith(1, 'plan', {
      steps: [{ action: 'search_kb', status: 'pending' }],
    })
    expect(onEvent).toHaveBeenNthCalledWith(2, 'step', {
      action: 'search_kb',
      status: 'running',
    })
  })
})

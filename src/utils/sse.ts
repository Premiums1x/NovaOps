import { getAccessToken } from '@/utils/request/token'
import type { AgentSseEvent } from '@/types/agent'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const KNOWN_EVENTS: AgentSseEvent[] = [
  'route',
  'plan',
  'step',
  'delta',
  'citation',
  'evidence',
  'meta',
  'done',
  'error',
]

const parseFrame = (frame: string) => {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  //SSE 规范要求多行 data 以换行拼接，JSON 载荷通常是单行，但网关可能拆行
  return { event, data: dataLines.join('\n') }
}

export const streamSse = async (
  path: string,
  body: unknown,
  onEvent: (event: AgentSseEvent, data: Record<string, unknown>) => void,
  signal: AbortSignal,
  retry = true,
): Promise<void> => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getAccessToken()}` },
    body: JSON.stringify(body),
    signal,
  })

  //SSE 不走 axios 拦截器，401 时自己刷新一次 token 再重试
  if (response.status === 401 && retry) {
    const { useAuthStore } = await import('@/store/auth')
    const { pinia } = await import('@/store')
    await useAuthStore(pinia).refresh()
    return streamSse(path, body, onEvent, signal, false)
  }
  if (!response.ok || !response.body) {
    throw new Error(response.status === 401 ? '登录状态已失效' : '问答服务连接失败')
  }
  const contentType = response.headers.get('Content-Type')?.toLowerCase() || ''
  if (!contentType.includes('text/event-stream')) {
    const raw = await response.text()
    let message = '问答服务返回了无法识别的响应'
    try {
      const payload = JSON.parse(raw) as { message?: unknown }
      if (typeof payload.message === 'string' && payload.message.trim()) message = payload.message
    } catch {
      // 非 JSON 响应使用统一提示，避免把网关 HTML 直接展示给用户
    }
    throw new Error(message)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  //读循环无论正常结束、抛错还是 abort，都必须释放 reader 并取消流，
  //否则连接与流锁会一直挂住直到 GC
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const { event, data } = parseFrame(frame)
        if (data && KNOWN_EVENTS.includes(event as AgentSseEvent)) {
          let payload: Record<string, unknown> | undefined
          try {
            payload = JSON.parse(data) as Record<string, unknown>
          } catch {
            //单帧 JSON 损坏只丢弃这一帧，不中断整条流
            payload = undefined
          }
          if (payload) {
            //onEvent 抛出的业务错误（如 error 事件）要向上传播给调用方处理
            onEvent(event as AgentSseEvent, payload)
          }
        }
        boundary = buffer.indexOf('\n\n')
      }
      if (done) break
    }
  } finally {
    reader.releaseLock()
    await response.body!.cancel().catch(() => {})
  }
}

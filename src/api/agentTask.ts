import request from '@/utils/request'
import { streamSse } from '@/utils/sse'

export interface AgentTaskDto {
  id: string
  goal: string
  status: string
  planJson?: string | null
  resultText?: string | null
  errorText?: string | null
  createdAt: string
  updatedAt: string
}

export interface AgentTaskStepDto {
  id: string
  seq: number | null
  kind: string
  toolName: string | null
  status: string
  argsJson: string | null
  observationJson: string | null
  createdAt: string | null
}

export type AgentTaskEventHandler = (event: string, data: Record<string, unknown>) => void

export const createTaskApi = (goal: string) => request.post<{ taskId: string }>('/agent/tasks', { goal })

export const confirmTaskApi = (id: string, confirmationId: string, approved: boolean) =>
  request.post<{ taskId: string; approved: boolean }>(`/agent/tasks/${id}/confirm`, { confirmationId, approved })

export const cancelTaskApi = (id: string) => request.post<null>(`/agent/tasks/${id}/cancel`)

export const getTaskApi = (id: string) =>
  request.get<{ task: AgentTaskDto; steps: AgentTaskStepDto[] }>(`/agent/tasks/${id}`)

export const listTasksApi = () => request.get<AgentTaskDto[]>('/agent/tasks')

export const streamTaskEvents = (id: string, onEvent: AgentTaskEventHandler, signal: AbortSignal) =>
  streamSse(`/agent/tasks/${id}/stream`, {}, onEvent as (e: string, d: Record<string, unknown>) => void, signal)

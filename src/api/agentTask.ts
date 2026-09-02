import request from '@/utils/request'
import { streamSse } from '@/utils/sse'
import type { TaskSseEvent } from '@/types/agent'

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
  revision?: number | null
  createdAt: string | null
}

export type AgentTaskEventHandler = (event: TaskSseEvent, data: Record<string, unknown>) => void

export interface AgentTaskPendingConfirmation {
  confirmationId: string
  tool: string
  title: string
  why?: string
  args?: Record<string, unknown> | null
  preview?: Record<string, unknown> | null
}

export interface AgentTaskAuditDto {
  id: string
  taskId: string | null
  source: string | null
  toolName: string | null
  argsDigest: string | null
  resultDigest: string | null
  writeOperation: boolean | null
  confirmed: boolean | null
  allowed: boolean | null
  createdAt: string | null
}

export interface AgentTaskStatsDto {
  total: number
  byStatus: Record<string, number>
  successRate: number
  avgSteps: number
  writeOperations: number
  confirmedOperations: number
}

export const createTaskApi = (goal: string) => request.post<{ taskId: string }>('/agent/tasks', { goal })

export const confirmTaskApi = (id: string, confirmationId: string, approved: boolean) =>
  request.post<{ taskId: string; approved: boolean }>(`/agent/tasks/${id}/confirm`, { confirmationId, approved })

export const cancelTaskApi = (id: string) => request.post<null>(`/agent/tasks/${id}/cancel`)

export const getTaskApi = (id: string) =>
  request.get<{ task: AgentTaskDto; steps: AgentTaskStepDto[]; pendingConfirmation?: AgentTaskPendingConfirmation | null }>(`/agent/tasks/${id}`)

export const listTasksApi = () => request.get<AgentTaskDto[]>('/agent/tasks')

export const getTaskAuditsApi = (id: string) =>
  request.get<AgentTaskAuditDto[]>(`/agent/tasks/${id}/audits`)

export const getTaskStatsApi = () => request.get<AgentTaskStatsDto>('/agent/tasks/stats')

export const streamTaskEvents = (id: string, onEvent: AgentTaskEventHandler, signal: AbortSignal) =>
  streamSse<TaskSseEvent>(`/agent/tasks/${id}/stream`, {}, onEvent, signal)

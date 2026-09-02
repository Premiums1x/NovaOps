//任务步骤 observationJson 的解析与耗时格式化：实时 SSE 与历史回放共用

export interface ParsedObservation {
  /** 引擎事件时间戳（毫秒 epoch）；旧格式记录无此字段 */
  at?: number
  observation: string
}

/**
 * 兼容两种落库格式：
 * - 新格式：{"at":123,"observation":{...}}（T8 起工具/总结步骤）
 * - 旧格式：裸 JSON 或任意文本
 */
export const parseStepObservation = (json: string | null | undefined): ParsedObservation => {
  if (!json) return { observation: '' }
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>
    if (parsed && typeof parsed === 'object' && 'at' in parsed && 'observation' in parsed) {
      const observation = typeof parsed.observation === 'string'
        ? parsed.observation
        : JSON.stringify(parsed.observation)
      return { at: Number(parsed.at), observation }
    }
  } catch {
    // 非 JSON 的历史文本按原样展示
  }
  return { observation: json }
}

export const formatDuration = (ms: number): string =>
  ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`

/**
 * 任务总耗时文本：优先用 plan→result 事件时间差（实时流），
 * 否则退化为首尾步骤的时间差（历史回放）。无可用时间戳返回 null。
 */
export const stepSpanText = (
  stepAts: Array<number | undefined>,
  planAt?: number,
  resultAt?: number,
): string | null => {
  if (planAt != null && resultAt != null && resultAt >= planAt) {
    return formatDuration(resultAt - planAt)
  }
  const valid = stepAts.filter((at): at is number => at != null)
  if (valid.length >= 2 && valid[valid.length - 1]! > valid[0]!) {
    return formatDuration(valid[valid.length - 1]! - valid[0]!)
  }
  return null
}

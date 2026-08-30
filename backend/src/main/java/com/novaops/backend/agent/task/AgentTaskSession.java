package com.novaops.backend.agent.task;

import com.novaops.backend.agent.engine.model.EngineEvent;
import com.novaops.backend.agent.engine.model.EngineState;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 一次任务运行的内存会话：引擎状态 + 事件历史 + 已附着的 SSE 连接。
 * 事件由引擎线程直接推送给所有连接（与既有 chat SSE 同一线程模型）；
 * 流断开后可重新附着并回放历史。会话不跨重启保留。
 */
public class AgentTaskSession {

  private final String taskId;
  private final EngineState state;
  private final List<EngineEvent> history = new ArrayList<>();
  private final List<SseEmitter> emitters = new ArrayList<>();
  private volatile Future<?> future;
  private volatile boolean terminal;

  public AgentTaskSession(String taskId, EngineState state) {
    this.taskId = taskId;
    this.state = state;
  }

  public String taskId() {
    return taskId;
  }

  public EngineState state() {
    return state;
  }

  public Future<?> future() {
    return future;
  }

  public void future(Future<?> future) {
    this.future = future;
  }

  public boolean isTerminal() {
    return terminal;
  }

  public void markTerminal() {
    terminal = true;
  }

  /** 引擎线程调用：记录事件并广播给所有存活连接。 */
  public synchronized void record(EngineEvent event) {
    history.add(event);
    for (SseEmitter emitter : new ArrayList<>(emitters)) {
      send(emitter, event);
    }
  }

  /** 新连接：回放全部历史事件；任务已终态则发送后立即完成。 */
  public synchronized SseEmitter attach() {
    SseEmitter emitter = new SseEmitter(0L);
    for (EngineEvent event : history) {
      send(emitter, event);
    }
    if (!terminal) {
      emitters.add(emitter);
    } else {
      emitter.complete();
    }
    return emitter;
  }

  private void send(SseEmitter emitter, EngineEvent event) {
    try {
      emitter.send(SseEmitter.event().name(event.type()).data(event.payload()));
      if (terminal && isTerminalEvent(event)) {
        emitter.complete();
      }
    } catch (Exception ex) {
      emitters.remove(emitter);
      emitter.completeWithError(ex);
    }
  }

  private boolean isTerminalEvent(EngineEvent event) {
    return "result".equals(event.type()) || "error".equals(event.type());
  }
}

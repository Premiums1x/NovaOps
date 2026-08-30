package com.novaops.backend.agent.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaops.backend.agent.engine.model.TaskPlan;
import org.junit.jupiter.api.Test;

class TaskPlanParserTest {

  private final TaskPlanParser parser = new TaskPlanParser(new ObjectMapper());

  @Test
  void parsesCleanPlanJson() {
    TaskPlan plan = parser.parse("""
        {"steps":[
          {"tool":"ticket.search","args":{"status":"pending"},"why":"找到目标工单"},
          {"tool":"ticket.detail","args":{"ticketId":"A-1"},"why":"查看详情"}
        ]}
        """);

    assertEquals(2, plan.steps().size());
    assertEquals("ticket.search", plan.steps().get(0).tool());
    assertEquals("pending", plan.steps().get(0).args().get("status"));
    assertEquals(1, plan.steps().get(0).seq());
  }

  @Test
  void toleratesMarkdownFenceAndProse() {
    TaskPlan plan = parser.parse("""
        以下是执行计划：
        ```json
        {"steps":[{"tool":"kb.search","args":{"query":"vpn"}}]}
        ```
        以上。
        """);

    assertEquals(1, plan.steps().size());
    assertEquals("kb.search", plan.steps().get(0).tool());
  }

  @Test
  void parsesAbortWithNote() {
    TaskPlan plan = parser.parse("{\"abort\":true,\"note\":\"缺少可用工具\"}");

    assertTrue(plan.abort());
    assertEquals("缺少可用工具", plan.note());
  }

  @Test
  void missingStepsThrows() {
    assertThrows(IllegalArgumentException.class, () -> parser.parse("{\"note\":\"nothing\"}"));
    assertThrows(IllegalArgumentException.class, () -> parser.parse("完全不是 JSON"));
  }

  @Test
  void stepWithoutToolThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse("{\"steps\":[{\"args\":{}}]}"));
  }
}

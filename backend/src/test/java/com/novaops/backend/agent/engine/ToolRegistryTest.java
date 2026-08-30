package com.novaops.backend.agent.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.novaops.backend.agent.engine.ToolDescriptor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @AgentTool(name = "kb.search", title = "知识检索", description = "检索知识库")
  static class SearchTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok("ok");
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object().string("query", "检索词", true);
    }
  }

  @AgentTool(name = "ticket.assign", title = "指派工单", description = "指派工单",
      permission = "ticket:assign", category = AgentToolCategory.WRITE)
  static class AssignTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok("ok");
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object().string("ticketId", "工单 ID", true);
    }
  }

  @Test
  void registersAnnotatedToolsAndExposesDescriptors() {
    ToolRegistry registry = new ToolRegistry(List.of(new SearchTool(), new AssignTool()));

    assertTrue(registry.find("kb.search").isPresent());
    assertEquals(2, registry.toolsFor(Set.of("ticket:assign")).size());
    assertEquals(1, registry.toolsFor(Set.of()).size());
    assertTrue(registry.find("kb.search").get().descriptor().inputSchema()
        .containsKey("properties"));
  }

  @Test
  void filtersToolsByPermission() {
    ToolRegistry registry = new ToolRegistry(List.of(new SearchTool(), new AssignTool()));

    List<ToolDescriptor> visible = registry.toolsFor(Set.of("kb:view"));
    assertEquals(1, visible.size());
    assertEquals("kb.search", visible.get(0).name());
  }

  @Test
  void mcpCatalogOnlyContainsReadableTools() {
    ToolRegistry registry = new ToolRegistry(List.of(new SearchTool(), new AssignTool()));

    List<ToolDescriptor> readable = registry.readableTools();
    assertEquals(1, readable.size());
    assertEquals("kb.search", readable.get(0).name());
  }

  @Test
  void duplicateToolNameFailsFast() {
    assertThrows(IllegalStateException.class,
        () -> new ToolRegistry(List.of(new SearchTool(), new SearchTool())));
  }

  static class UnAnnotatedTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok("ok");
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }
  }

  @Test
  void missingAnnotationFailsFast() {
    assertThrows(IllegalStateException.class,
        () -> new ToolRegistry(List.of(new UnAnnotatedTool())));
  }
}

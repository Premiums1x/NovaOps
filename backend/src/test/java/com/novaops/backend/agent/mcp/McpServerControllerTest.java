package com.novaops.backend.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolRegistry;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import com.novaops.backend.agent.task.mapper.AgentAuditMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class McpServerControllerTest {

  @com.novaops.backend.agent.engine.AgentTool(name = "kb.search", title = "知识库检索",
      description = "检索知识库", permission = "kb:view")
  static class FakeSearchTool implements AgentToolExecutor {
    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      return ToolResult.ok(Map.of("chunks", List.of(Map.of("chunkId", "chunk-1"))));
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object().string("query", "检索词", true);
    }
  }

  private final AgentAuditMapper auditMapper = mock(AgentAuditMapper.class);
  private final ToolRegistry registry = new ToolRegistry(List.of(new FakeSearchTool()));
  private final McpToolCatalog catalog = new McpToolCatalog(registry);

  private MockMvc mvc(String token) {
    return MockMvcBuilders
        .standaloneSetup(new McpServerController(catalog, auditMapper, new com.fasterxml.jackson.databind.ObjectMapper(), token))
        .build();
  }

  private static String rpc(String method, String params) {
    return """
        {"jsonrpc":"2.0","id":1,"method":"%s"%s}
        """.formatted(method, params == null ? "" : ",\"params\":" + params);
  }

  @Test
  void disabledWithoutToken() throws Exception {
    mvc("").perform(post("/api/mcp").content(rpc("tools/list", null))
        .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound());
  }

  @Test
  void rejectsWrongBearerToken() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer wrong")
        .content(rpc("tools/list", null))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void initializeReturnsProtocolVersionAndServerInfo() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer secret")
        .content(rpc("initialize", "{\"capabilities\":{},\"clientInfo\":{\"name\":\"test\"}}"))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("\"protocolVersion\":\"2025-03-26\"")))
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("\"name\":\"novaops\"")));
  }

  @Test
  void notificationsInitializedIsAccepted() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer secret")
        .content("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());
  }

  @Test
  void toolsListExposesReadableToolsWithSchema() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer secret")
        .content(rpc("tools/list", null))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("\"name\":\"kb_search\"")))
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("\"inputSchema\"")));
  }

  @Test
  void toolCallReturnsTextContentAndAudits() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer secret")
        .content(rpc("tools/call", "{\"name\":\"kb_search\",\"arguments\":{\"query\":\"vpn\"}}"))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("\"isError\":false")))
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("chunk-1")));
    verify(auditMapper).insertAudit(any());
  }

  @Test
  void unknownToolReturnsIsErrorResult() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer secret")
        .content(rpc("tools/call", "{\"name\":\"nope\",\"arguments\":{}}"))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("\"isError\":true")));
  }

  @Test
  void unknownMethodReturnsJsonRpcError() throws Exception {
    mvc("secret").perform(post("/api/mcp")
        .header("Authorization", "Bearer secret")
        .content(rpc("resources/list", null))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
            .contains("-32601")));
  }
}

package com.novaops.backend.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.novaops.backend.agent.engine.ToolContext;

import com.novaops.backend.agent.engine.ToolHandle;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.task.mapper.AgentAuditMapper;
import com.novaops.backend.agent.task.model.AgentAuditRecord;
import com.novaops.backend.common.util.IdGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Server 端点：把 NovaOps 的只读运维工具按 MCP 规范（JSON-RPC 2.0，
 * Streamable HTTP 无状态模式）暴露给外部 Agent 宿主。
 *
 * <p>安全模型：不走用户 JWT（外部宿主没有用户会话），而是独立的服务令牌
 * （Authorization: Bearer）。令牌未配置 = 端点整体禁用（fail-closed）。
 * 只暴露 READ 类工具；每次 tools/call 落审计（source=mcp）。
 */
@RestController
@RequestMapping("/api/mcp")
public class McpServerController {

  private static final Logger log = LoggerFactory.getLogger(McpServerController.class);
  private static final String PROTOCOL_VERSION = "2025-03-26";

  private final McpToolCatalog catalog;
  private final AgentAuditMapper auditMapper;
  private final ObjectMapper objectMapper;
  private final String token;

  public McpServerController(
      McpToolCatalog catalog,
      AgentAuditMapper auditMapper,
      ObjectMapper objectMapper,
      @Value("${app.agent.mcp.server.token:}") String token) {
    this.catalog = catalog;
    this.auditMapper = auditMapper;
    this.objectMapper = objectMapper;
    this.token = token;
  }

  @PostMapping
  public ResponseEntity<Map<String, Object>> handle(
      @RequestBody(required = false) Map<String, Object> body,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (token == null || token.isBlank()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    if (authorization == null || !authorization.equals("Bearer " + token)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    if (body == null || body.get("method") == null) {
      return ResponseEntity.badRequest().body(error(null, -32600, "无效的 JSON-RPC 请求"));
    }
    String method = String.valueOf(body.get("method"));
    Object id = body.get("id");
    boolean notification = id == null;
    Map<String, Object> params = body.get("params") instanceof Map<?, ?> map
        ? (Map<String, Object>) map
        : Map.of();

    return switch (method) {
      case "initialize" -> ResponseEntity.ok(result(id, Map.of(
          "protocolVersion", PROTOCOL_VERSION,
          "capabilities", Map.of("tools", Map.of("listChanged", false)),
          "serverInfo", Map.of("name", "novaops", "version", "0.1.0"))));
      case "notifications/initialized" -> ResponseEntity.accepted().build();
      case "tools/list" -> ResponseEntity.ok(result(id, Map.of("tools", catalog.listTools())));
      case "tools/call" -> ResponseEntity.ok(result(id, callTool(id, params)));
      default -> notification
          ? ResponseEntity.accepted().build()
          : ResponseEntity.ok(error(id, -32601, "不支持的方法: " + method));
    };
  }

  private Map<String, Object> callTool(Object id, Map<String, Object> params) {
    String mcpName = String.valueOf(params.get("name"));
    Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> map
        ? (Map<String, Object>) map
        : Map.of();
    ToolHandle handle = catalog.findHandleByMcpName(mcpName);
    if (handle == null) {
      return toolError("工具不存在或不可通过 MCP 调用: " + mcpName);
    }
    ToolContext ctx = new ToolContext("mcp-service", "novaops-mcp", catalog.servicePermissions());
    ToolResult result;
    try {
      result = handle.executor().execute(ctx, arguments, true);
    } catch (Exception ex) {
      log.warn("mcp tool call failed: {}", mcpName, ex);
      result = ToolResult.failed("工具执行异常：" + ex.getMessage());
    }
    audit(mcpName, arguments, result);
    return switch (result.status()) {
      case OK -> Map.of("content", List.of(text(json(result.payload()))), "isError", false);
      case EMPTY -> Map.of("content", List.of(text(result.message() == null ? "无结果" : result.message())),
          "isError", false);
      default -> toolError(result.message() == null ? "工具执行失败" : result.message());
    };
  }

  private Map<String, Object> toolError(String message) {
    return Map.of("content", List.of(text(message)), "isError", true);
  }

  private Map<String, Object> text(String value) {
    return Map.of("type", "text", "text", value);
  }

  private void audit(String mcpName, Map<String, Object> args, ToolResult result) {
    try {
      AgentAuditRecord record = new AgentAuditRecord();
      record.setId(IdGenerator.randomId("aud"));
      record.setUserId("mcp-service");
      record.setSource("mcp");
      record.setToolName(mcpName);
      record.setArgsDigest(json(args));
      record.setResultDigest(result.status() == ToolResult.Status.OK ? json(result.payload()) : result.message());
      record.setWriteOperation(false);
      record.setAllowed(result.status() != ToolResult.Status.FAILED);
      auditMapper.insertAudit(record);
    } catch (Exception ex) {
      log.warn("mcp audit persistence failed", ex);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return String.valueOf(value);
    }
  }

  private Map<String, Object> result(Object id, Map<String, Object> value) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("result", value);
    return response;
  }

  private Map<String, Object> error(Object id, int code, String message) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("error", Map.of("code", code, "message", message));
    return response;
  }
}

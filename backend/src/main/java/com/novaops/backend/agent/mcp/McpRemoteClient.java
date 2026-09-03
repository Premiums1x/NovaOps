package com.novaops.backend.agent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 远端 MCP JSON-RPC 客户端（JDK HttpClient，零新增依赖）。
 * 传输：Streamable HTTP（POST JSON-RPC，application/json 响应）；
 * 握手：initialize → notifications/initialized → tools/list。
 */
@Component
public class McpRemoteClient {

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper;
  private final AtomicLong ids = new AtomicLong();

  public McpRemoteClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 执行 initialize 握手，返回服务端信息。 */
  public Map<String, Object> initialize(String endpoint, String token, int timeoutSeconds) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("protocolVersion", "2025-03-26");
    params.put("capabilities", Map.of());
    params.put("clientInfo", Map.of("name", "novaops-agent", "version", "0.1.0"));
    JsonNode result = rpc(endpoint, token, timeoutSeconds, "initialize", params);
    return objectMapper.convertValue(result, Map.class);
  }

  /**
   * 发送 initialized 通知（JSON-RPC 通知：无 id）。MCP 规范要求 initialize
   * 成功后先发本通知再 tools/list；通知不要求响应体（202/空 body 均视为成功）。
   */
  public void notifyInitialized(String endpoint, String token, int timeoutSeconds) {
    try {
      Map<String, Object> notification = new LinkedHashMap<>();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/initialized");
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(notification)));
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("远端 MCP 返回 HTTP " + response.statusCode());
      }
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("远端 MCP 调用失败：" + ex.getMessage(), ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("远端 MCP 调用被中断", ex);
    }
  }

  /** 返回远端工具清单：[{name, description, inputSchema}]。 */
  public JsonNode listTools(String endpoint, String token, int timeoutSeconds) {
    JsonNode result = rpc(endpoint, token, timeoutSeconds, "tools/list", Map.of());
    return result.path("tools");
  }

  /** 调用远端工具，返回 tools/call 的 result 节点（content[] + isError）。 */
  public JsonNode callTool(
      String endpoint, String token, int timeoutSeconds, String toolName, Map<String, Object> args) {
    return rpc(endpoint, token, timeoutSeconds, "tools/call",
        Map.of("name", toolName, "arguments", args == null ? Map.of() : args));
  }

  private JsonNode rpc(
      String endpoint, String token, int timeoutSeconds, String method, Map<String, Object> params) {
    try {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("jsonrpc", "2.0");
      request.put("id", ids.incrementAndGet());
      request.put("method", method);
      if (params != null) {
        request.put("params", params);
      }
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(URI.create(endpoint))
          .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
      if (token != null && !token.isBlank()) {
        builder.header("Authorization", "Bearer " + token);
      }
      HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("远端 MCP 返回 HTTP " + response.statusCode());
      }
      JsonNode root = objectMapper.readTree(response.body());
      if (root.hasNonNull("error")) {
        throw new IllegalStateException("远端 MCP 错误: " + root.path("error").path("message").asText("未知"));
      }
      return root.path("result");
    } catch (IllegalStateException | java.io.IOException ex) {
      throw new IllegalStateException("远端 MCP 调用失败：" + ex.getMessage(), ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("远端 MCP 调用被中断", ex);
    }
  }
}

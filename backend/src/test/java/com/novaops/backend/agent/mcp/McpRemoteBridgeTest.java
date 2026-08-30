package com.novaops.backend.agent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.novaops.backend.agent.engine.ToolRegistry;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolContext;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpRemoteBridgeTest {

  private HttpServer server;
  private final McpRemoteClient client = new McpRemoteClient(new com.fasterxml.jackson.databind.ObjectMapper());
  private ToolRegistry registry;
  private McpRemoteToolBridge bridge;
  private String lastToolCallName;
  private String lastToolCallArgs;

  @BeforeEach
  void setUp() throws Exception {
    registry = new ToolRegistry(java.util.List.of());
    bridge = new McpRemoteToolBridge(
        client, new McpRemoteProperties(), registry, new com.fasterxml.jackson.databind.ObjectMapper());

    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/mcp", exchange -> {
      byte[] body = exchange.getRequestBody().readAllBytes();
      String request = new String(body, StandardCharsets.UTF_8);
      String response;
      if (request.contains("\"method\":\"initialize\"")) {
        response = """
            {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26",
             "serverInfo":{"name":"fake-remote","version":"1.0"}}}""".replace("\n", "");
      } else if (request.contains("\"method\":\"tools/list\"")) {
        response = """
            {"jsonrpc":"2.0","id":2,"result":{"tools":[
              {"name":"web_search","description":"联网搜索",
               "inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}
            ]}}""".replace("\n", "");
      } else if (request.contains("\"method\":\"tools/call\"")) {
        if (request.contains("\"name\":\"web_search\"")) {
            lastToolCallName = "web_search";
            lastToolCallArgs = request;
        }
        response = """
            {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"找到 3 条搜索结果"}],"isError":false}}""".replace("\n", "");
      } else {
        response = "{\"jsonrpc\":\"2.0\",\"id\":9,\"error\":{\"code\":-32601,\"message\":\"unknown\"}}";
      }
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(response.getBytes(StandardCharsets.UTF_8));
      }
    });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private McpRemoteProperties.Server enabledServer() {
    McpRemoteProperties.Server server = new McpRemoteProperties.Server();
    server.setName("tavily");
    server.setEndpoint("http://127.0.0.1:" + fixedPort() + "/mcp");
    server.setEnabled(true);
    server.setToken("tavily-key");
    server.setTimeoutSeconds(5);
    return server;
  }

  @Test
  void discoversRemoteToolsAndRegistersThem() {
    int registered = bridge.discoverAndRegister(enabledServer());

    assertEquals(1, registered);
    assertTrue(registry.find("mcp_tavily_web_search").isPresent());
    assertTrue(registry.readableTools().stream()
        .anyMatch(descriptor -> "mcp_tavily_web_search".equals(descriptor.name())));
  }

  @Test
  void remoteToolExecutionRoundTripsThroughMcp() {
    bridge.discoverAndRegister(enabledServer());

    var handle = registry.find("mcp_tavily_web_search").orElseThrow();
    ToolResult result = handle.executor().execute(
        new ToolContext("u-1", "tester", Set.of()), Map.of("query", "novaops"), false);

    assertEquals(ToolResult.Status.OK, result.status());
    assertTrue(result.payload().toString().contains("找到 3 条搜索结果"));
    assertEquals("web_search", lastToolCallName);
    assertTrue(lastToolCallArgs.contains("novaops"));
  }

  private int fixedPort() {
    return server.getAddress().getPort();
  }
}

package com.novaops.backend.agent.tools;

import com.novaops.backend.agent.engine.AgentTool;
import com.novaops.backend.agent.engine.AgentToolExecutor;
import com.novaops.backend.agent.engine.ToolContext;
import com.novaops.backend.agent.engine.ToolResult;
import com.novaops.backend.agent.engine.ToolSchema;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataDocument;
import com.novaops.backend.kb.dto.KnowledgeBaseMetadataSnapshot;
import com.novaops.backend.kb.dto.RetrievalChunk;
import com.novaops.backend.common.util.StringUtils;
import com.novaops.backend.kb.dto.RetrievalResult;
import com.novaops.backend.kb.service.KbMetadataService;
import com.novaops.backend.kb.service.KbRetrievalService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 知识库只读工具：向量检索与文档元数据总览，为 Agent 提供"查手册"能力。
 */
public final class KbReadTools {

  private static final int CHUNK_TEXT_LIMIT = 400;
  private static final int METADATA_DOCUMENT_LIMIT = 50;

  private KbReadTools() {
  }

  @Component
  @AgentTool(name = "kb.search", title = "知识库检索", description = "在知识库中检索与查询相关的文档片段（含来源与得分）",
      permission = "kb:view")
  public static class KbSearchTool implements AgentToolExecutor {

    private final KbRetrievalService retrievalService;
    private final double minScore;

    public KbSearchTool(
        KbRetrievalService retrievalService,
        @Value("${app.agent.min-score:0.55}") double minScore) {
      this.retrievalService = retrievalService;
      this.minScore = minScore;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      String query = ToolArgs.asString(args, "query");
      if (query == null) {
        return ToolResult.failed("缺少必填参数：query");
      }
      int topK = ToolArgs.asInt(args, "topK", 5, 1, 8);
      try {
        RetrievalResult result = retrievalService.retrieve(query, topK, minScore);
        List<RetrievalChunk> chunks = result == null || result.chunks() == null
            ? List.of()
            : result.chunks();
        if (chunks.isEmpty()) {
          return ToolResult.empty("知识库中没有与查询相关的内容");
        }
        List<Map<String, Object>> hits = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
          RetrievalChunk chunk = chunks.get(index);
          Map<String, Object> hit = new LinkedHashMap<>();
          hit.put("index", index + 1);
          hit.put("chunkId", chunk.chunkId());
          hit.put("documentName", chunk.documentName());
          hit.put("score", chunk.score());
          hit.put("content", truncate(chunk.content()));
          hits.add(hit);
        }
        return ToolResult.ok(Map.of("query", query, "chunks", hits));
      } catch (Exception ex) {
        return ToolResult.failed("知识库检索服务暂不可用：" + ex.getMessage());
      }
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object()
          .string("query", "检索查询词", true)
          .integer("topK", "返回片段数（1-8，默认 5）", false);
    }
  }

  @Component
  @AgentTool(name = "kb.metadata", title = "知识库总览", description = "查看知识库收录了哪些文档及其状态概览",
      permission = "kb:view")
  public static class KbMetadataTool implements AgentToolExecutor {

    private final KbMetadataService metadataService;

    public KbMetadataTool(KbMetadataService metadataService) {
      this.metadataService = metadataService;
    }

    @Override
    public ToolResult execute(ToolContext ctx, Map<String, Object> args, boolean confirmed) {
      try {
        KnowledgeBaseMetadataSnapshot snapshot = metadataService.snapshot();
        List<Map<String, Object>> documents = new ArrayList<>();
        if (snapshot.documents() != null) {
          snapshot.documents().stream()
              .limit(METADATA_DOCUMENT_LIMIT)
              .forEach(document -> documents.add(toDocument(document)));
        }
        return ToolResult.ok(Map.of(
            "totalDocuments", snapshot.totalDocuments(),
            "readyDocuments", snapshot.readyDocuments(),
            "listTruncated", snapshot.truncated(),
            "documents", documents));
      } catch (Exception ex) {
        return ToolResult.failed("知识库元数据服务暂不可用：" + ex.getMessage());
      }
    }

    @Override
    public ToolSchema inputSchema() {
      return ToolSchema.object();
    }

    private Map<String, Object> toDocument(KnowledgeBaseMetadataDocument document) {
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("title", document.title());
      view.put("fileName", document.fileName());
      view.put("type", document.fileType());
      view.put("status", document.status());
      view.put("chunks", document.chunkCount());
      return view;
    }
  }

  private static String truncate(String text) {
    if (text == null) {
      return "";
    }
    // 按码点截断，避免 emoji 等代理对被切成乱码
    return text.length() <= CHUNK_TEXT_LIMIT
        ? text
        : StringUtils.truncateByCodePoints(text, CHUNK_TEXT_LIMIT) + "…(片段截断)";
  }
}

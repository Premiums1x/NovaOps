package com.novaops.backend.kb.service;

import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class KbIngestionService {

  private final KbMapper mapper;
  private final TextChunker chunker;
  private final QdrantVectorGateway vectorStore;

  public KbIngestionService(KbMapper mapper, TextChunker chunker, QdrantVectorGateway vectorStore) {
    this.mapper = mapper;
    this.chunker = chunker;
    this.vectorStore = vectorStore;
  }

  @Async
  public void process(KbDocumentRecord source) {
    List<String> vectorIds = new ArrayList<>();
    try (InputStream input = Files.newInputStream(Path.of(source.getStoragePath()))) {
      StructuredTextParser.Result parsed = "md".equals(source.getFileType()) ? parseMarkdown(input) : parseWithTika(input);
      List<String> chunks = chunker.split(parsed.blocks(), parsed.plainText());
      if (chunks.isEmpty()) {
        throw new IllegalStateException("文档未解析出有效文本");
      }
      mapper.updateStatus(source.getId(), "VECTORIZING", 0, null);

      List<QdrantVectorGateway.VectorPoint> vectors = new ArrayList<>();
      List<KbChunkRecord> records = new ArrayList<>();
      for (int index = 0; index < chunks.size(); index++) {
        String chunkId = IdGenerator.randomId("chunk");
        String vectorId = UUID.randomUUID().toString();
        vectorIds.add(vectorId);
        Map<String, Object> metadata = Map.of("documentId", source.getId(), "chunkId", chunkId, "documentName", source.getTitle());
        vectors.add(new QdrantVectorGateway.VectorPoint(vectorId, chunks.get(index), metadata));
        KbChunkRecord record = new KbChunkRecord();
        record.setId(chunkId);
        record.setDocumentId(source.getId());
        record.setChunkIndex(index);
        record.setContent(chunks.get(index));
        record.setVectorId(vectorId);
        records.add(record);
      }
      vectorStore.add(vectors);
      mapper.insertChunks(records);
      mapper.updateStatus(source.getId(), "READY", records.size(), null);
    } catch (Exception ex) {
      if (!vectorIds.isEmpty()) {
        try {
          vectorStore.delete(vectorIds);
        } catch (Exception ignored) {
        }
      }
      String message = ex.getMessage() == null ? "解析或向量化失败" : ex.getMessage();
      mapper.updateStatus(source.getId(), "FAILED", 0, message.substring(0, Math.min(900, message.length())));
    }
  }

  /** md 文件走原生 Markdown 解析:Tika 标准包无 markdown 解析器,交给它只会退化为纯文本。 */
  private StructuredTextParser.Result parseMarkdown(InputStream input) {
    try {
      return StructuredTextParser.parseMarkdown(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException("文档解析失败: " + ex.getMessage(), ex);
    }
  }

  private StructuredTextParser.Result parseWithTika(InputStream input) {
    // ToXMLContentHandler 保留标题/段落/代码等 XHTML 结构供结构化切片使用,
    // 且不像 BodyContentHandler 那样有 10 万字符截断上限,大文档不会静默丢内容
    ToXMLContentHandler handler = new ToXMLContentHandler();
    AutoDetectParser parser = new AutoDetectParser();
    Metadata metadata = new Metadata();
    try {
      parser.parse(new BufferedInputStream(input), handler, metadata);
    } catch (Exception ex) {
      throw new IllegalStateException("文档解析失败: " + ex.getMessage(), ex);
    }
    return StructuredTextParser.parse(handler.toString());
  }
}

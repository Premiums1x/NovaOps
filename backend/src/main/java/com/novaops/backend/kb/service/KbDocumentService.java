package com.novaops.backend.kb.service;

import com.novaops.backend.auth.service.AuthService;
import com.novaops.backend.common.api.PageResult;
import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.common.security.CurrentSession;
import com.novaops.backend.common.util.IdGenerator;
import com.novaops.backend.kb.dto.KbDocumentQuery;
import com.novaops.backend.kb.mapper.KbMapper;
import com.novaops.backend.kb.model.KbChunkRecord;
import com.novaops.backend.kb.model.KbDocumentRecord;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbDocumentService {

  private final KbMapper mapper;
  private final AuthService authService;
  private final KbFileStorage storage;
  private final KbIngestionService ingestion;
  private final QdrantVectorGateway vectorStore;

  public KbDocumentService(KbMapper mapper, AuthService authService, KbFileStorage storage, KbIngestionService ingestion, QdrantVectorGateway vectorStore) {
    this.mapper = mapper;
    this.authService = authService;
    this.storage = storage;
    this.ingestion = ingestion;
    this.vectorStore = vectorStore;
  }

  public KbDocumentRecord upload(CurrentSession session, String title, MultipartFile file) {
    authService.assertAdmin(session);
    String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
    String type = storage.extension(original);
    String id = IdGenerator.randomId("doc");
    Path path = storage.save(session.getTenantId(), id, file);

    KbDocumentRecord document = new KbDocumentRecord();
    document.setId(id);
    document.setTenantId(session.getTenantId());
    document.setTitle(title == null || title.isBlank() ? original : title.trim());
    document.setFileName(original);
    document.setFileType(type);
    document.setFileSize(file.getSize());
    document.setStoragePath(path.toString());
    document.setStatus("PARSING");
    document.setChunkCount(0);
    document.setCreatedBy(session.getUserId());

    mapper.insertDocument(document);
    ingestion.process(document);
    return mapper.findDocument(session.getTenantId(), id);
  }

  public PageResult<KbDocumentRecord> list(CurrentSession session, KbDocumentQuery query) {
    authService.assertAdmin(session);
    int offset = (query.getPage() - 1) * query.getPageSize();
    return new PageResult<>(
        mapper.listDocuments(session.getTenantId(), query.getKeyword(), query.getFileType(), query.getStatus(), offset, query.getPageSize()),
        query.getPage(),
        query.getPageSize(),
        mapper.countDocuments(session.getTenantId(), query.getKeyword(), query.getFileType(), query.getStatus()));
  }

  public KbDocumentRecord detail(CurrentSession session, String id) {
    authService.assertAdmin(session);
    return requireDocument(session.getTenantId(), id);
  }

  public List<KbChunkRecord> chunks(CurrentSession session, String id) {
    authService.assertAdmin(session);
    requireDocument(session.getTenantId(), id);
    return mapper.listChunks(session.getTenantId(), id);
  }

  public void updateTitle(CurrentSession session, String id, String title) {
    authService.assertAdmin(session);
    requireDocument(session.getTenantId(), id);
    mapper.updateTitle(session.getTenantId(), id, title.trim());
  }

  @Transactional
  public void delete(CurrentSession session, String id) {
    authService.assertAdmin(session);
    KbDocumentRecord document = requireDocument(session.getTenantId(), id);
    List<KbChunkRecord> chunks = mapper.listChunks(session.getTenantId(), id);
    // 向量删除在最前且不可回滚：失败直接中止，文档保留、可稍后重试
    try {
      if (!chunks.isEmpty()) {
        vectorStore.delete(chunks.stream().map(KbChunkRecord::getVectorId).toList());
      }
    } catch (Exception ex) {
      throw new BusinessException(503, "向量库不可用，文档未删除，可稍后重试");
    }
    mapper.deleteChunks(session.getTenantId(), id);
    mapper.softDeleteDocument(session.getTenantId(), id);
    storage.delete(Path.of(document.getStoragePath()));
  }

  public KbDocumentRecord replace(CurrentSession session, String id, String title, MultipartFile file) {
    authService.assertAdmin(session);
    requireDocument(session.getTenantId(), id);
    // 先上传新文档、再删旧文档：上传校验失败（类型/大小）时旧文档原样保留，避免替换失败丢数据
    KbDocumentRecord created = upload(session, title, file);
    delete(session, id);
    return created;
  }

  private KbDocumentRecord requireDocument(String tenantId, String id) {
    KbDocumentRecord document = mapper.findDocument(tenantId, id);
    if (document == null) {
      throw new BusinessException(404, "知识库文档不存在");
    }
    return document;
  }
}

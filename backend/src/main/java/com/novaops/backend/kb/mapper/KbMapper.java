package com.novaops.backend.kb.mapper;
import com.novaops.backend.kb.model.KbChunkRecord; import com.novaops.backend.kb.model.KbDocumentRecord; import java.util.List; import org.apache.ibatis.annotations.Param;
public interface KbMapper {
  void insertDocument(KbDocumentRecord document); KbDocumentRecord findDocument(@Param("id") String id);
  List<KbDocumentRecord> listDocuments(@Param("keyword") String keyword,@Param("fileType") String fileType,@Param("status") String status,@Param("offset") int offset,@Param("pageSize") int pageSize);
  long countDocuments(@Param("keyword") String keyword,@Param("fileType") String fileType,@Param("status") String status);
  void updateStatus(@Param("id") String id, @Param("status") String status, @Param("chunkCount") int chunkCount, @Param("errorMsg") String errorMsg);
  void updateTitle(@Param("id") String id, @Param("title") String title);
  void insertChunks(@Param("records") List<KbChunkRecord> records);
  List<KbChunkRecord> listChunks(@Param("documentId") String documentId); void deleteChunks(@Param("documentId") String documentId); void softDeleteDocument(@Param("id") String id);
}

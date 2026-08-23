package com.novaops.backend.kb.dto;
import java.util.List;
public record RetrievalResult(List<RetrievalChunk> chunks) { public boolean isEmpty(){return chunks==null||chunks.isEmpty();} }

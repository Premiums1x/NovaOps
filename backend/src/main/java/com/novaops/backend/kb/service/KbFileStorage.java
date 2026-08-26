package com.novaops.backend.kb.service;

import com.novaops.backend.common.exception.BusinessException;
import com.novaops.backend.kb.config.KbProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbFileStorage {
  private static final Set<String> ALLOWED = Set.of("md","pdf","doc","docx");
  private final KbProperties properties;
  public KbFileStorage(KbProperties properties){this.properties=properties;}
  public String extension(String fileName){
    int dot=fileName==null?-1:fileName.lastIndexOf('.');
    String extension=dot<0?"":fileName.substring(dot+1).toLowerCase(Locale.ROOT);
    if(!ALLOWED.contains(extension)) throw new BusinessException(400,"仅支持 md/pdf/doc/docx 文件");
    return extension;
  }
  public Path save(String documentId,MultipartFile file){
    if(file.isEmpty()) throw new BusinessException(400,"上传文件不能为空");
    if(file.getSize()>properties.getMaxFileSizeBytes()) throw new BusinessException(400,"文件超过允许的大小上限");
    String ext=extension(file.getOriginalFilename());
    try {
      Path root=Path.of(properties.getStoragePath()).toAbsolutePath().normalize();
      Path directory=root.resolve(documentId).normalize();
      if(!directory.startsWith(root)) throw new BusinessException(400,"非法存储路径");
      Files.createDirectories(directory);
      Path target=directory.resolve("source."+ext);
      try(var input=file.getInputStream()){Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);}
      return target;
    } catch(IOException ex){throw new BusinessException(500,"文件保存失败");}
  }
  public void delete(Path path){try{Files.deleteIfExists(path);}catch(IOException ignored){}}
}

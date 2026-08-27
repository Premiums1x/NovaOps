package com.novaops.backend.kb.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component @ConfigurationProperties(prefix = "app.kb")
public class KbProperties {
  private String storagePath="./data/kb"; private long maxFileSizeBytes=31_457_280; private int chunkTokens=600; private int defaultTopK=5; private double defaultMinScore=.55; private String qdrantBaseUrl="http://localhost:6333"; private String qdrantCollection="novaops_kb"; private String qdrantApiKey="";
  public String getStoragePath(){return storagePath;} public void setStoragePath(String v){storagePath=v;}
  public long getMaxFileSizeBytes(){return maxFileSizeBytes;} public void setMaxFileSizeBytes(long v){maxFileSizeBytes=v;}
  public int getChunkTokens(){return chunkTokens;} public void setChunkTokens(int v){chunkTokens=v;}
  public int getDefaultTopK(){return defaultTopK;} public void setDefaultTopK(int v){defaultTopK=v;}
  public double getDefaultMinScore(){return defaultMinScore;} public void setDefaultMinScore(double v){defaultMinScore=v;}
  public String getQdrantBaseUrl(){return qdrantBaseUrl;} public void setQdrantBaseUrl(String v){qdrantBaseUrl=v;}
  public String getQdrantCollection(){return qdrantCollection;} public void setQdrantCollection(String v){qdrantCollection=v;}
  public String getQdrantApiKey(){return qdrantApiKey;} public void setQdrantApiKey(String v){qdrantApiKey=v;}
}

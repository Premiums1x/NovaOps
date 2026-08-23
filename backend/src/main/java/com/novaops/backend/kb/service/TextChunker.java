package com.novaops.backend.kb.service;

import com.novaops.backend.kb.config.KbProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
  private final KbProperties properties;
  public TextChunker(KbProperties properties){this.properties=properties;}
  public List<String> split(String raw){
    String text=raw.replace('\u0000',' ').replaceAll("[ \\t]+"," ").replaceAll("\\n{3,}","\\n\\n").trim();
    if(text.isEmpty()) return List.of();
    int size=Math.max(400,properties.getChunkSize()), overlap=Math.min(Math.max(0,properties.getChunkOverlap()),size/2);
    List<String> chunks=new ArrayList<>(); int start=0;
    while(start<text.length()){
      int end=Math.min(text.length(),start+size);
      if(end<text.length()){
        int paragraph=text.lastIndexOf("\n\n",end); int sentence=Math.max(text.lastIndexOf('。',end),text.lastIndexOf('.',end));
        int boundary=Math.max(paragraph,sentence); if(boundary>start+size/2) end=boundary+1;
      }
      String chunk=text.substring(start,end).trim(); if(!chunk.isEmpty()) chunks.add(chunk);
      if(end>=text.length()) break; start=Math.max(start+1,end-overlap);
    }
    return chunks;
  }
}

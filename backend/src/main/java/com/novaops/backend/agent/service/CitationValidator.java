package com.novaops.backend.agent.service;

import com.novaops.backend.kb.dto.RetrievalChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CitationValidator {
  public record ValidationResult(boolean passed,String reason,Set<Integer> citationIndexes) {}
  private static final Pattern CITATION=Pattern.compile("\\[(\\d+)]");
  private final EmbeddingModel embeddingModel; private final double threshold;
  public CitationValidator(EmbeddingModel embeddingModel,@Value("${app.agent.validation-score:0.45}") double threshold){this.embeddingModel=embeddingModel;this.threshold=threshold;}
  public ValidationResult validate(String answer,List<RetrievalChunk> chunks){
    Set<Integer> indexes=new HashSet<>(); Matcher matcher=CITATION.matcher(answer); while(matcher.find())indexes.add(Integer.parseInt(matcher.group(1)));
    if(indexes.isEmpty())return new ValidationResult(false,"回答没有知识库引用",indexes);
    if(indexes.stream().anyMatch(index->index<1||index>chunks.size()))return new ValidationResult(false,"回答包含伪造引用编号",indexes);
    List<String> texts=new ArrayList<>(); List<Integer> pairs=new ArrayList<>();
    for(String sentence:answer.split("(?<=[。！？!?])")){Matcher cited=CITATION.matcher(sentence);while(cited.find()){int index=Integer.parseInt(cited.group(1));texts.add(sentence.replaceAll("\\[\\d+]","").trim());texts.add(chunks.get(index-1).content());pairs.add(index);}}
    if(texts.isEmpty())return new ValidationResult(false,"引用未绑定到具体论断",indexes);
    List<float[]> vectors=embeddingModel.embed(texts); for(int i=0;i<pairs.size();i++){if(cosine(vectors.get(i*2),vectors.get(i*2+1))<threshold)return new ValidationResult(false,"论断与引用依据相似度不足",indexes);}
    return new ValidationResult(true,"知识库引用校验通过",indexes);
  }
  private double cosine(float[] left,float[] right){double dot=0,a=0,b=0;for(int i=0;i<Math.min(left.length,right.length);i++){dot+=left[i]*right[i];a+=left[i]*left[i];b+=right[i]*right[i];}return a==0||b==0?0:dot/(Math.sqrt(a)*Math.sqrt(b));}
}

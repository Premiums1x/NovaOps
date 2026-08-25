package com.novaops.backend.agent.service;

import java.util.Locale;
import java.util.Map;

public class ChatIntentClassifier {
  public enum Intent { GENERAL_CHAT, RAG }

  private static final Map<String, String> RESPONSES = Map.ofEntries(
      Map.entry("你好", "你好，我是 NovaOps 智能助手。你可以和我简单交流，也可以询问当前租户知识库中的企业问题。"),
      Map.entry("您好", "您好，我是 NovaOps 智能助手。你可以和我简单交流，也可以询问当前租户知识库中的企业问题。"),
      Map.entry("hi", "你好，我是 NovaOps 智能助手。你可以和我简单交流，也可以询问当前租户知识库中的企业问题。"),
      Map.entry("hello", "你好，我是 NovaOps 智能助手。你可以和我简单交流，也可以询问当前租户知识库中的企业问题。"),
      Map.entry("在吗", "在的。你可以询问当前租户知识库中的企业问题。"),
      Map.entry("谢谢", "不客气，有需要可以继续问我。"),
      Map.entry("感谢", "不客气，有需要可以继续问我。"),
      Map.entry("再见", "再见，有需要随时找我。"),
      Map.entry("拜拜", "再见，有需要随时找我。"),
      Map.entry("你是谁", "我是 NovaOps 智能助手，可以进行简单交流，并基于当前租户的知识库回答企业相关问题。"),
      Map.entry("你能做什么", "我可以进行简单交流，并基于当前租户的知识库回答企业相关问题。")
  );

  public Intent classify(String content) {
    return RESPONSES.containsKey(normalize(content)) ? Intent.GENERAL_CHAT : Intent.RAG;
  }

  public String response(String content) {
    return RESPONSES.get(normalize(content));
  }

  private String normalize(String content) {
    if (content == null) return "";
    return content.trim().toLowerCase(Locale.ROOT)
        .replaceFirst("^[\\p{P}\\p{S}\\s]+", "")
        .replaceFirst("[\\p{P}\\p{S}\\s]+$", "");
  }
}

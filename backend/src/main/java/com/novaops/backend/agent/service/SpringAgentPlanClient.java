package com.novaops.backend.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class SpringAgentPlanClient implements AgentPlanClient {

  private static final String PLAN_SYSTEM = """
      你是 NovaOps 知识库问答的行动规划器。只输出 JSON，不要输出 Markdown、解释或思考过程。
      固定输出三个步骤，action 依次为 search_kb、answer、validate。
      search_kb 必须提供 query，所有步骤提供简短 reason；reason 只描述可公开的行动目的。
      格式：{"steps":[{"action":"search_kb","query":"检索词","reason":"行动目的"},{"action":"answer","reason":"行动目的"},{"action":"validate","reason":"行动目的"}]}
      """;

  private final ChatClient chatClient;

  public SpringAgentPlanClient(ChatClient.Builder builder) {
    this.chatClient = builder.build();
  }

  @Override
  public String generate(String question) {
    return chatClient.prompt().system(PLAN_SYSTEM).user(question).call().content();
  }
}

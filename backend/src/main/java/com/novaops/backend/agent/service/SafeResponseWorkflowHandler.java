package com.novaops.backend.agent.service;

import com.novaops.backend.agent.model.QueryRoute;
import com.novaops.backend.agent.model.RouteDecision;
import com.novaops.backend.agent.model.ValidationStatus;
import com.novaops.backend.agent.model.WorkflowResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SafeResponseWorkflowHandler {
  public WorkflowResult execute(RouteDecision route) {
    String answer = route.route() == QueryRoute.REJECT
        ? "这个请求超出了智能助手的安全能力边界，无法执行。"
        : "我还不能确定你要查询的对象或目标，请补充具体的知识库、文档或问题范围。";
    return new WorkflowResult(route.route(), route.reason(), answer, List.of(), List.of(), false, 0, 0,
        ValidationStatus.NOT_APPLICABLE, route.reasonCode());
  }
}

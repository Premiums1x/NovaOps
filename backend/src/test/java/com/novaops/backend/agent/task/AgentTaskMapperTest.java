package com.novaops.backend.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import com.novaops.backend.agent.task.mapper.AgentAuditMapper;
import com.novaops.backend.agent.task.mapper.AgentTaskMapper;
import com.novaops.backend.agent.task.model.AgentTaskRecord;
import com.novaops.backend.agent.task.model.AgentTaskStepRecord;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 用 H2 内存库直连 AgentTaskMapper.xml 验证 SQL 语义（不依赖 MySQL）。
 * 重点：finishTask 的终态幂等守卫——先写先得，后写不覆盖。
 */
class AgentTaskMapperTest {

  private static SqlSessionFactory factory;

  @BeforeAll
  static void init() throws Exception {
    //mapper resource 加载时按 namespace 自动绑定 AgentTaskMapper 接口
    String mybatisConfig = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <!DOCTYPE configuration PUBLIC "-//mybatis.org//DTD Config 3.0//EN" "http://mybatis.org/dtd/mybatis-3-config.dtd">
        <configuration>
          <settings>
            <!-- 与 application.yml 的 map-underscore-to-camel-case 保持一致 -->
            <setting name="mapUnderscoreToCamelCase" value="true" />
          </settings>
          <environments default="test">
            <environment id="test">
              <transactionManager type="JDBC" />
              <dataSource type="POOLED">
                <property name="driver" value="org.h2.Driver" />
                <property name="url" value="jdbc:h2:mem:agenttask;MODE=MySQL;DB_CLOSE_DELAY=-1" />
                <property name="username" value="sa" />
                <property name="password" value="" />
              </dataSource>
            </environment>
          </environments>
          <mappers>
            <mapper resource="mapper/AgentTaskMapper.xml" />
            <mapper resource="mapper/AgentAuditMapper.xml" />
          </mappers>
        </configuration>
        """;
    factory = new SqlSessionFactoryBuilder().build(
        new ByteArrayInputStream(mybatisConfig.getBytes(StandardCharsets.UTF_8)));

    try (Connection connection = factory.getConfiguration().getEnvironment().getDataSource().getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("""
          create table agent_task (
            id varchar(64) primary key,
            user_id varchar(64) not null,
            goal varchar(2000),
            status varchar(20) not null,
            plan_json text,
            result_text text,
            error_text varchar(1000),
            created_at timestamp default current_timestamp,
            updated_at timestamp default current_timestamp
          )""");
      statement.execute("""
          create table agent_task_step (
            id varchar(64) primary key,
            task_id varchar(64) not null,
            seq int,
            kind varchar(20),
            tool_name varchar(100),
            args_json text,
            observation_json text,
            status varchar(20),
            revision int not null default 0,
            created_at timestamp default current_timestamp
          )""");
      statement.execute("""
          create table agent_audit_log (
            id varchar(64) primary key,
            task_id varchar(64),
            user_id varchar(64) not null,
            source varchar(20) default 'task',
            tool_name varchar(100),
            args_digest varchar(2000),
            result_digest varchar(2000),
            write_operation tinyint,
            confirmed tinyint,
            allowed tinyint,
            detail varchar(500),
            created_at timestamp default current_timestamp
          )""");
    }
  }

  @Test
  void finishTaskIsTerminalIdempotent() {
    try (SqlSession session = factory.openSession(true)) {
      AgentTaskMapper mapper = session.getMapper(AgentTaskMapper.class);
      AgentTaskRecord record = new AgentTaskRecord();
      record.setId("task-1");
      record.setUserId("user-1");
      record.setGoal("重启核心交换机");
      record.setStatus("RUNNING");
      mapper.insertTask(record);

      mapper.finishTask("task-1", "CANCELLED", null, "用户取消任务");
      mapper.finishTask("task-1", "FAILED", null, "引擎执行失败");

      AgentTaskRecord after = mapper.findTask("task-1", "user-1");
      assertEquals("CANCELLED", after.getStatus());
      assertEquals("用户取消任务", after.getErrorText());
      assertThat(after.getResultText()).isNull();
    }
  }

  @Test
  void finishTaskAllowsRunningToAnyTerminal() {
    try (SqlSession session = factory.openSession(true)) {
      AgentTaskMapper mapper = session.getMapper(AgentTaskMapper.class);
      AgentTaskRecord record = new AgentTaskRecord();
      record.setId("task-2");
      record.setUserId("user-1");
      record.setGoal("巡检");
      record.setStatus("RUNNING");
      mapper.insertTask(record);

      mapper.finishTask("task-2", "DONE", "巡检完成", null);
      AgentTaskRecord after = mapper.findTask("task-2", "user-1");
      assertEquals("DONE", after.getStatus());
      assertEquals("巡检完成", after.getResultText());
    }
  }

  @Test
  void findStaleTaskIdsReturnsOnlyNonTerminalOlderThanCutoff() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      AgentTaskMapper mapper = session.getMapper(AgentTaskMapper.class);
      insertTask(mapper, "stale-running", "RUNNING");
      insertTask(mapper, "stale-awaiting", "AWAITING_CONFIRM");
      insertTask(mapper, "stale-done", "DONE");
      insertTask(mapper, "fresh-running", "RUNNING");

      // 把前三个任务的 updated_at 拨回 2 小时前
      try (var connection = factory.getConfiguration().getEnvironment().getDataSource().getConnection();
          var statement = connection.createStatement()) {
        statement.execute("update agent_task set updated_at = dateadd(hour, -2, current_timestamp) "
            + "where id in ('stale-running','stale-awaiting','stale-done')");
      }

      var staleIds = mapper.findStaleTaskIds(java.time.LocalDateTime.now().minusMinutes(30));

      org.assertj.core.api.Assertions.assertThat(staleIds)
          .containsExactlyInAnyOrder("stale-running", "stale-awaiting");
    }
  }

  private static void insertTask(AgentTaskMapper mapper, String id, String status) {
    AgentTaskRecord record = new AgentTaskRecord();
    record.setId(id);
    record.setUserId("user-1");
    record.setGoal("清扫测试");
    record.setStatus(status);
    mapper.insertTask(record);
  }

  @Test
  void statsAggregationMatchesManualCounts() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      AgentTaskMapper taskMapper = session.getMapper(AgentTaskMapper.class);
      AgentAuditMapper auditMapper = session.getMapper(AgentAuditMapper.class);

      insertTask(taskMapper, "stat-done-1", "DONE");
      insertTask(taskMapper, "stat-done-2", "DONE");
      insertTask(taskMapper, "stat-failed", "FAILED");
      insertTask(taskMapper, "stat-other", "RUNNING");
      // user-2 的任务不计入 user-1 的统计
      AgentTaskRecord other = new AgentTaskRecord();
      other.setId("stat-other-user");
      other.setUserId("user-2");
      other.setGoal("别人的任务");
      other.setStatus("DONE");
      taskMapper.insertTask(other);

      // stat-done-1 两个工具步、stat-done-2 一个工具步 → 平均 1.5
      insertStep(taskMapper, "s1", "stat-done-1", "tool");
      insertStep(taskMapper, "s2", "stat-done-1", "tool");
      insertStep(taskMapper, "s3", "stat-done-2", "tool");
      insertStep(taskMapper, "s4", "stat-done-2", "summary");
      insertStep(taskMapper, "s5", "stat-other", "tool");

      // 三条写审计，两条已确认
      insertAudit(auditMapper, "a1", "stat-done-1", true, true);
      insertAudit(auditMapper, "a2", "stat-done-2", true, true);
      insertAudit(auditMapper, "a3", "stat-done-2", true, false);
      insertAudit(auditMapper, "a4", "stat-done-2", false, null);

      var byStatus = taskMapper.countTasksByStatus("user-1");
      java.util.Map<String, Long> counts = new java.util.HashMap<>();
      byStatus.forEach(row -> counts.put(
          String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue()));
      org.assertj.core.api.Assertions.assertThat(counts)
          .containsEntry("DONE", 2L)
          .containsEntry("FAILED", 1L)
          .containsEntry("RUNNING", 1L)
          .hasSize(3);

      // stat-done-1 两个工具步、stat-done-2 一个、stat-other 一个 → 4 步 / 3 任务 ≈ 1.333
      Double avgSteps = taskMapper.avgToolStepsPerTask("user-1");
      assertEquals(1.333, avgSteps, 0.001);

      java.util.Map<String, Object> writeStats = auditMapper.writeAuditStats("user-1");
      assertEquals(3L, ((Number) writeStats.get("writeOperations")).longValue());
      assertEquals(2L, ((Number) writeStats.get("confirmedOperations")).longValue());
    }
  }

  @Test
  void listAuditsByTaskReturnsOnlyThatTask() {
    try (SqlSession session = factory.openSession(true)) {
      AgentTaskMapper taskMapper = session.getMapper(AgentTaskMapper.class);
      AgentAuditMapper auditMapper = session.getMapper(AgentAuditMapper.class);
      insertTask(taskMapper, "aud-task-1", "DONE");
      insertTask(taskMapper, "aud-task-2", "DONE");

      insertAudit(auditMapper, "b1", "aud-task-1", true, true);
      insertAudit(auditMapper, "b2", "aud-task-2", true, false);
      insertAudit(auditMapper, "b3", "aud-task-1", false, null);

      var audits = auditMapper.listAuditsByTask("aud-task-1");
      org.assertj.core.api.Assertions.assertThat(audits)
          .extracting(com.novaops.backend.agent.task.model.AgentAuditRecord::getId)
          .containsExactlyInAnyOrder("b1", "b3");
    }
  }

  private static void insertStep(
      AgentTaskMapper mapper, String id, String taskId, String kind) {
    AgentTaskStepRecord record = new AgentTaskStepRecord();
    record.setId(id);
    record.setTaskId(taskId);
    record.setSeq(1);
    record.setKind(kind);
    record.setStatus("DONE");
    record.setRevision(0);
    mapper.insertStep(record);
  }

  private static void insertAudit(
      AgentAuditMapper mapper, String id, String taskId, boolean write, Boolean confirmed) {
    com.novaops.backend.agent.task.model.AgentAuditRecord record =
        new com.novaops.backend.agent.task.model.AgentAuditRecord();
    record.setId(id);
    record.setTaskId(taskId);
    record.setUserId("user-1");
    record.setSource("task");
    record.setToolName("ticket.assign");
    record.setWriteOperation(write);
    record.setConfirmed(confirmed);
    record.setAllowed(true);
    mapper.insertAudit(record);
  }
}

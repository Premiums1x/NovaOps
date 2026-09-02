package com.novaops.backend.agent.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import com.novaops.backend.agent.task.mapper.AgentTaskMapper;
import com.novaops.backend.agent.task.model.AgentTaskRecord;
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
}

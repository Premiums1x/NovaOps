-- 任务型 Agent 模块增量脚本（在既有库上执行；全新安装直接使用 novaops_init.sql）
-- 任务：用户下达的运维目标及其执行状态
drop table if exists agent_audit_log;
drop table if exists agent_task_step;
drop table if exists agent_task;

create table agent_task (
  id varchar(64) primary key,
  user_id varchar(64) not null,
  goal varchar(2000) not null,
  status varchar(20) not null,
  plan_json text null,
  result_text text null,
  error_text varchar(1000) null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  index idx_agent_task_user (user_id, created_at desc)
) comment '任务型 Agent 任务';

create table agent_task_step (
  id varchar(64) primary key,
  task_id varchar(64) not null,
  seq int not null,
  kind varchar(20) not null,
  tool_name varchar(100) null,
  args_json text null,
  observation_json text null,
  status varchar(20) not null,
  revision int not null default 0,
  created_at datetime not null default current_timestamp,
  index idx_agent_task_step_task (task_id, seq)
) comment '任务型 Agent 步骤流水';

create table agent_audit_log (
  id varchar(64) primary key,
  task_id varchar(64) null,
  user_id varchar(64) not null,
  source varchar(20) not null default 'task',
  tool_name varchar(100) not null,
  args_digest varchar(2000) null,
  result_digest varchar(2000) null,
  write_operation tinyint not null default 0,
  confirmed tinyint null,
  allowed tinyint not null default 1,
  detail varchar(500) null,
  created_at datetime not null default current_timestamp,
  index idx_agent_audit_user (user_id, created_at desc),
  index idx_agent_audit_tool (tool_name, created_at desc)
) comment 'Agent 工具调用审计（含 MCP 来源）';

-- 权限：运行智能体任务（admin / staff）
insert into sys_permission (id, code, name) values ('perm-agent-task', 'agent:task', '运行智能体任务');
insert into sys_role_permission (role_id, permission_id) values
  ('role-admin', 'perm-agent-task'),
  ('role-staff', 'perm-agent-task');

-- 菜单：智能体工作台（full / staff 两个 scope）
insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-agent-console', '智能体工作台', 'AgentConsole', '/agent/console', 'AgentConsoleView', 'robot', 'agent:task', 1, null, 45, 'full'),
  ('staff-agent-console', '智能体工作台', 'AgentConsole', '/agent/console', 'AgentConsoleView', 'robot', 'agent:task', 1, null, 45, 'staff');

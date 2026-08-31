-- 回滚智能问答执行审计字段；执行前请先备份 agent_message
set @execution_json_exists = (
  select count(1)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'agent_message'
    and column_name = 'execution_json'
);

set @drop_execution_json_sql = if(
  @execution_json_exists = 1,
  'alter table agent_message drop column execution_json',
  'select ''agent_message.execution_json does not exist'' as migration_status'
);

prepare drop_execution_json_stmt from @drop_execution_json_sql;
execute drop_execution_json_stmt;
deallocate prepare drop_execution_json_stmt;

-- 智能问答路由与检索执行审计增量脚本（已有数据库执行）
set @execution_json_exists = (
  select count(1)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'agent_message'
    and column_name = 'execution_json'
);

set @add_execution_json_sql = if(
  @execution_json_exists = 0,
  'alter table agent_message add column execution_json mediumtext null after validation_passed',
  'select ''agent_message.execution_json already exists'' as migration_status'
);

prepare add_execution_json_stmt from @add_execution_json_sql;
execute add_execution_json_stmt;
deallocate prepare add_execution_json_stmt;

-- 任务中心菜单（优化计划 T7）：组件 AgentTasksView，权限沿用 agent:task。
-- 已初始化的库执行本文件；全新安装使用 novaops_init.sql（已包含同样数据）。
insert into sys_menu (id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope) values
  ('full-agent-tasks', '任务中心', 'AgentTasks', '/agent/tasks', 'AgentTasksView', 'robot', 'agent:task', 1, null, 46, 'full'),
  ('staff-agent-tasks', '任务中心', 'AgentTasks', '/agent/tasks', 'AgentTasksView', 'robot', 'agent:task', 1, null, 46, 'staff');

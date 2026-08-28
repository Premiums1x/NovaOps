-- =====================================================================
-- refactor_ticket_person_fk.sql 的回滚脚本：恢复姓名字符串列。
-- 负责人/操作人/评论人恢复 display_name，创建人恢复 username；未知用户使用兜底值。
-- 用户账号可能已被其他业务引用，结构回滚不会删除 sys_user 数据。
-- =====================================================================
SET NAMES utf8mb4;

-- 1. biz_ticket：恢复 assignee/creator 字符串
alter table biz_ticket
  drop foreign key fk_ticket_assignee,
  drop foreign key fk_ticket_creator;

alter table biz_ticket
  add column assignee varchar(100) null after priority,
  add column creator varchar(100) null after assignee;

update biz_ticket set
  assignee = coalesce((select display_name from sys_user where id = biz_ticket.assignee_id), 'Unassigned'),
  creator = coalesce((select username from sys_user where id = biz_ticket.creator_id), 'unknown');

alter table biz_ticket
  drop column assignee_id,
  drop column creator_id;

-- 2. biz_ticket_timeline：恢复 operator
alter table biz_ticket_timeline
  drop foreign key fk_ticket_timeline_operator;

alter table biz_ticket_timeline
  add column operator varchar(100) null after action;

update biz_ticket_timeline set
  operator = coalesce((select display_name from sys_user where id = biz_ticket_timeline.operator_id), 'unknown');

alter table biz_ticket_timeline
  drop column operator_id;

-- 3. biz_ticket_comment：恢复 author
alter table biz_ticket_comment
  drop foreign key fk_ticket_comment_author;

alter table biz_ticket_comment
  add column author varchar(100) null after ticket_id;

update biz_ticket_comment set
  author = coalesce((select display_name from sys_user where id = biz_ticket_comment.author_id), 'unknown');

alter table biz_ticket_comment
  drop column author_id;

-- 4. 撤销 staff 转派权限；保留用户账号，避免删除被其他业务引用的数据
delete from sys_role_permission where role_id = 'role-staff' and permission_id = 'perm-ticket-transfer';

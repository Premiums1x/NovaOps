-- =====================================================================
-- 工单人员字段外键化迁移（针对已用旧版 novaops_init.sql 初始化过的库）
--   biz_ticket.assignee/creator            → assignee_id/creator_id
--   biz_ticket_timeline.operator           → operator_id
--   biz_ticket_comment.author              → author_id
-- 均指向 sys_user.id；旧的姓名字符串按映射表回填后删除。
-- 幂等说明：重复执行需先回滚（rollback_ticket_person_fk.sql），本脚本
-- 只针对「旧结构」执行一次，不包含 IF NOT EXISTS 之类的重复执行保护。
-- =====================================================================
SET NAMES utf8mb4;

-- 1. 补充种子员工账号（与 novaops_init.sql 种子密码一致，初始密码 123456）
insert ignore into sys_user (id, username, email, password_hash, display_name, role_id, enabled, must_change_password, deleted)
values
  ('u-tom', 'tom', 'tom@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Tom', 'role-staff', 1, 0, 0),
  ('u-jerry', 'jerry', 'jerry@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Jerry', 'role-staff', 1, 0, 0),
  ('u-alice', 'alice', 'alice@novaops.local', '$2a$10$t4amKqsqabkgwLhaZpj0F.wDk7mpyJgZokQRAdTfrxaIwPilCrHoq', 'Alice', 'role-staff', 1, 0, 0);

-- 2. 变更表结构前校验所有旧人员字符串均可唯一映射。
-- MySQL 8 会用 CHECK 约束阻止 unmatched_count > 0；失败时原表结构完全保留。
create temporary table ticket_person_fk_validation (
  unmatched_count int not null,
  constraint chk_ticket_person_fk_validation check (unmatched_count = 0)
);

insert into ticket_person_fk_validation (unmatched_count)
select count(*)
from (
  select concat('ticket.creator:', t.id) as source
  from biz_ticket t
  where not exists (
    select 1 from sys_user u
    where u.deleted = 0 and u.username = t.creator
  )
  union all
  select concat('ticket.assignee:', t.id)
  from biz_ticket t
  where nullif(trim(t.assignee), '') is not null
    and t.assignee <> 'Unassigned'
    and not exists (
      select 1 from sys_user u
      where u.deleted = 0 and u.username = t.assignee
    )
    and (
      select count(*) from sys_user u
      where u.deleted = 0 and u.display_name = t.assignee
    ) <> 1
  union all
  select concat('timeline.operator:', tl.id)
  from biz_ticket_timeline tl
  where nullif(trim(tl.operator), '') is not null
    and not exists (
      select 1 from sys_user u
      where u.deleted = 0 and u.username = tl.operator
    )
    and (
      select count(*) from sys_user u
      where u.deleted = 0 and u.display_name = tl.operator
    ) <> 1
  union all
  select concat('comment.author:', cm.id)
  from biz_ticket_comment cm
  where nullif(trim(cm.author), '') is not null
    and not exists (
      select 1 from sys_user u
      where u.deleted = 0 and u.username = cm.author
    )
    and (
      select count(*) from sys_user u
      where u.deleted = 0 and u.display_name = cm.author
    ) <> 1
) unmatched;

drop temporary table ticket_person_fk_validation;

-- 3. biz_ticket：加外键列并按旧字符串回填。
-- 旧 creator 存 username；assignee 兼容 username 和唯一 display_name。
alter table biz_ticket
  add column assignee_id varchar(64) null after priority,
  add column creator_id varchar(64) null after assignee_id;

update biz_ticket t
left join sys_user creator_user
  on creator_user.username = t.creator and creator_user.deleted = 0
left join sys_user assignee_username
  on assignee_username.username = t.assignee and assignee_username.deleted = 0
left join (
  select display_name, min(id) as id
  from sys_user
  where deleted = 0
  group by display_name
  having count(*) = 1
) assignee_display on assignee_display.display_name = t.assignee
set t.assignee_id = case
      when nullif(trim(t.assignee), '') is null or t.assignee = 'Unassigned' then null
      else coalesce(assignee_username.id, assignee_display.id)
    end,
    t.creator_id = creator_user.id;

-- 4. biz_ticket_timeline：operator → operator_id
alter table biz_ticket_timeline
  add column operator_id varchar(64) null after action;

update biz_ticket_timeline tl
left join sys_user operator_username
  on operator_username.username = tl.operator and operator_username.deleted = 0
left join (
  select display_name, min(id) as id
  from sys_user
  where deleted = 0
  group by display_name
  having count(*) = 1
) operator_display on operator_display.display_name = tl.operator
set tl.operator_id = coalesce(operator_username.id, operator_display.id);

-- 5. biz_ticket_comment：author → author_id
alter table biz_ticket_comment
  add column author_id varchar(64) null after ticket_id;

update biz_ticket_comment cm
left join sys_user author_username
  on author_username.username = cm.author and author_username.deleted = 0
left join (
  select display_name, min(id) as id
  from sys_user
  where deleted = 0
  group by display_name
  having count(*) = 1
) author_display on author_display.display_name = cm.author
set cm.author_id = coalesce(author_username.id, author_display.id);

-- 6. 回填完成后删除旧列、收紧创建人非空约束并建立外键。
alter table biz_ticket
  modify column creator_id varchar(64) not null,
  drop column assignee,
  drop column creator,
  add index idx_ticket_assignee (assignee_id),
  add index idx_ticket_creator (creator_id),
  add constraint fk_ticket_assignee foreign key (assignee_id) references sys_user (id),
  add constraint fk_ticket_creator foreign key (creator_id) references sys_user (id);

alter table biz_ticket_timeline
  drop column operator,
  add constraint fk_ticket_timeline_operator foreign key (operator_id) references sys_user (id);

alter table biz_ticket_comment
  drop column author,
  add constraint fk_ticket_comment_author foreign key (author_id) references sys_user (id);

-- 7. 清理旧种子流水错误：A-TICKET-0001 状态为 pending，却存在 pending→processing 的
--    advance 流水（与新状态机矛盾），直接删除该条流水
delete from biz_ticket_timeline
where action = 'advance' and from_status = 'pending' and to_status = 'processing';

-- 8. staff 角色补转派权限（与设计文档 3.4.3 对齐）
insert ignore into sys_role_permission (role_id, permission_id) values ('role-staff', 'perm-ticket-transfer');

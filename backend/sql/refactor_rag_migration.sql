-- 从旧版 NovaOps 升级到单身份 + RAG 架构。仅执行一次；执行前请备份数据库。
alter table sys_role add column description varchar(255) not null default '' after name;
update sys_role set description=case code when 'admin' then '管理用户、身份、知识库以及全部业务数据' when 'staff' then '处理工单、资产与使用智能问答' else '只读访问授权看板与智能问答' end;
alter table sys_user add column role_id varchar(64) null after display_name, add column enabled tinyint not null default 1 after role_id;
update sys_user u inner join (select user_id,min(role_id) role_id from sys_user_role group by user_id) ur on ur.user_id=u.id set u.role_id=ur.role_id;
alter table sys_user modify role_id varchar(64) not null;

insert into sys_permission(id,code,name) values ('perm-auth-user-manage','auth:user:manage','管理用户与身份'),('perm-agent-chat','agent:chat','使用智能问答');
insert into sys_role_permission(role_id,permission_id,tenant_id) select 'role-admin','perm-auth-user-manage',id from sys_tenant;
insert into sys_role_permission(role_id,permission_id,tenant_id) select r.id,'perm-agent-chat',t.id from sys_role r cross join sys_tenant t;
insert into sys_menu(id,title,name,path,component,icon,permission_code,keep_alive,parent_id,sort_order,menu_scope) values ('full-user','用户与身份','UserManagement','/system/users','UserManagementView','user','auth:user:manage',1,null,50,'full');

create table kb_document (id varchar(64) primary key,tenant_id varchar(64) not null,title varchar(255) not null,file_name varchar(255) not null,file_type varchar(16) not null,file_size bigint not null,storage_path varchar(500) not null,status varchar(32) not null,chunk_count int not null default 0,error_msg varchar(1000) null,created_by varchar(64) not null,created_at datetime not null default current_timestamp,updated_at datetime not null default current_timestamp,deleted tinyint not null default 0,index idx_kb_document_tenant_status(tenant_id,status),index idx_kb_document_tenant_updated(tenant_id,updated_at desc));
create table kb_chunk (id varchar(64) primary key,document_id varchar(64) not null,tenant_id varchar(64) not null,chunk_index int not null,content mediumtext not null,vector_id varchar(64) not null,index idx_kb_chunk_document(document_id,chunk_index),index idx_kb_chunk_tenant(tenant_id));
create table agent_conversation (id varchar(64) primary key,tenant_id varchar(64) not null,user_id varchar(64) not null,title varchar(255) not null,created_at datetime not null default current_timestamp,updated_at datetime not null default current_timestamp,index idx_agent_conversation_owner(tenant_id,user_id,updated_at desc));
create table agent_message (id varchar(64) primary key,conversation_id varchar(64) not null,role varchar(16) not null,content mediumtext not null,citations_json text null,validation_passed tinyint null,created_at datetime not null default current_timestamp,index idx_agent_message_conversation(conversation_id,created_at));

-- 验证所有用户均已迁移到单身份后再执行：drop table sys_user_role;

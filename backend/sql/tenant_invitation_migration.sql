-- Add custom-tenant administration and invitation-only registration.
-- Non-destructive one-time migration for an existing NovaOps database.
-- Back up the database before running this script.

set @platform_admin_column_exists = (
  select count(1)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sys_user'
    and column_name = 'platform_admin'
);
set @platform_admin_ddl = if(
  @platform_admin_column_exists = 0,
  'alter table sys_user add column platform_admin tinyint not null default 0 after enabled',
  'select 1'
);
prepare platform_admin_migration from @platform_admin_ddl;
execute platform_admin_migration;
deallocate prepare platform_admin_migration;

-- Seed only the original built-in administrator as a platform administrator.
update sys_user set platform_admin = 1 where id = 'u-admin' and username = 'admin';
-- Repair the built-in administrator if an older user-management screen changed its global role.
update sys_user set role_id = 'role-admin' where id = 'u-admin' and username = 'admin';

create table if not exists sys_invitation (
  id varchar(64) primary key,
  token_hash char(64) not null unique,
  tenant_id varchar(64) not null,
  role_id varchar(64) not null,
  created_by varchar(64) not null,
  expires_at datetime not null,
  used_at datetime null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp,
  index idx_invitation_tenant_created (tenant_id, created_at desc),
  index idx_invitation_expiry_used (expires_at, used_at)
);

insert ignore into sys_menu (
  id, title, name, path, component, icon, permission_code, keep_alive, parent_id, sort_order, menu_scope
) values (
  'platform-tenant-invitations', '租户与邀请', 'TenantInvitationManagement', '/system/tenants',
  'TenantInvitationManagementView', 'user', null, 1, null, 60, 'platform'
);

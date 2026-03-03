<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { MenuProps, TabsProps } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import {
  AppstoreOutlined,
  BookOutlined,
  DashboardOutlined,
  DesktopOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'
import { useAppStore } from '@/store/app'
import { useAuthStore } from '@/store/auth'
import { usePermissionStore } from '@/store/permission'
import type { MenuItemDto } from '@/types/menu'

type MenuItem = Required<MenuProps>['items'][number]

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const authStore = useAuthStore()
const permissionStore = usePermissionStore()
const openKeys = ref<string[]>([])

const iconMap = {
  dashboard: DashboardOutlined,
  ticket: ToolOutlined,
  asset: DesktopOutlined,
  kb: BookOutlined,
  default: AppstoreOutlined,
}

const renderMenuItems = (menus: MenuItemDto[]): MenuItem[] => {
  return menus.map((menu) => {
    const Icon = iconMap[menu.icon as keyof typeof iconMap] || iconMap.default
    return {
      key: menu.path,
      icon: () => h(Icon),
      label: menu.title,
      children: menu.children?.length ? renderMenuItems(menu.children) : undefined,
    }
  })
}

const getParentPaths = (
  targetPath: string,
  menus: MenuItemDto[],
  chain: string[] = []
): string[] => {
  for (const menu of menus) {
    const nextChain = [...chain, menu.path]
    if (menu.path === targetPath) {
      return chain
    }
    if (menu.children?.length) {
      const result = getParentPaths(targetPath, menu.children, nextChain)
      if (result.length) {
        return result
      }
    }
  }
  return []
}

const menuItems = computed(() => renderMenuItems(permissionStore.menus))
const selectedMenuPath = computed(() => {
  if (route.path.startsWith('/ticket/detail')) {
    return '/ticket/list'
  }
  if (route.path.startsWith('/asset/detail')) {
    return '/asset/list'
  }
  if (route.path.startsWith('/kb/edit')) {
    return '/kb/list'
  }
  return route.path
})
const selectedKeys = computed(() => [selectedMenuPath.value])
const tabs = computed(() => appStore.tabs)
const activeTabPath = computed(() => appStore.activeTabPath)
const breadcrumbItems = computed(() => {
  return route.matched
    .filter((item) => item.meta.title && item.name !== 'Root')
    .map((item) => ({
      key: String(item.path),
      title: String(item.meta.title),
    }))
})
const tenantOptions = computed(() =>
  (authStore.user?.tenants || []).map((item) => ({ label: item.name, value: item.id }))
)

watch(
  () => route.fullPath,
  () => {
    appStore.addTabByRoute(route)
    openKeys.value = getParentPaths(selectedMenuPath.value, permissionStore.menus)
  },
  { immediate: true }
)

const toggleCollapse = () => {
  appStore.setCollapsed(!appStore.collapsed)
}

const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
  void router.push(String(key))
}

const handleOpenChange: MenuProps['onOpenChange'] = (keys) => {
  openKeys.value = keys as string[]
}

const handleTabChange: TabsProps['onChange'] = (key) => {
  void router.push(String(key))
}

const handleTabEdit: TabsProps['onEdit'] = (targetKey, action) => {
  if (action === 'remove') {
    const path = String(targetKey)
    appStore.removeTabByPath(path)
    if (route.path === path) {
      void router.push(appStore.activeTabPath)
    }
  }
}

const handleTenantChange = async (tenantId: string) => {
  if (tenantId === authStore.tenantId) {
    return
  }
  await authStore.switchTenant(tenantId)
  await permissionStore.generateRoutes(router)
  appStore.resetTabs()
  message.success('租户切换成功')
  await router.replace('/dashboard')
}

const handleUserAction = async ({ key }: { key: string }) => {
  if (key !== 'logout') {
    return
  }
  authStore.logout()
  permissionStore.resetDynamicRoutes(router)
  appStore.resetTabs()
  await router.replace('/login')
}
</script>

<template>
  <a-layout class="layout-shell">
    <a-layout-sider
      :collapsed="appStore.collapsed"
      collapsible
      :trigger="null"
      width="220"
      class="layout-sider"
    >
      <div class="logo">{{ appStore.collapsed ? 'NO' : 'NovaOps' }}</div>
      <a-menu
        theme="dark"
        mode="inline"
        :selected-keys="selectedKeys"
        :open-keys="openKeys"
        :items="menuItems"
        @open-change="handleOpenChange"
        @click="handleMenuClick"
      />
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="layout-header">
        <div class="header-left">
          <a-button type="text" class="fold-btn" @click="toggleCollapse">
            <MenuUnfoldOutlined v-if="appStore.collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <a-breadcrumb :items="breadcrumbItems" />
        </div>
        <div class="header-right">
          <a-space>
            <a-select
              :value="authStore.tenantId"
              style="width: 160px"
              :options="tenantOptions"
              @change="handleTenantChange"
            />
            <a-dropdown>
              <a class="user-link" @click.prevent>
                {{ authStore.user?.displayName || authStore.user?.username }}
              </a>
              <template #overlay>
                <a-menu @click="handleUserAction">
                  <a-menu-item key="logout">退出登录</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </a-space>
        </div>
      </a-layout-header>
      <a-layout-content class="layout-content">
        <div class="tabs-wrap">
          <a-tabs
            type="editable-card"
            hide-add
            size="small"
            :active-key="activeTabPath"
            @change="handleTabChange"
            @edit="handleTabEdit"
          >
            <a-tab-pane
              v-for="tab in tabs"
              :key="tab.path"
              :tab="tab.title"
              :closable="tab.closable"
            />
          </a-tabs>
        </div>
        <section class="page-wrap">
          <RouterView v-slot="{ Component }">
            <keep-alive :include="appStore.cachedTabNames">
              <component :is="Component" :key="route.name || route.path" />
            </keep-alive>
          </RouterView>
        </section>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.layout-shell {
  min-height: 100vh;
}

.layout-sider {
  overflow: auto;
}

.logo {
  height: 56px;
  margin: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: grid;
  place-items: center;
}

.layout-header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #edf1f5;
  padding: 0 16px 0 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fold-btn {
  width: 40px;
}

.user-link {
  color: #1f2937;
}

.layout-content {
  padding: 12px;
}

.tabs-wrap {
  background: #fff;
  border-radius: 8px;
  padding: 8px 12px 0;
}

.page-wrap {
  margin-top: 12px;
  min-height: calc(100vh - 138px);
}
</style>

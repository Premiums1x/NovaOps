import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark' | 'system'

interface ThemeState {
  mode: ThemeMode
  primaryColor: string
  systemDark: boolean
}

let mediaQuery: MediaQueryList | undefined
let mediaListener: ((event: MediaQueryListEvent) => void) | undefined

const setDocumentTheme = (mode: 'light' | 'dark', primaryColor: string) => {
  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode
  document.documentElement.style.setProperty('--nova-primary', primaryColor)
}

export const useThemeStore = defineStore('theme', {
  state: (): ThemeState => ({ mode: 'system', primaryColor: '#1677ff', systemDark: false }),
  getters: {
    resolvedMode: (state): 'light' | 'dark' =>
      state.mode === 'system' ? (state.systemDark ? 'dark' : 'light') : state.mode,
  },
  actions: {
    applyTheme() {
      setDocumentTheme(this.resolvedMode, this.primaryColor)
    },
    setMode(mode: ThemeMode) {
      this.mode = mode
      this.applyTheme()
    },
    setPrimaryColor(color: string) {
      this.primaryColor = color
      this.applyTheme()
    },
    initialize() {
      mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
      this.systemDark = mediaQuery.matches
      mediaListener = (event) => {
        this.systemDark = event.matches
        this.applyTheme()
      }
      mediaQuery.addEventListener('change', mediaListener)
      this.applyTheme()
    },
    dispose() {
      if (mediaQuery && mediaListener) mediaQuery.removeEventListener('change', mediaListener)
      mediaQuery = undefined
      mediaListener = undefined
    },
  },
  persist: { key: 'novaops_theme', pick: ['mode', 'primaryColor'] },
})

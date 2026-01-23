import { defineStore } from 'pinia'

interface AppState {
  sidebar: {
    opened: boolean
    withoutAnimation: boolean
  }
  device: 'desktop' | 'mobile'
  size: 'default' | 'small' | 'large'
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    sidebar: {
      opened: true,
      withoutAnimation: false
    },
    device: 'desktop',
    size: 'default'
  }),

  actions: {
    toggleSidebar() {
      this.sidebar.opened = !this.sidebar.opened
      this.sidebar.withoutAnimation = false
    },

    closeSidebar(withoutAnimation: boolean) {
      this.sidebar.opened = false
      this.sidebar.withoutAnimation = withoutAnimation
    },

    toggleDevice(device: 'desktop' | 'mobile') {
      this.device = device
    },

    setSize(size: 'default' | 'small' | 'large') {
      this.size = size
    }
  },

  persist: {
    key: 'xuman-app',
    paths: ['sidebar.opened', 'size']
  }
})

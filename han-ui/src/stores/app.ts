import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebar = ref({ opened: true, withoutAnimation: false })
  const device = ref<'desktop' | 'mobile'>('desktop')
  const size = ref<'default' | 'small' | 'large'>('default')

  function toggleSidebar() {
    sidebar.value.opened = !sidebar.value.opened
    sidebar.value.withoutAnimation = false
  }

  function closeSidebar(withoutAnimation: boolean) {
    sidebar.value.opened = false
    sidebar.value.withoutAnimation = withoutAnimation
  }

  function toggleDevice(d: 'desktop' | 'mobile') {
    device.value = d
  }

  function setSize(s: 'default' | 'small' | 'large') {
    size.value = s
  }

  return { sidebar, device, size, toggleSidebar, closeSidebar, toggleDevice, setSize }
}, {
  persist: {
    key: 'HAN-app',
    pick: ['sidebar.opened', 'size']
  }
})

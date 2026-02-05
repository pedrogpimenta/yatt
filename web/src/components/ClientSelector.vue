<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({ id: null, name: '' })
  },
  clients: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: 'Client (optional)'
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const inputValue = ref('')
const dropdownOpen = ref(false)
const selectedIndex = ref(-1)
const pendingClear = ref(false)

const clientOptions = computed(() => {
  return (props.clients || []).map((c) => ({ id: c.id, name: c.name }))
})

const filteredOptions = computed(() => {
  const q = inputValue.value.trim().toLowerCase()
  if (!q) return clientOptions.value
  return clientOptions.value.filter((opt) => opt.name.toLowerCase().includes(q))
})

function syncInputWithSelection() {
  const v = props.modelValue
  const empty = !v || !(v.name && v.name.trim())
  if (empty) {
    pendingClear.value = false
    inputValue.value = ''
    return
  }
  if (inputValue.value === '' && pendingClear.value) {
    return
  }
  pendingClear.value = false
  inputValue.value = v.name || ''
}

watch(() => props.modelValue, syncInputWithSelection, { immediate: true, deep: true })
watch(() => props.clients, syncInputWithSelection, { deep: true })

function onFocus() {
  dropdownOpen.value = true
  selectedIndex.value = -1
}

function onBlur() {
  setTimeout(() => {
    dropdownOpen.value = false
    const trimmed = inputValue.value.trim()
    if (trimmed === '') {
      pendingClear.value = true
      emit('update:modelValue', { id: null, name: '' })
    } else {
      const match = clientOptions.value.find((c) => c.name.toLowerCase() === trimmed.toLowerCase())
      if (match) {
        emit('update:modelValue', { id: match.id, name: match.name })
      } else {
        emit('update:modelValue', { id: null, name: trimmed })
      }
    }
  }, 200)
}

function onInput(event) {
  inputValue.value = event.target.value
  if (inputValue.value.trim() === '') {
    pendingClear.value = true
    emit('update:modelValue', { id: null, name: '' })
  } else {
    pendingClear.value = false
    dropdownOpen.value = true
    selectedIndex.value = -1
    const trimmed = inputValue.value.trim()
    const match = clientOptions.value.find((c) => c.name === trimmed)
    if (match) {
      emit('update:modelValue', { id: match.id, name: match.name })
    } else {
      emit('update:modelValue', { id: null, name: trimmed })
    }
  }
}

function clearSelection(e) {
  e?.preventDefault()
  pendingClear.value = true
  inputValue.value = ''
  emit('update:modelValue', { id: null, name: '' })
  dropdownOpen.value = false
}

function selectOption(option) {
  inputValue.value = option.name
  emit('update:modelValue', { id: option.id, name: option.name })
  dropdownOpen.value = false
}

function onKeydown(e) {
  if (!dropdownOpen.value) return
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      if (filteredOptions.value.length > 0) {
        selectedIndex.value = Math.min(selectedIndex.value + 1, filteredOptions.value.length - 1)
      }
      break
    case 'ArrowUp':
      e.preventDefault()
      if (filteredOptions.value.length > 0) {
        selectedIndex.value = Math.max(selectedIndex.value - 1, -1)
      }
      break
    case 'Enter':
      if (selectedIndex.value >= 0 && filteredOptions.value.length > 0) {
        e.preventDefault()
        selectOption(filteredOptions.value[selectedIndex.value])
      }
      break
    case 'Escape':
      dropdownOpen.value = false
      selectedIndex.value = -1
      break
  }
}
</script>

<template>
  <div class="client-selector">
    <div class="client-input-wrapper">
      <input
        :value="inputValue"
        :placeholder="placeholder"
        :disabled="disabled"
        class="client-input"
        autocomplete="off"
        @input="onInput"
        @focus="onFocus"
        @blur="onBlur"
        @keydown="onKeydown"
      />
      <button
        v-if="inputValue"
        type="button"
        class="client-clear-btn"
        :disabled="disabled"
        @mousedown.prevent="clearSelection"
        title="Clear client"
        tabindex="-1"
      >
        ×
      </button>
      <span v-else class="client-chevron" aria-hidden="true">▼</span>
      <div
        v-if="dropdownOpen"
        class="client-dropdown"
        @mousedown.prevent
      >
        <button
          v-for="(option, index) in filteredOptions"
          :key="option.id"
          type="button"
          class="client-dropdown-option"
          :class="{ selected: index === selectedIndex }"
          @mousedown.prevent="selectOption(option)"
        >
          {{ option.name }}
        </button>
        <p v-if="filteredOptions.length === 0" class="client-dropdown-empty">
          {{ inputValue.trim() ? 'No matching clients. Type to add new.' : 'Type to search or add a new client.' }}
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.client-selector {
  width: 100%;
}

.client-input-wrapper {
  position: relative;
  width: 100%;
}

.client-input {
  width: 100%;
  padding: 0.75rem 1rem;
  padding-right: 2.25rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.875rem;
  box-sizing: border-box;
  appearance: none;
}

.client-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.client-input::placeholder {
  color: var(--text-muted);
}

.client-clear-btn {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  font-size: 1.125rem;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.client-clear-btn:hover:not(:disabled) {
  color: var(--text-primary);
  background: var(--bg-secondary);
}

.client-chevron {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  pointer-events: none;
  color: var(--text-muted);
  font-size: 0.625rem;
  line-height: 1;
}

.client-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
  max-height: 220px;
  overflow-y: auto;
  padding: 4px 0;
}

.client-dropdown-option {
  display: block;
  width: 100%;
  padding: 0.625rem 1rem;
  border: none;
  background: none;
  color: var(--text-primary);
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
  transition: background 0.1s;
}

.client-dropdown-option:hover,
.client-dropdown-option.selected {
  background: var(--bg-secondary);
}

.client-dropdown-option.selected {
  background: var(--accent-color);
  color: #fff;
}

.client-dropdown-empty {
  padding: 0.625rem 1rem;
  margin: 0;
  font-size: 0.8125rem;
  color: var(--text-muted);
}
</style>

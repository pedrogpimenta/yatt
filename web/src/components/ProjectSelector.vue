<script setup>
import { ref, computed, watch } from 'vue'
import { formatProjectLabel } from '../projects.js'

const props = defineProps({
  modelValue: {
    type: [Number, String, null],
    default: null
  },
  projects: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: 'Project (optional)'
  },
  disabled: {
    type: Boolean,
    default: false
  },
  allowCreate: {
    type: Boolean,
    default: true
  },
  onCreate: {
    type: Function,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const inputValue = ref('')
const dropdownOpen = ref(false)
const showCreateForm = ref(false)
const createError = ref('')
const isCreating = ref(false)
const createForm = ref({
  name: '',
  type: '',
  clientName: ''
})

const projectOptions = computed(() => {
  return (props.projects || []).map((project) => ({
    id: project.id,
    label: formatProjectLabel(project)
  }))
})

const filteredOptions = computed(() => {
  const q = inputValue.value.trim().toLowerCase()
  if (!q) return projectOptions.value
  return projectOptions.value.filter((opt) => opt.label.toLowerCase().includes(q))
})

function findProjectById(id) {
  if (id === null || id === undefined) return null
  return props.projects.find((project) => String(project.id) === String(id)) || null
}

function syncInputWithSelection() {
  const selected = findProjectById(props.modelValue)
  inputValue.value = selected ? formatProjectLabel(selected) : ''
}

watch(() => props.modelValue, syncInputWithSelection, { immediate: true })
watch(() => props.projects, syncInputWithSelection, { deep: true })

function handleInput(event) {
  inputValue.value = event.target.value
  dropdownOpen.value = true
  const match = projectOptions.value.find((option) => option.label === inputValue.value)
  emit('update:modelValue', match ? match.id : null)
}

function onFocus() {
  dropdownOpen.value = true
}

function onBlur() {
  setTimeout(() => {
    dropdownOpen.value = false
  }, 200)
}

function clearSelection(e) {
  e?.preventDefault()
  inputValue.value = ''
  emit('update:modelValue', null)
}

function selectOption(option) {
  inputValue.value = option.label
  emit('update:modelValue', option.id)
  dropdownOpen.value = false
}

function openCreateForm(e) {
  e?.preventDefault()
  dropdownOpen.value = false
  showCreateForm.value = true
  createError.value = ''
  const hasMatch = projectOptions.value.some((option) => option.label === inputValue.value)
  createForm.value.name = !hasMatch ? inputValue.value.trim() : ''
  createForm.value.type = ''
  createForm.value.clientName = ''
}

function closeCreateForm() {
  showCreateForm.value = false
  createError.value = ''
  createForm.value = { name: '', type: '', clientName: '' }
}

async function submitCreateForm() {
  createError.value = ''
  if (!createForm.value.name.trim()) {
    createError.value = 'Project name is required'
    return
  }
  if (!props.onCreate) {
    createError.value = 'Project creation is unavailable'
    return
  }

  isCreating.value = true
  try {
    const created = await props.onCreate({
      name: createForm.value.name.trim(),
      type: createForm.value.type.trim() || null,
      clientName: createForm.value.clientName.trim() || null
    })
    if (created?.id !== undefined) {
      emit('update:modelValue', created.id)
      inputValue.value = formatProjectLabel(created)
    }
    closeCreateForm()
  } catch (err) {
    createError.value = err?.message || 'Failed to create project'
  } finally {
    isCreating.value = false
  }
}
</script>

<template>
  <div class="project-selector">
    <div class="project-input-wrapper">
      <input
        :value="inputValue"
        :placeholder="placeholder"
        :disabled="disabled"
        class="project-input"
        autocomplete="off"
        @input="handleInput"
        @focus="onFocus"
        @blur="onBlur"
      />
      <button
        v-if="inputValue"
        type="button"
        class="project-clear-btn"
        :disabled="disabled"
        @mousedown.prevent="clearSelection"
        title="Clear project"
        tabindex="-1"
      >
        ×
      </button>
      <div
        v-if="dropdownOpen && !showCreateForm"
        class="project-dropdown"
        @mousedown.prevent
      >
        <button
          v-for="option in filteredOptions"
          :key="option.id"
          type="button"
          class="project-dropdown-option"
          @mousedown.prevent="selectOption(option)"
        >
          {{ option.label }}
        </button>
        <button
          v-if="allowCreate"
          type="button"
          class="project-dropdown-add"
          @mousedown.prevent="openCreateForm"
        >
          + Add new project
        </button>
        <p v-if="filteredOptions.length === 0 && !allowCreate" class="project-dropdown-empty">No projects</p>
      </div>
    </div>

    <div v-if="showCreateForm" class="project-create">
      <div class="project-create-fields">
        <input
          v-model="createForm.name"
          type="text"
          placeholder="Project name"
          class="sidebar-input"
        />
        <input
          v-model="createForm.type"
          type="text"
          placeholder="Project type (optional)"
          class="sidebar-input"
        />
        <input
          v-model="createForm.clientName"
          type="text"
          placeholder="Client name (optional)"
          class="sidebar-input"
        />
      </div>
      <p v-if="createError" class="project-error">{{ createError }}</p>
      <div class="project-create-actions">
        <button type="button" class="project-btn secondary" @click="closeCreateForm">
          Cancel
        </button>
        <button type="button" class="project-btn primary" :disabled="isCreating" @click="submitCreateForm">
          {{ isCreating ? 'Creating...' : 'Create Project' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.project-selector {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
}

.project-input-wrapper {
  position: relative;
  width: 100%;
}

.project-input {
  width: 100%;
  padding: 0.75rem 1rem;
  padding-right: 2.25rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.875rem;
  box-sizing: border-box;
}

.project-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.project-input::placeholder {
  color: var(--text-muted);
}

.project-clear-btn {
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

.project-clear-btn:hover:not(:disabled) {
  color: var(--text-primary);
  background: var(--bg-secondary);
}

.project-dropdown {
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

.project-dropdown-option {
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

.project-dropdown-option:hover {
  background: var(--bg-secondary);
}

.project-dropdown-add {
  display: block;
  width: 100%;
  padding: 0.625rem 1rem;
  border: none;
  border-top: 1px solid var(--border-color);
  background: none;
  color: var(--accent-color);
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
  transition: background 0.1s;
  margin-top: 4px;
}

.project-dropdown-add:hover {
  background: var(--bg-secondary);
}

.project-dropdown-empty {
  padding: 0.625rem 1rem;
  margin: 0;
  font-size: 0.875rem;
  color: var(--text-muted);
}

.project-create {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.project-create-fields {
  display: grid;
  gap: 0.5rem;
}

.sidebar-input {
  padding: 0.75rem 1rem;
  border-radius: 10px;
  border: 1px solid var(--border-color);
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 0.875rem;
  width: 100%;
  box-sizing: border-box;
}

.sidebar-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.project-error {
  color: var(--danger-color);
  font-size: 0.75rem;
  margin: 0;
}

.project-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

.project-btn {
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  font-size: 0.75rem;
  font-weight: 500;
}

.project-btn.secondary {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.project-btn.primary {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.project-btn.primary:hover:not(:disabled) {
  background: var(--accent-hover);
}
</style>

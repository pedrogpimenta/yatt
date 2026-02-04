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
const showCreateForm = ref(false)
const createError = ref('')
const isCreating = ref(false)
const createForm = ref({
  name: '',
  type: '',
  clientName: ''
})

const listId = `project-list-${Math.random().toString(36).slice(2, 10)}`

const projectOptions = computed(() => {
  return (props.projects || []).map((project) => ({
    id: project.id,
    label: formatProjectLabel(project)
  }))
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
  const match = projectOptions.value.find((option) => option.label === inputValue.value)
  emit('update:modelValue', match ? match.id : null)
}

function clearSelection() {
  inputValue.value = ''
  emit('update:modelValue', null)
}

function openCreateForm() {
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
    <div class="project-input-row">
      <input
        :value="inputValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :list="listId"
        @input="handleInput"
        class="project-input"
        autocomplete="off"
      />
      <datalist :id="listId">
        <option v-for="option in projectOptions" :key="option.id" :value="option.label" />
      </datalist>
      <button
        v-if="inputValue"
        type="button"
        class="project-clear-btn"
        :disabled="disabled"
        @click="clearSelection"
        title="Clear project"
      >
        ×
      </button>
    </div>

    <div v-if="allowCreate" class="project-actions">
      <button type="button" class="project-add-btn" :disabled="disabled" @click="openCreateForm">
        Add new project
      </button>
    </div>

    <div v-if="showCreateForm" class="project-create">
      <div class="project-create-fields">
        <input
          v-model="createForm.name"
          type="text"
          placeholder="Project name"
        />
        <input
          v-model="createForm.type"
          type="text"
          placeholder="Project type (optional)"
        />
        <input
          v-model="createForm.clientName"
          type="text"
          placeholder="Client name (optional)"
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

.project-input-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.project-input {
  flex: 1;
  padding: 0.75rem 1rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.875rem;
}

.project-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.project-clear-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-secondary);
  color: var(--text-muted);
  font-size: 1rem;
  line-height: 1;
  cursor: pointer;
}

.project-clear-btn:hover:not(:disabled) {
  color: var(--text-primary);
  border-color: var(--border-light);
}

.project-actions {
  display: flex;
  justify-content: flex-end;
}

.project-add-btn {
  border: 1px dashed var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  padding: 0.375rem 0.75rem;
  border-radius: 8px;
  font-size: 0.75rem;
  cursor: pointer;
}

.project-add-btn:hover:not(:disabled) {
  border-color: var(--accent-color);
  color: var(--accent-color);
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

.project-create-fields input {
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 0.8125rem;
}

.project-create-fields input:focus {
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

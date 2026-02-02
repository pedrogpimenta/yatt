<script setup>
import { ref, onMounted } from 'vue'
import { api } from '../api.js'

const emit = defineEmits(['close', 'saved'])

const apiUrl = ref('')
const saving = ref(false)

onMounted(async () => {
  apiUrl.value = await api.getApiUrl()
})

async function save() {
  saving.value = true
  await api.setApiUrl(apiUrl.value)
  saving.value = false
  emit('saved')
  emit('close')
}
</script>

<template>
  <div class="overlay" @click.self="$emit('close')">
    <div class="modal">
      <h2>Settings</h2>

      <div class="field">
        <label for="apiUrl">API Server URL</label>
        <input 
          id="apiUrl"
          v-model="apiUrl" 
          type="url" 
          placeholder="http://192.168.1.100:3000"
        />
        <p class="hint">Enter your YATT API server address</p>
      </div>

      <div class="actions">
        <button @click="$emit('close')" class="cancel-btn">Cancel</button>
        <button @click="save" class="save-btn" :disabled="saving">
          {{ saving ? 'Saving...' : 'Save' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  z-index: 100;
}

.modal {
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 1.5rem;
  width: 100%;
  max-width: 400px;
}

h2 {
  margin-bottom: 1.5rem;
  font-weight: 500;
  color: var(--text-primary);
}

.field {
  margin-bottom: 1.5rem;
}

.field label {
  display: block;
  font-size: 0.875rem;
  color: var(--text-secondary);
  margin-bottom: 0.5rem;
}

.field input {
  width: 100%;
  padding: 0.875rem;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-primary);
  font-size: 1rem;
}

.field input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.hint {
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.actions {
  display: flex;
  gap: 0.75rem;
  justify-content: flex-end;
}

.actions button {
  padding: 0.75rem 1.25rem;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 500;
}

.cancel-btn {
  background: transparent;
  border: 1px solid var(--border-light);
  color: var(--text-secondary);
}

.cancel-btn:active {
  border-color: var(--border-color);
  color: var(--text-primary);
}

.save-btn {
  background: var(--accent-color);
  border: none;
  color: #fff;
}

.save-btn:active:not(:disabled) {
  background: var(--accent-hover);
}

.save-btn:disabled {
  opacity: 0.6;
}
</style>

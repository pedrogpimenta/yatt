<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  modelValue: String,
  tags: Array,
  placeholder: {
    type: String,
    default: 'Tag (optional)'
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const showSuggestions = ref(false)

const filteredTags = computed(() => {
  if (!props.tags || props.tags.length === 0) return []
  
  const query = (props.modelValue || '').toLowerCase().trim()
  
  if (!query) {
    return props.tags.slice(0, 8)
  }
  
  return props.tags
    .filter(tag => tag.toLowerCase().includes(query))
    .slice(0, 8)
})

function onInput(e) {
  emit('update:modelValue', e.target.value)
  showSuggestions.value = true
}

function onFocus() {
  showSuggestions.value = true
}

function onBlur() {
  setTimeout(() => {
    showSuggestions.value = false
  }, 150)
}

function selectTag(tag) {
  emit('update:modelValue', tag)
  showSuggestions.value = false
}

function onKeydown(e) {
  if (e.key === 'Enter') {
    emit('submit')
    showSuggestions.value = false
  }
}
</script>

<template>
  <div class="tag-input-wrapper">
    <input
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      @input="onInput"
      @focus="onFocus"
      @blur="onBlur"
      @keydown="onKeydown"
      class="tag-input"
      autocomplete="off"
    />
    <div v-if="showSuggestions && filteredTags.length > 0" class="suggestions">
      <div
        v-for="tag in filteredTags"
        :key="tag"
        class="suggestion"
        @mousedown.prevent="selectTag(tag)"
        @touchstart.prevent="selectTag(tag)"
      >
        {{ tag }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.tag-input-wrapper {
  position: relative;
  width: 100%;
}

.tag-input {
  width: 100%;
  padding: 0.875rem 1rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  color: var(--text-primary);
  font-size: 1rem;
  text-align: center;
}

.tag-input:focus {
  outline: none;
  border-color: var(--accent-color);
}

.tag-input::placeholder {
  color: var(--text-muted);
}

.suggestions {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  z-index: 100;
  max-height: 200px;
  overflow-y: auto;
}

.suggestion {
  padding: 0.75rem 1rem;
  font-size: 0.9375rem;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
}

.suggestion:last-child {
  border-bottom: none;
}

.suggestion:active {
  background: var(--accent-color);
  color: #fff;
}
</style>

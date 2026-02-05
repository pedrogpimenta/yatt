<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: String,
  tags: Array,
  placeholder: {
    type: String,
    default: 'Tag (optional)'
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const inputRef = ref(null)
const showSuggestions = ref(false)
const selectedIndex = ref(-1)

const filteredTags = computed(() => {
  if (!props.tags || props.tags.length === 0) return []
  
  const query = (props.modelValue || '').toLowerCase().trim()
  
  if (!query) {
    // Show all tags when input is empty but focused
    return props.tags.slice(0, 10)
  }
  
  return props.tags
    .filter(tag => tag.toLowerCase().includes(query))
    .slice(0, 10)
})

function onInput(e) {
  emit('update:modelValue', e.target.value)
  selectedIndex.value = -1
  showSuggestions.value = true
}

function onFocus() {
  showSuggestions.value = true
}

function onBlur() {
  // Delay hiding to allow click on suggestion
  setTimeout(() => {
    showSuggestions.value = false
  }, 150)
}

function selectTag(tag) {
  emit('update:modelValue', tag)
  showSuggestions.value = false
  inputRef.value?.focus()
}

function onKeydown(e) {
  if (!showSuggestions.value || filteredTags.value.length === 0) {
    if (e.key === 'Enter') {
      emit('submit')
    }
    return
  }
  
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, filteredTags.value.length - 1)
      break
    case 'ArrowUp':
      e.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, -1)
      break
    case 'Enter':
      e.preventDefault()
      if (selectedIndex.value >= 0) {
        selectTag(filteredTags.value[selectedIndex.value])
      } else {
        emit('submit')
        showSuggestions.value = false
      }
      break
    case 'Escape':
      showSuggestions.value = false
      selectedIndex.value = -1
      break
  }
}
</script>

<template>
  <div class="tag-input-wrapper">
    <input
      ref="inputRef"
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
        v-for="(tag, index) in filteredTags"
        :key="tag"
        class="suggestion"
        :class="{ selected: index === selectedIndex }"
        @mousedown.prevent="selectTag(tag)"
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
  padding: 0.75rem 1rem;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-primary);
  font-size: 0.875rem;
  text-align: left;
  box-sizing: border-box;
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
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
  max-height: 200px;
  overflow-y: auto;
}

.suggestion {
  padding: 0.625rem 1rem;
  cursor: pointer;
  font-size: 0.875rem;
  color: var(--text-primary);
  transition: background 0.1s;
}

.suggestion:first-child {
  border-radius: 8px 8px 0 0;
}

.suggestion:last-child {
  border-radius: 0 0 8px 8px;
}

.suggestion:hover,
.suggestion.selected {
  background: var(--bg-secondary);
}

.suggestion.selected {
  background: var(--accent-color);
  color: #fff;
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    pointer: { x: number; y: number; active: boolean }
    size: number
    pupilSize: number
    white?: boolean
    maxDistance?: number
    forceLook?: { x: number; y: number } | null
    blinkDelay?: number
  }>(),
  {
    white: true,
    maxDistance: 5,
    forceLook: null,
    blinkDelay: 0,
  },
)

const eye = ref<HTMLElement | null>(null)
const pupil = computed(() => {
  if (props.forceLook) return props.forceLook
  if (!eye.value || !props.pointer.active) return { x: 0, y: 0 }

  // Measure the eye itself: the stage can grow or scale independently of its characters.
  const bounds = eye.value.getBoundingClientRect()
  const dx = props.pointer.x - bounds.left - bounds.width / 2
  const dy = props.pointer.y - bounds.top - bounds.height / 2
  const angle = Math.atan2(dy, dx)
  const distance = Math.min(props.maxDistance, Math.hypot(dx, dy))
  return { x: Math.cos(angle) * distance, y: Math.sin(angle) * distance }
})
</script>

<template>
  <span
    ref="eye"
    class="character-eye"
    :class="{ 'character-eye-white': white }"
    :style="{ width: `${size}px`, height: `${size}px`, '--blink-delay': `${blinkDelay}s` }"
    aria-hidden="true"
  >
    <span
      class="character-pupil"
      :style="{
        width: `${pupilSize}px`,
        height: `${pupilSize}px`,
        transform: `translate(${pupil.x}px, ${pupil.y}px)`,
      }"
    ></span>
  </span>
</template>

<style scoped>
.character-eye {
  display: grid;
  flex: none;
  place-items: center;
  border-radius: 50%;
  overflow: visible;
}

.character-eye-white {
  overflow: hidden;
  background: #fff;
  animation: eye-blink 6.8s var(--blink-delay) infinite;
}

.character-pupil {
  display: block;
  flex: none;
  border-radius: 50%;
  background: #2d2d2d;
  transition: transform 0.1s ease-out;
}

@keyframes eye-blink {
  0%,
  47%,
  50%,
  100% {
    scale: 1 1;
  }
  48.5%,
  49% {
    scale: 1 0.12;
  }
}

@media (prefers-reduced-motion: reduce) {
  .character-eye-white {
    animation: none;
  }
  .character-pupil {
    transition: none;
  }
}
</style>

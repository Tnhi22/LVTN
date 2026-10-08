<script setup>
import { ref, watch } from 'vue'
const props = defineProps({ src: String, name: String })
const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  },
)
</script>
<template>
  <div class="court-visual">
    <img
      v-if="src && !failed"
      :src="src"
      :alt="`Ảnh thực tế ${name}`"
      loading="lazy"
      @error="failed = true"
    />
    <div v-else class="court-placeholder">
      <svg viewBox="0 0 280 180" fill="none" aria-hidden="true">
        <path
          d="M28 24h224v132H28zM47 24v132m186-132v132M28 90h224M28 49h224M28 131h224M140 24v66m0 0v66"
          stroke="currentColor"
          stroke-width="2"
        />
        <path d="M15 90h250" stroke="#ff8500" stroke-width="3" /></svg
      ><span>{{ name }}</span
      ><small>Ảnh thực tế đang cập nhật</small>
    </div>
  </div>
</template>
<style scoped>
.court-visual {
  aspect-ratio: 16/10;
  background: #14251d;
  overflow: hidden;
}
.court-visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s;
}
.court-visual:hover img {
  transform: scale(1.03);
}
.court-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #59725a;
  background: radial-gradient(ellipse at center, #253b28, #14251d);
}
.court-placeholder svg {
  width: 72%;
  height: 60%;
}
.court-placeholder span {
  color: #c8d6bb;
  font-size: 18px;
  font-weight: 850;
}
.court-placeholder small {
  font-size: 9px;
  color: #92a782;
  margin-top: 5px;
}
</style>

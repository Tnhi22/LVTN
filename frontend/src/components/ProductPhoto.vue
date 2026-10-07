<script setup>
import { computed, ref, watch } from 'vue'
import { imageSource } from '../services/adminInventoryUtils'
const props = defineProps({ src: String, name: String })
const failed = ref(false)
const url = computed(() => imageSource(props.src))
watch(url, () => { failed.value = false })
</script>
<template><div class="product-photo"><img v-if="url && !failed" :src="url" :alt="name || 'Ảnh sản phẩm'" loading="lazy" @error="failed = true" /><div v-else class="placeholder"><svg viewBox="0 0 80 80" fill="none" aria-hidden="true"><path d="M26 12l8 40h12l8-40M20 15l14 37m26-37L46 52M40 10v42M23 25h34M27 38h26" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M33 53h14v7a7 7 0 01-14 0z" fill="currentColor"/></svg><small>{{failed?'Ảnh chưa tải được':'Chưa có ảnh'}}</small></div></div></template>
<style scoped>.product-photo{display:grid;place-items:center;background:#f5f7f1;overflow:hidden;border-radius:14px;aspect-ratio:1}.product-photo img{display:block;width:100%;height:100%;min-width:0;min-height:0;object-fit:contain;padding:6px}.placeholder{display:grid;place-items:center;width:100%;color:#a1b296}.placeholder svg{width:65px;height:65px}.placeholder small{font-size:10px;margin-bottom:10px;color:#85977d}</style>

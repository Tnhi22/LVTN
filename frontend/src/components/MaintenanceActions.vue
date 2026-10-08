<script setup>
import { nextTick, onUnmounted, ref } from 'vue';
import { getAdminData } from '../services/adminService';
const props = defineProps({
  auth: { type: Object, required: true },
  courts: { type: Array, default: () => [] },
});
const emit = defineEmits(['changed', 'session-expired']);
const dialog = ref(null),
  record = ref(null),
  mode = ref('edit'),
  form = ref({}),
  loading = ref(false),
  saving = ref(false),
  error = ref('');
let controller,
  disposed = false;
function message(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Yêu cầu hết thời gian. Tải lại để kiểm tra kết quả trước khi thử lại.'
    : e.message;
}
async function request(path, options = {}) {
  controller = new AbortController();
  const current = controller;
  const timeout = setTimeout(() => current.abort(), 15000);
  try {
    return await getAdminData(path, props.auth.accessToken, current.signal, options);
  } finally {
    clearTimeout(timeout);
  }
}
async function open(id, courtId, action) {
  if (saving.value || loading.value) return;
  mode.value = action;
  error.value = '';
  record.value = null;
  loading.value = true;
  await nextTick();
  dialog.value?.showModal();
  try {
    const rows = await request(`/api/court-maintenances/court/${courtId}`);
    if (disposed) return;
    const r = Array.isArray(rows) ? rows.find((r) => r.id === id) : null;
    if (!r) throw Error('Không tìm thấy lịch. Tải lại danh sách.');
    if (!['SCHEDULED', 'IN_PROGRESS', 'PENDING_APPROVAL'].includes(r.status))
      throw Error('Lịch đã hoàn tất hoặc đã hủy.');
    record.value = r;
    form.value = {
      courtId: r.court.id,
      type: r.type,
      reason: r.reason || '',
      startTime: r.startTime,
      endTime: r.endTime || '',
      maintenanceCost: r.maintenanceCost || 0,
      cancelReason: '',
      confirmed: false,
    };
  } catch (e) {
    if (!disposed) error.value = message(e);
  } finally {
    loading.value = false;
  }
}
function started() {
  return (
    record.value &&
    (record.value.status !== 'SCHEDULED' ||
      new Date(`${record.value.startTime}+07:00`).getTime() <= Date.now())
  );
}
function close() {
  if (saving.value) return;
  controller?.abort();
  dialog.value?.close();
  record.value = null;
}
async function submit() {
  if (saving.value || !record.value) return;
  error.value = '';
  let path = `/api/court-maintenances/${record.value.id}`,
    method = 'PUT',
    body;
  if (mode.value === 'cancel') {
    if (!form.value.cancelReason.trim()) {
      error.value = 'Nhập lý do hủy.';
      return;
    }
    path += '/cancel';
    method = 'PATCH';
    body = { reason: form.value.cancelReason.trim() };
  } else {
    if (
      !form.value.reason.trim() ||
      !form.value.startTime ||
      !form.value.courtId ||
      (form.value.type === 'SCHEDULED' && !form.value.endTime) ||
      (form.value.endTime && form.value.endTime <= form.value.startTime) ||
      !Number.isSafeInteger(Number(form.value.maintenanceCost)) ||
      Number(form.value.maintenanceCost) < 0
    ) {
      error.value = 'Kiểm tra sân, lý do, thời gian và chi phí nguyên không âm.';
      return;
    }
    if (form.value.type === 'EMERGENCY' && !form.value.confirmed) {
      error.value = 'Xác nhận ảnh hưởng booking trước khi lưu sự cố.';
      return;
    }
    body = {
      courtId: Number(form.value.courtId),
      type: form.value.type,
      reason: form.value.reason.trim(),
      startTime: form.value.startTime,
      endTime: form.value.endTime || null,
      maintenanceCost: Number(form.value.maintenanceCost),
    };
  }
  saving.value = true;
  try {
    await request(path, { method, body: JSON.stringify(body) });
    if (disposed) return;
    saving.value = false;
    close();
    emit(
      'changed',
      mode.value === 'cancel' ? 'Đã hủy lịch bảo trì.' : 'Đã cập nhật lịch bảo trì.',
    );
  } catch (e) {
    if (!disposed) error.value = message(e);
  } finally {
    saving.value = false;
  }
}
defineExpose({ open });
onUnmounted(() => {
  disposed = true;
  controller?.abort();
});
</script>
<template>
  <dialog ref="dialog" @cancel.prevent="close">
    <form @submit.prevent="submit">
      <header>
        <h3>
          {{ mode === 'cancel' ? 'Hủy lịch bảo trì' : 'Sửa lịch bảo trì' }}
          <span v-if="record">#{{ record.id }}</span>
        </h3>
        <button type="button" :disabled="saving" aria-label="Đóng" @click="close">
          ✕
        </button>
      </header>
      <p v-if="loading">Đang tải thông tin mới nhất…</p>
      <template v-if="record">
        <template v-if="mode === 'edit'">
          <p v-if="started()" class="hint">
            Lịch đã bắt đầu: giữ sân, loại và giờ bắt đầu; được sửa lý do, chi phí và giờ
            kết thúc.
          </p>
          <label
            >Sân<select v-model="form.courtId" :disabled="saving || started()" required>
              <option v-for="c in courts" :key="c.id" :value="c.id">{{ c.name }}</option>
              <option
                v-if="!courts.some((c) => c.id === form.courtId)"
                :value="form.courtId"
              >
                {{ record.court.name }}
              </option>
            </select></label
          >
          <label
            >Loại<select v-model="form.type" :disabled="saving || started()">
              <option value="SCHEDULED">Bảo trì theo kế hoạch</option>
              <option value="EMERGENCY">Sự cố đột xuất</option>
            </select></label
          >
          <label
            >Lý do / nội dung sửa chữa<textarea
              v-model="form.reason"
              required
              maxlength="1000"
              :disabled="saving"
            ></textarea>
          </label>
          <label
            >Bắt đầu (giờ Việt Nam)<input
              v-model="form.startTime"
              type="datetime-local"
              step="any"
              required
              :disabled="saving || started()"
          /></label>
          <label
            >Kết thúc dự kiến<input
              v-model="form.endTime"
              type="datetime-local"
              step="any"
              :required="form.type === 'SCHEDULED'"
              :disabled="saving"
          /></label>
          <label
            >Chi phí dự kiến (đ)<input
              v-model="form.maintenanceCost"
              type="number"
              min="0"
              step="1"
              required
              :disabled="saving"
          /></label>
          <label v-if="form.type === 'EMERGENCY'" class="check"
            ><input
              v-model="form.confirmed"
              type="checkbox"
              required
              :disabled="saving"
            />
            Tôi xác nhận sửa khung sự cố có thể hủy thêm booking chưa check-in bị ảnh
            hưởng.</label
          >
        </template>
        <template v-else
          ><p>Hủy lịch {{ record.court.name }}: {{ record.reason }}?</p>
          <label
            >Lý do hủy<textarea
              v-model="form.cancelReason"
              required
              maxlength="1000"
              :disabled="saving"
              placeholder="Ví dụ: Nhập nhầm thời gian, tạo lại lịch đúng…"
            ></textarea></label
        ></template>
        <p class="hint">
          Booking đã bị hủy trước đó không tự khôi phục. Sân vẫn bị chặn nếu còn lịch bảo
          trì khác.
        </p>
      </template>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <footer>
        <button type="button" :disabled="saving" @click="close">Đóng</button
        ><button
          v-if="record"
          :class="mode === 'cancel' ? 'danger' : 'primary'"
          :disabled="saving || loading"
        >
          {{ saving ? 'Đang lưu…' : mode === 'cancel' ? 'Xác nhận hủy' : 'Lưu thay đổi' }}
        </button>
      </footer>
    </form>
  </dialog>
</template>
<style scoped>
dialog {
  border: 0;
  border-radius: 16px;
  padding: 24px;
  width: min(600px, calc(100vw - 32px));
  box-sizing: border-box;
  max-height: 90vh;
  overflow: auto;
  color: #234732;
}
dialog::backdrop {
  background: #12302170;
}
header,
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
h3 {
  margin: 0;
  font-size: 20px;
}
footer {
  justify-content: flex-end;
  margin-top: 20px;
}
label {
  display: grid;
  gap: 8px;
  margin: 16px 0;
  font-size: 13px;
}
input,
select,
textarea,
button {
  font: inherit;
  font-size: 13px;
  border: 1px solid #dce7df;
  border-radius: 8px;
  padding: 10px;
  color: #245239;
  background: white;
  min-width: 0;
}
textarea {
  min-height: 90px;
  resize: vertical;
}
button {
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.primary {
  background: #176c46;
  color: white;
}
.danger {
  background: #fff0e9;
  color: #a45136;
}
.hint {
  font-size: 12px;
  color: #708276;
  line-height: 1.8;
}
.error {
  background: #fff0ec;
  color: #a44d30;
  padding: 12px;
  border-radius: 8px;
}
.check {
  display: flex;
  align-items: center;
  line-height: 1.7;
}
input:focus,
select:focus,
textarea:focus {
  outline: 2px solid #9fc899;
  outline-offset: 2px;
}
</style>

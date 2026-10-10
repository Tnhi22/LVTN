<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { getAdminData } from '../services/adminService.js';
const props = defineProps({
  auth: { type: Object, required: true },
  refreshKey: { type: Number, default: 0 },
});
const emit = defineEmits(['session-expired']);
const today = () =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(
    new Date(),
  );
const from = ref(today()),
  to = ref(today()),
  query = ref(''),
  source = ref(''),
  status = ref(''),
  rows = ref([]),
  error = ref(''),
  success = ref(''),
  busy = ref(false),
  saving = ref(false),
  clock = ref(Date.now()),
  detail = ref(null),
  dialog = ref(null),
  page = ref(1);
const labels = {
  CONFIRMED: 'Chờ nhận sân',
  CHECKED_IN: 'Đã check-in',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Vắng mặt',
  COMPLETED: 'Hoàn thành',
};
let controller,
  timer,
  sequence = 0,
  disposed = false,
  offset = 0;
const money = (v) =>
  v == null ? 'Chưa có giá' : Number(v).toLocaleString('vi-VN') + ' đ';
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      (!source.value || (source.value === 'WALK_IN') === r.walkIn) &&
      (!status.value || r.status === status.value) &&
      [r.id, r.customerName, r.phone, r.courtName, r.roomName, r.skillLevel]
        .join(' ')
        .toLowerCase()
        .includes(query.value.trim().toLowerCase()),
  ),
);
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 10)),
);
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 10, page.value * 10),
);
const start = (r) => new Date(`${r.date}T${r.startTime}+07:00`).getTime();
const live = (r) => !['CANCELLED', 'CLOSED'].includes(r.sessionStatus);
const canCheckIn = (r) =>
  r.status === 'CONFIRMED' &&
  live(r) &&
  clock.value >= start(r) &&
  clock.value < start(r) + 1800000;
const canCancel = (r) =>
  r.walkIn &&
  r.status === 'CONFIRMED' &&
  live(r) &&
  clock.value < start(r) - 1800000;
function message(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Máy chủ phản hồi quá lâu.'
    : e.message || 'Không xử lý được Daily.';
}
async function api(path, method = 'GET', signal) {
  return getAdminData(path, props.auth.accessToken, signal, { method });
}
async function load() {
  if (saving.value) return;
  if (
    !from.value ||
    !to.value ||
    from.value > to.value ||
    (Date.parse(to.value) - Date.parse(from.value)) / 86400000 > 30
  ) {
    error.value = 'Chọn khoảng ngày hợp lệ, tối đa 31 ngày.';
    return;
  }
  controller?.abort();
  controller = new AbortController();
  const current = controller,
    seq = ++sequence;
  busy.value = true;
  error.value = '';
  const timeout = setTimeout(() => current.abort(), 15000);
  try {
    const data = await api(
      `/api/counter-daily-bookings?from=${from.value}&to=${to.value}`,
      'GET',
      current.signal,
    );
    if (seq !== sequence || disposed) return;
    if (!Array.isArray(data.rows))
      throw new Error('Danh sách Daily không đúng định dạng.');
    rows.value = data.rows;
    offset = new Date(`${data.serverTime}+07:00`).getTime() - Date.now();
    clock.value = Date.now() + offset;
    page.value = Math.min(page.value, pages.value);
    if (detail.value)
      detail.value = data.rows.find((r) => r.id === detail.value.id) || null;
  } catch (e) {
    if (seq === sequence && !disposed) {
      error.value = message(e);
      rows.value = [];
    }
  } finally {
    clearTimeout(timeout);
    if (seq === sequence) busy.value = false;
  }
}
async function act(r, action) {
  if (saving.value) return;
  if (!(action === 'check-in' ? canCheckIn(r) : canCancel(r))) {
    error.value = 'Trạng thái hoặc thời gian hiện tại không cho phép thao tác.';
    return;
  }
  if (
    !window.confirm(
      action === 'check-in'
        ? `Check-in ${r.slotCount} người cho ${r.customerName}? Backend sẽ thực hiện quy trình thu tiền Daily.`
        : `Hủy Daily của ${r.customerName}?`,
    )
  )
    return;
  saving.value = true;
  error.value = '';
  success.value = '';
  const current = new AbortController(),
    timeout = setTimeout(() => current.abort(), 15000);
  try {
    await api(
      `/api/daily-visitor-participants/${r.id}/${action === 'check-in' ? 'check-in' : 'staff-cancel'}`,
      action === 'check-in' ? 'POST' : 'DELETE',
      current.signal,
    );
    success.value =
      action === 'check-in'
        ? 'Đã check-in theo quy trình Daily.'
        : 'Đã hủy Daily tại quầy.';
  } catch (e) {
    error.value = message(e);
  } finally {
    clearTimeout(timeout);
    const actionError = error.value;
    saving.value = false;
    await load();
    if (actionError) error.value = actionError;
  }
}
async function openDetail(r) {
  detail.value = r;
  await nextTick();
  dialog.value?.showModal();
}
function useToday() {
  from.value = to.value = today();
  page.value = 1;
  load();
}
watch([query, source, status], () => {
  page.value = 1;
});
watch(() => props.refreshKey, load);
onMounted(() => {
  load();
  timer = setInterval(() => {
    clock.value = Date.now() + offset;
    if (!document.hidden && !busy.value && !saving.value) load();
  }, 30000);
});
onUnmounted(() => {
  disposed = true;
  sequence++;
  controller?.abort();
  clearInterval(timer);
});
</script>
<template>
  <section class="daily-management">
    <header>
      <div>
        <small>CARROT / DAILY VISITOR</small>
        <h3>Tất cả lịch Daily</h3>
        <p>Khách có tài khoản và khách vãng lai tại quầy · Ca giờ cố định</p>
      </div>
      <button :disabled="busy || saving" @click="load">↻ Làm mới</button>
    </header>
    <form
      class="daily-filters"
      @submit.prevent="
        page = 1;
        load();
      "
    >
      <label>Từ ngày<input v-model="from" type="date" required /></label
      ><label
        >Đến ngày<input v-model="to" type="date" :min="from" required /></label
      ><button :disabled="busy || saving">Xem lịch</button
      ><button type="button" @click="useToday">Hôm nay</button>
    </form>
    <div class="daily-filters">
      <input
        v-model="query"
        type="search"
        placeholder="Tên, SĐT, sân, TBY/TB/TB+…"
        aria-label="Tìm Daily"
      /><select v-model="source" aria-label="Nguồn đăng ký">
        <option value="">Tất cả khách</option>
        <option value="ACCOUNT">Có tài khoản</option>
        <option value="WALK_IN">Vãng lai tại quầy</option></select
      ><select v-model="status" aria-label="Trạng thái">
        <option value="">Tất cả trạng thái</option>
        <option v-for="(label, key) in labels" :key="key" :value="key">
          {{ label }}
        </option>
      </select>
    </div>
    <p v-if="error" class="daily-error" role="alert">{{ error }}</p>
    <p v-if="success" class="daily-success" role="status">{{ success }}</p>
    <div class="daily-table-wrap">
      <table>
        <thead>
          <tr>
            <th>Ca / sân</th>
            <th>Khách / nguồn</th>
            <th>Số người</th>
            <th>Tiền</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in shown" :key="r.id">
            <td>
              <strong>{{ r.skillLevel }} · {{ r.courtName }}</strong
              ><small
                >{{ r.date }} · {{ r.startTime?.slice(0, 5) }}–{{
                  r.endTime?.slice(0, 5)
                }}
                · #{{ r.id }}</small
              >
            </td>
            <td>
              {{ r.customerName
              }}<small
                >{{ r.phone || 'Chưa lưu SĐT' }} ·
                {{ r.walkIn ? 'Vãng lai tại quầy' : 'Có tài khoản' }}</small
              >
            </td>
            <td>{{ r.slotCount }} người</td>
            <td>
              {{ money(r.amountDue)
              }}<small>{{
                r.paidAt ? 'Đã thu ' + money(r.amountPaid) : 'Chưa thu tiền'
              }}</small>
            </td>
            <td>
              <span :class="['daily-status', r.status]">{{
                labels[r.status] || r.status
              }}</span
              ><small v-if="r.sessionStatus === 'CANCELLED'"
                >Ca bị hủy:
                {{
                  r.cancelReason === 'NOT_ENOUGH_REGISTERED_PLAYERS'
                    ? 'Không đủ người đăng ký'
                    : r.cancelReason === 'NOT_ENOUGH_CHECKED_IN_PLAYERS'
                      ? 'Không đủ người check-in'
                      : r.cancelReason || 'Xem chi tiết ca'
                }}</small
              >
            </td>
            <td class="daily-actions">
              <button @click="openDetail(r)">Chi tiết</button
              ><button
                v-if="canCheckIn(r)"
                :disabled="saving"
                class="daily-table-checkin"
                @click="act(r, 'check-in')"
              >
                Check-in & nhận tiền</button
              ><button
                v-if="canCancel(r)"
                :disabled="saving"
                class="daily-table-cancel"
                @click="act(r, 'cancel')"
              >
                Hủy
              </button>
            </td>
          </tr>
          <tr v-if="!shown.length">
            <td colspan="6">
              {{ busy ? 'Đang tải…' : 'Chưa có lượt đăng ký Daily phù hợp.' }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer>
      <span>{{ filtered.length }} lượt đăng ký</span
      ><button :disabled="page <= 1" @click="page--">Trước</button
      ><span>{{ page }} / {{ pages }}</span
      ><button :disabled="page >= pages" @click="page++">Sau</button>
    </footer>
    <dialog ref="dialog" class="daily-detail-dialog" @cancel="detail = null">
      <template v-if="detail">
        <header class="daily-detail-hero">
          <div>
            <span class="daily-detail-eyebrow">CARROT · DAILY VISITOR</span>
            <h3>
              Chi tiết lượt chơi <span>#{{ detail.id }}</span>
            </h3>
            <p>
              {{ detail.skillLevel }} · {{ detail.slotCount }} người ·
              {{ detail.walkIn ? 'Tại quầy' : 'Đặt online' }}
            </p>
          </div>
          <button
            type="button"
            class="daily-detail-close"
            aria-label="Đóng chi tiết"
            @click="
              dialog.close();
              detail = null;
            "
          >
            ×
          </button>
        </header>
        <div class="daily-detail-content">
          <div class="daily-detail-status">
            <span :class="['daily-status-pill', detail.status]">{{
              labels[detail.status] || detail.status
            }}</span>
            <span :class="['daily-payment-pill', { paid: detail.paidAt }]">{{
              detail.paidAt ? '✓ Đã nhận tiền' : '◷ Chưa nhận tiền'
            }}</span>
          </div>
          <div class="daily-detail-grid">
            <section class="daily-info-card">
              <span class="daily-info-label">◉ NGƯỜI CHƠI</span>
              <h4>{{ detail.customerName }}</h4>
              <p>{{ detail.phone || 'Chưa có số điện thoại' }}</p>
              <small>{{
                detail.walkIn ? 'Khách vãng lai tại quầy' : 'Khách có tài khoản'
              }}</small>
            </section>
            <section class="daily-info-card">
              <span class="daily-info-label">⌖ SÂN & KHUNG GIỜ</span>
              <h4>{{ detail.courtName }}</h4>
              <p>{{ detail.roomName }} · {{ detail.skillLevel }}</p>
              <strong
                >{{ detail.startTime?.slice(0, 5) }} –
                {{ detail.endTime?.slice(0, 5) }}</strong
              >
              <small>{{ detail.date }} · Ca #{{ detail.sessionId }}</small>
            </section>
          </div>
          <section class="daily-money-card">
            <div>
              <span>Tổng phí lượt chơi</span
              ><strong>{{ money(detail.amountDue) }}</strong
              ><small>{{ detail.slotCount }} người tham gia</small>
            </div>
            <div class="daily-money-received">
              <span>Đã thu</span><strong>{{ money(detail.amountPaid) }}</strong
              ><small>{{
                detail.paidAt
                  ? 'Ghi nhận: ' + detail.paidAt.replace('T', ' ')
                  : 'Thu tiền theo quy trình check-in Daily'
              }}</small>
            </div>
          </section>
          <p v-if="detail.cancelReason" class="daily-detail-warning">
            {{
              detail.cancelReason === 'NOT_ENOUGH_REGISTERED_PLAYERS'
                ? 'Ca hủy vì không đủ người đăng ký.'
                : detail.cancelReason === 'NOT_ENOUGH_CHECKED_IN_PLAYERS'
                  ? 'Ca hủy vì không đủ người check-in.'
                  : detail.cancelReason
            }}
          </p>
          <p v-if="error" class="daily-detail-warning" role="alert">
            {{ error }}
          </p>
          <p v-if="success" class="daily-detail-success" role="status">
            {{ success }}
          </p>
          <p
            v-if="detail.status === 'CONFIRMED' && !canCheckIn(detail)"
            class="daily-action-note"
          >
            Check-in được mở từ giờ bắt đầu ca đến trước 30 phút sau đó. Ca phải
            còn hoạt động.
          </p>
          <div class="daily-detail-actions">
            <button
              v-if="canCheckIn(detail)"
              type="button"
              class="daily-checkin-action"
              :disabled="saving"
              @click="act(detail, 'check-in')"
            >
              {{
                saving
                  ? 'Đang xử lý…'
                  : detail.paidAt
                    ? '✓ Xác nhận check-in'
                    : '✓ Check-in & nhận tiền'
              }}<span v-if="!detail.paidAt">{{ money(detail.amountDue) }}</span>
            </button>
            <button
              v-if="canCancel(detail)"
              type="button"
              class="daily-cancel-action"
              :disabled="saving"
              @click="act(detail, 'cancel')"
            >
              Hủy lượt chơi
            </button>
            <button
              type="button"
              class="daily-back-action"
              @click="
                dialog.close();
                detail = null;
              "
            >
              Đóng chi tiết
            </button>
          </div>
        </div>
      </template>
    </dialog>
  </section>
</template>
<style scoped>
.daily-management {
  margin: 24px 0;
  padding: 24px;
  border: 1px solid #e4ded2;
  border-radius: 20px;
  background: #fffdf8;
  color: #304537;
}
header,
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
header small {
  font-size: 10px;
  letter-spacing: 2px;
  color: #947745;
}
h3 {
  font-size: 24px;
  margin: 8px 0;
}
p {
  font-size: 13px;
  line-height: 1.7;
  color: #778171;
}
.daily-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
  align-items: end;
}
.daily-filters label {
  font-size: 11px;
  display: grid;
  gap: 6px;
}
input,
select,
button {
  padding: 10px 12px;
  border: 1px solid #dddccf;
  border-radius: 9px;
  background: #fff;
  font: inherit;
  font-size: 12px;
  color: #405442;
}
button {
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
.daily-table-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  min-width: 850px;
}
th,
td {
  text-align: left;
  padding: 14px 10px;
  border-bottom: 1px solid #eee9df;
  font-size: 12px;
}
th {
  background: #f4f1e8;
  color: #7e806f;
  font-size: 11px;
}
td small {
  display: block;
  margin-top: 6px;
  color: #7c8577;
  font-size: 10px;
}
.daily-actions button {
  margin: 3px;
}
.daily-status {
  padding: 6px 9px;
  border-radius: 20px;
  background: #f4e7be;
  color: #947025;
}
.daily-status.CHECKED_IN {
  background: #dcefdc;
  color: #3c6d3c;
}
.daily-status.CANCELLED,
.daily-status.NO_SHOW {
  background: #f8dfd6;
  color: #9f583e;
}
.daily-error {
  color: #a73e32;
}
.daily-success {
  color: #3d7548;
}
footer {
  margin-top: 16px;
  font-size: 12px;
}
dialog {
  max-width: 540px;
  width: calc(100% - 48px);
  padding: 26px;
  border: 1px solid #e2decd;
  border-radius: 20px;
  background: #fffdf8;
}
dialog::backdrop {
  background: #16302499;
}

.daily-table-checkin {
  background: #244b38;
  color: #fff;
  border-color: #244b38;
  box-shadow: 0 3px 8px #244b3822;
}
.daily-table-cancel {
  color: #a44535;
  background: #fff0ea;
  border-color: #e7c9be;
}
.daily-detail-dialog {
  width: min(680px, calc(100% - 32px));
  max-width: 680px;
  padding: 0;
  max-height: 88vh;
  overflow-y: auto;
  border-radius: 24px;
  border: 1px solid #ddd8ca;
  box-shadow: 0 28px 90px #101e2544;
}
.daily-detail-dialog::backdrop {
  background: #101d24aa;
  backdrop-filter: blur(5px);
}
.daily-detail-dialog .daily-detail-hero {
  margin: 0;
  padding: 28px;
  background:
    radial-gradient(ellipse at top right, #3e5b45, transparent 75%), #172820;
  color: #fff;
  align-items: flex-start;
}
.daily-detail-eyebrow {
  font-size: 9px;
  letter-spacing: 2px;
  font-weight: 800;
  color: #edc38b;
}
.daily-detail-hero h3 {
  font-size: 25px;
  letter-spacing: -0.6px;
  margin: 12px 0 8px;
  color: #fff;
}
.daily-detail-hero h3 span {
  color: #edc38b;
}
.daily-detail-hero p {
  color: #b8cbbb;
  margin: 0;
  font-size: 12px;
}
.daily-detail-dialog .daily-detail-close {
  border-radius: 50%;
  background: #ffffff12;
  color: #fff;
  border: 1px solid #ffffff22;
  width: 36px;
  height: 36px;
  font-size: 22px;
  padding: 0;
}
.daily-detail-content {
  padding: 26px;
}
.daily-detail-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}
.daily-status-pill,
.daily-payment-pill {
  border-radius: 30px;
  padding: 8px 13px;
  background: #f3e8d1;
  color: #875e24;
  font-size: 11px;
  font-weight: 800;
}
.daily-status-pill.CHECKED_IN,
.daily-payment-pill.paid {
  background: #e5eee2;
  color: #315d36;
}
.daily-status-pill.CANCELLED {
  background: #f9e7e1;
  color: #9b4737;
}
.daily-detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.daily-info-card {
  background: #fff;
  border: 1px solid #e9e3d7;
  padding: 20px;
  border-radius: 16px;
  min-width: 0;
}
.daily-info-label {
  font-size: 10px;
  letter-spacing: 1px;
  color: #8a795b;
  font-weight: 800;
}
.daily-info-card h4 {
  margin: 12px 0 8px;
  font-size: 19px;
  color: #293e30;
  overflow-wrap: anywhere;
}
.daily-info-card p {
  margin: 0 0 10px;
  color: #687665;
  font-size: 13px;
}
.daily-info-card small {
  display: block;
  margin-top: 10px;
  color: #879180;
  font-size: 11px;
}
.daily-money-card {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  background: #f2eadb;
  border: 1px solid #e1d6bd;
  padding: 22px;
  border-radius: 16px;
  margin-top: 16px;
}
.daily-money-card span,
.daily-money-card small {
  display: block;
  font-size: 12px;
  color: #7d705b;
  line-height: 1.7;
}
.daily-money-card strong {
  display: block;
  font-size: 27px;
  margin: 6px 0;
  color: #263d2d;
  letter-spacing: -0.7px;
}
.daily-money-received {
  border-left: 1px solid #d8cbb2;
  padding-left: 20px;
}
.daily-detail-warning,
.daily-detail-success {
  padding: 12px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.7;
  background: #fff0eb;
  color: #974836;
}
.daily-detail-success {
  background: #e8f1e4;
  color: #365b33;
}
.daily-action-note {
  font-size: 12px;
  color: #837966;
  line-height: 1.7;
  margin: 18px 0;
}
.daily-detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 22px;
}
.daily-detail-dialog .daily-checkin-action {
  background: #264b36;
  color: #fff;
  border-color: #264b36;
  padding: 13px 18px;
  box-shadow: 0 5px 15px #264b3625;
  font-weight: 800;
}
.daily-checkin-action span {
  display: block;
  font-size: 11px;
  opacity: 0.8;
  margin-top: 4px;
}
.daily-detail-dialog .daily-cancel-action {
  color: #a14735;
  background: #fff3ed;
  border-color: #e4cabb;
  padding: 13px 16px;
}
.daily-detail-dialog .daily-back-action {
  margin-left: auto;
  padding: 13px 16px;
}
.daily-detail-dialog button {
  transition:
    transform 0.2s,
    filter 0.2s;
}
.daily-detail-dialog button:hover:not(:disabled) {
  transform: translateY(-2px);
  filter: brightness(1.06);
}
.daily-detail-dialog button:focus-visible {
  outline: 3px solid #d4a969;
  outline-offset: 3px;
}
@media (max-width: 520px) {
  .daily-detail-grid,
  .daily-money-card {
    grid-template-columns: 1fr;
  }
  .daily-money-received {
    padding-left: 0;
    border-left: 0;
    border-top: 1px solid #d8cbb2;
    padding-top: 14px;
  }
  .daily-detail-content,
  .daily-detail-dialog .daily-detail-hero {
    padding: 20px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .daily-detail-dialog button {
    transition: none;
  }
  .daily-detail-dialog button:hover:not(:disabled) {
    transform: none;
  }
}
</style>

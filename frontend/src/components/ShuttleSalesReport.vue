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
  productId = ref(''),
  source = ref('');
const dailyUsage = ref(null);
const dailyUsageError = ref('');
const report = ref(null),
  loading = ref(false),
  error = ref(''),
  page = ref(1);
const detail = ref(null),
  detailDialog = ref(null),
  detailLoading = ref(false),
  detailError = ref('');
const labels = {
  BOOKING: 'Kèm booking',
  ADDON: 'Mua thêm vào booking',
  COUNTER: 'Bán riêng tại quầy',
};
const statuses = {
  PENDING: 'Chờ nhận sân',
  CHECKED_IN: 'Đang chơi',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Không đến',
};
const money = (n) => Number(n || 0).toLocaleString('vi-VN') + ' đ';
const stamp = (v) =>
  v
    ? `${v.slice(8, 10)}/${v.slice(5, 7)}/${v.slice(0, 4)} · ${v.slice(11, 16)}`
    : '—';
const filtered = computed(() =>
  (report.value?.rows || []).filter(
    (r) =>
      (!productId.value || r.productId === Number(productId.value)) &&
      (!source.value || r.source === source.value) &&
      [
        r.productName,
        r.bookingId,
        r.courtName,
        r.customerName,
        r.customerPhone,
        r.issueCode,
      ]
        .join(' ')
        .toLocaleLowerCase('vi')
        .includes(query.value.trim().toLocaleLowerCase('vi')),
  ),
);
const total = computed(() =>
  filtered.value.reduce((sum, r) => sum + Number(r.quantityTubes || 0), 0),
);
const totalPieces = computed(() =>
  filtered.value.reduce((sum, r) => sum + Number(r.quantityPieces || 0), 0),
);
const totals = computed(() => {
  const map = new Map();
  for (const r of filtered.value) {
    const old = map.get(r.productId);
    map.set(r.productId, {
      id: r.productId,
      name: r.productName,
      count: (old?.count || 0) + Number(r.quantityTubes || 0),
      pieces: (old?.pieces || 0) + Number(r.quantityPieces || 0),
    });
  }
  return [...map.values()].sort((a, b) => b.count - a.count);
});
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 15)),
);
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 15, page.value * 15),
);
let controller,
  detailController,
  disposed = false;
async function api(path, current) {
  const timer = setTimeout(() => current.abort(), 15000);
  try {
    return await getAdminData(path, props.auth.accessToken, current.signal);
  } finally {
    clearTimeout(timer);
  }
}
function message(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Máy chủ phản hồi quá lâu. Hãy thử lại.'
    : e.message || 'Không tải được thống kê.';
}
async function load() {
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
  const current = new AbortController();
  controller = current;
  loading.value = true;
  error.value = '';
  try {
    const [salesResult, dailyResult] = await Promise.allSettled([
      api(
        `/api/shuttle-sales-report?from=${from.value}&to=${to.value}`,
        current,
      ),
      api(
        `/api/shuttle-sales-report/daily-usage?from=${from.value}&to=${to.value}`,
        current,
      ),
    ]);
    if (disposed || current !== controller) return;
    dailyUsage.value =
      dailyResult.status === 'fulfilled' ? dailyResult.value : null;
    dailyUsageError.value =
      dailyResult.status === 'rejected' ? dailyResult.reason.message : '';
    if (salesResult.status === 'rejected') throw salesResult.reason;
    const data = salesResult.value;
    if (!disposed && current === controller) {
      report.value = data;
      page.value = 1;
    }
  } catch (e) {
    if (!disposed && current === controller) {
      error.value = message(e);
      report.value = null;
    }
  } finally {
    if (current === controller) loading.value = false;
  }
}
async function openBooking(id) {
  detailController?.abort();
  const current = new AbortController();
  detailController = current;
  detail.value = null;
  detailLoading.value = true;
  detailError.value = '';
  await nextTick();
  if (disposed) return;
  detailDialog.value?.showModal();
  try {
    const data = await api(`/api/shuttle-sales-report/bookings/${id}`, current);
    if (!disposed && current === detailController) detail.value = data;
  } catch (e) {
    if (!disposed && current === detailController)
      detailError.value = message(e);
  } finally {
    if (current === detailController) detailLoading.value = false;
  }
}
function closeDetail() {
  detailController?.abort();
  detailDialog.value?.close();
  detail.value = null;
}
function useToday() {
  from.value = to.value = today();
  load();
}
watch([query, productId, source], () => {
  page.value = 1;
});
watch(pages, (n) => {
  page.value = Math.min(page.value, n);
});
watch(
  () => props.refreshKey,
  () => load(),
);
onMounted(load);
onUnmounted(() => {
  disposed = true;
  controller?.abort();
  detailController?.abort();
});
</script>
<template>
  <section class="shuttle-report">
    <header>
      <div>
        <small>CARROT / SẢN PHẨM ĐÃ GIAO</small>
        <h3>Cầu đã bán · nguyên ống & bán lẻ</h3>
        <p>
          Theo dõi loại cầu, số ống, số quả, khách mua và booking theo ngày.
        </p>
      </div>
      <button :disabled="loading" @click="load">↻ Làm mới</button>
    </header>
    <form class="filters" @submit.prevent="load">
      <label>Từ ngày<input v-model="from" type="date" required /></label
      ><label
        >Đến ngày<input v-model="to" type="date" :min="from" required /></label
      ><button class="primary" :disabled="loading">
        {{ loading ? 'Đang tải…' : 'Xem thống kê' }}</button
      ><button type="button" :disabled="loading" @click="useToday">
        Hôm nay
      </button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p class="note">
      Tính theo ngày giao / xuất kho; không tính cầu đang giữ, phiếu đã hủy và
      cầu tiêu hao Daily Visitor. Dữ liệu gồm toàn bộ giao dịch Admin và Staff.
    </p>
    <template v-if="report">
      <div class="metrics">
        <div class="total">
          <span>Tổng số ống phù hợp</span
          ><strong
            >{{ total }} <small>ống</small> · {{ totalPieces }}
            <small>quả lẻ</small></strong
          ><span
            >{{ filtered.length }} phiếu · {{ report.from }} →
            {{ report.to }}</span
          >
        </div>
        <div v-for="p in totals" :key="p.id">
          <span>{{ p.name }}</span
          ><strong
            >{{ p.count }} <small>ống</small> · {{ p.pieces }}
            <small>quả lẻ</small></strong
          >
        </div>
      </div>
      <div class="filters">
        <label class="search"
          >Tìm kiếm<input
            v-model="query"
            placeholder="Tên cầu, P1-01, mã booking, khách…" /></label
        ><label
          >Loại cầu<select v-model="productId">
            <option value="">Tất cả loại cầu</option>
            <option
              v-for="p in report.products"
              :key="p.productId"
              :value="p.productId"
            >
              {{ p.productName }}
            </option>
          </select></label
        ><label
          >Hình thức<select v-model="source">
            <option value="">Tất cả</option>
            <option v-for="(label, key) in labels" :key="key" :value="key">
              {{ label }}
            </option>
          </select></label
        >
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Thời gian giao / phiếu</th>
              <th>Tên cầu</th>
              <th>Ống / quả lẻ</th>
              <th>Booking / khách</th>
              <th>Sân</th>
              <th>Hình thức</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in shown" :key="r.issueId">
              <td>
                {{ stamp(r.issuedAt) }}<small>{{ r.issueCode }}</small>
              </td>
              <td>
                <strong>{{ r.productName }}</strong
                ><small
                  >{{ money(r.unitPrice) }} /
                  {{ r.quantityPieces > 0 ? 'quả' : 'ống' }}</small
                >
              </td>
              <td>
                <span class="quantity"
                  >{{ r.quantityTubes || 0 }} ống ·
                  {{ r.quantityPieces || 0 }} quả</span
                >
              </td>
              <td>
                <button
                  v-if="r.bookingId"
                  class="booking-link"
                  @click="openBooking(r.bookingId)"
                >
                  #{{ r.bookingId }} ↗</button
                ><span v-else>Không gắn booking</span
                ><small
                  >{{ r.customerName }}<br />{{
                    r.customerPhone || 'Chưa lưu SĐT'
                  }}</small
                >
              </td>
              <td>{{ r.courtName || '—' }}</td>
              <td>
                <span class="badge">{{ labels[r.source] }}</span>
              </td>
            </tr>
            <tr v-if="!shown.length">
              <td colspan="6" class="empty">
                Không có giao dịch bán cầu phù hợp.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer>
        <span>{{ filtered.length }} phiếu</span
        ><button :disabled="page <= 1" @click="page--">Trước</button
        ><span>{{ page }} / {{ pages }}</span
        ><button :disabled="page >= pages" @click="page++">Sau</button>
      </footer>
    </template>
    <section
      class="daily-usage-section"
      aria-label="Thống kê ống cầu sử dụng cho Daily"
    >
      <header class="daily-usage-heading">
        <div>
          <small>DAILY VISITOR · XUẤT KHO</small>
          <h3>Ống cầu sử dụng cho Daily</h3>
          <p>Theo ngày xuất kho · Tách riêng với cầu bán và doanh thu</p>
        </div>
        <strong v-if="dailyUsage"
          >{{ dailyUsage.totalTubes }} <span>ống</span></strong
        >
      </header>
      <p v-if="dailyUsageError" role="alert" class="error">
        {{ dailyUsageError }}
      </p>
      <template v-if="dailyUsage">
        <div class="daily-usage-products">
          <div v-for="p in dailyUsage.products" :key="p.productId">
            <span>{{ p.productName }}</span
            ><strong>{{ p.quantityTubes }} ống</strong>
          </div>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Phiếu xuất / thời gian</th>
                <th>Ca Daily</th>
                <th>Sân / trình độ</th>
                <th>Loại cầu</th>
                <th>Số ống</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in dailyUsage.rows" :key="r.issueId">
                <td>
                  <strong>{{ r.issueCode || '#' + r.issueId }}</strong
                  ><small>{{ stamp(r.issuedAt) }}</small>
                </td>
                <td>
                  <strong>Ca #{{ r.sessionId || '—' }}</strong
                  ><small
                    >{{ r.sessionDate }} · {{ r.startTime?.slice(0, 5) }}–{{
                      r.endTime?.slice(0, 5)
                    }}</small
                  >
                </td>
                <td>
                  <strong>{{ r.courtName || 'Chưa có thông tin sân' }}</strong
                  ><small>{{ r.skillLevel }} · {{ r.sessionStatus }}</small>
                </td>
                <td>{{ r.productName }}</td>
                <td>
                  <b class="daily-usage-quantity">{{ r.quantityTubes }} ống</b>
                </td>
              </tr>
              <tr v-if="!dailyUsage.rows.length">
                <td colspan="5">
                  Chưa có phiếu xuất cầu Daily trong khoảng ngày này.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="daily-usage-note">
          Chỉ tính phiếu xuất kho Daily còn hiệu lực. Ca chưa xuất cầu không
          cộng; ca hủy sau khi đã xuất vẫn tính nếu phiếu chưa được hoàn/hủy.
        </p>
      </template>
    </section>
    <dialog ref="detailDialog" @cancel.prevent="closeDetail">
      <header>
        <h3>Chi tiết booking {{ detail ? '#' + detail.id : '' }}</h3>
        <button @click="closeDetail">✕</button>
      </header>
      <p v-if="detailLoading">Đang tải booking…</p>
      <p v-if="detailError" class="error">{{ detailError }}</p>
      <template v-if="detail"
        ><h2>{{ detail.courtName }} · {{ detail.roomName }}</h2>
        <p>{{ detail.customerName }} · {{ detail.phone || '—' }}</p>
        <p>
          {{ detail.bookingDate }} · {{ detail.startTime?.slice(0, 5) }} –
          {{ detail.endTime?.slice(0, 5) }}
        </p>
        <p>
          {{ statuses[detail.status] || detail.status }} ·
          {{
            detail.paidAt
              ? 'Đã thu tiền — ' +
                (detail.paymentMethod === 'CASH' ? 'Tiền mặt' : 'Chuyển khoản')
              : 'Chưa thu tiền'
          }}
        </p>
        <div class="bill">
          <p>
            Tiền sân <strong>{{ money(detail.receipt.courtAmount) }}</strong>
          </p>
          <p>
            Tiền cầu
            <strong>{{ money(detail.receipt.shuttlecockAmount) }}</strong>
          </p>
          <p>
            Tổng booking
            <strong>{{ money(detail.receipt.totalAmount) }}</strong>
          </p>
          <p
            v-for="(item, i) in detail.receipt.items"
            :key="item.issueId || i"
            :class="{ cancelled: item.cancelled }"
          >
            {{ item.productName }} · {{ item.quantityTubes || 0 }} ống
            {{ item.cancelled ? '(Đã hủy)' : ''
            }}<strong>{{ money(item.totalAmount) }}</strong>
          </p>
        </div>
        <p class="note">
          Đơn bán riêng được thống kê theo phiếu riêng và không cộng vào tổng
          booking.
        </p></template
      >
    </dialog>
  </section>
</template>
<style scoped>
.shuttle-report {
  margin: 22px 0;
  padding: 24px;
  background: #fffdf9;
  border: 1px solid #e8ddd0;
  border-radius: 18px;
  color: #352b23;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
header small {
  font-size: 10px;
  letter-spacing: 1.5px;
  color: #b16d27;
  font-weight: 800;
}
h3 {
  margin: 8px 0;
  font-size: 23px;
}
header p,
.note {
  font-size: 12px;
  color: #817568;
  line-height: 1.7;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
  margin: 20px 0;
}
label {
  display: grid;
  gap: 7px;
  font-size: 12px;
  font-weight: 700;
}
.search {
  flex: 1;
  min-width: 200px;
}
button,
input,
select {
  box-sizing: border-box;
  min-height: 40px;
  padding: 10px 12px;
  border: 1px solid #e8ddd0;
  border-radius: 9px;
  background: #fff;
  font: inherit;
  font-size: 12px;
  color: inherit;
}
button {
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.primary {
  background: #df802c;
  color: #fff;
  border-color: #df802c;
}
.metrics {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 20px 0;
}
.metrics > div {
  display: grid;
  gap: 9px;
  min-width: 150px;
  flex: 1;
  padding: 18px;
  background: #f8f3eb;
  border-radius: 12px;
}
.metrics > div.total {
  background: #f8e8d4;
}
.metrics span {
  font-size: 12px;
  color: #817568;
}
.metrics strong {
  font-size: 27px;
}
.metrics small {
  font-size: 12px;
}
.table-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}
th {
  padding: 13px;
  background: #f8f3eb;
  font-size: 11px;
  color: #817568;
}
td {
  padding: 15px 12px;
  border-bottom: 1px solid #eee5d9;
}
td small {
  display: block;
  margin-top: 6px;
  color: #817568;
  font-size: 11px;
}
.quantity {
  font-weight: 800;
  color: #a9611f;
}
.booking-link {
  border: 0;
  background: #fff0dd;
  color: #a9611f;
  font-weight: 800;
}
.badge {
  font-size: 11px;
  background: #f3eee5;
  padding: 7px;
  border-radius: 7px;
}
.empty {
  text-align: center;
  padding: 30px;
  color: #817568;
}
footer {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 16px;
  font-size: 12px;
}
footer > span:first-child {
  margin-right: auto;
}
.error {
  padding: 12px;
  color: #a43e24;
  background: #fff0eb;
  border-radius: 10px;
  font-size: 13px;
}
dialog {
  width: min(600px, calc(100% - 30px));
  max-height: 85dvh;
  overflow: auto;
  box-sizing: border-box;
  padding: 24px;
  border: 0;
  border-radius: 18px;
  color: #352b23;
}
dialog::backdrop {
  background: #20150e90;
}
.bill {
  padding: 15px;
  background: #f8f3eb;
  border-radius: 12px;
}
.bill p {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  font-size: 13px;
}
.cancelled {
  text-decoration: line-through;
  opacity: 0.6;
}
dialog p {
  line-height: 1.7;
}
input:focus-visible,
select:focus-visible,
button:focus-visible {
  outline: 2px solid #df802c;
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .shuttle-report {
    padding: 16px;
  }
  header {
    align-items: flex-start;
  }
  header h3 {
    font-size: 20px;
  }
  .filters label {
    flex: 1;
  }
  .metrics > div {
    min-width: 110px;
  }
  .filters input,
  .filters select {
    width: 100%;
  }
  dialog {
    padding: 18px;
  }
}

.daily-usage-section {
  margin-top: 28px;
  padding: 24px;
  border: 1px solid #dbd8c6;
  border-radius: 20px;
  background: #f7f5ed;
}
.daily-usage-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}
.daily-usage-heading small {
  color: #98805a;
  letter-spacing: 1.5px;
  font-size: 9px;
  font-weight: 800;
}
.daily-usage-heading h3 {
  color: #294535;
  margin: 8px 0;
  font-size: 22px;
}
.daily-usage-heading p,
.daily-usage-note {
  color: #7f826d;
  font-size: 12px;
  line-height: 1.7;
}
.daily-usage-heading > strong {
  font-size: 36px;
  color: #315640;
}
.daily-usage-heading > strong span {
  font-size: 13px;
}
.daily-usage-products {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.daily-usage-products > div {
  display: grid;
  gap: 8px;
  background: #fff;
  border: 1px solid #e1decf;
  border-radius: 12px;
  padding: 14px 18px;
}
.daily-usage-products span {
  font-size: 12px;
  color: #79806b;
}
.daily-usage-products strong {
  color: #365a3b;
  font-size: 18px;
}
.daily-usage-quantity {
  color: #345b38;
  white-space: nowrap;
}
.daily-usage-section td small {
  display: block;
  margin-top: 5px;
  color: #888a77;
  font-size: 11px;
}
@media (max-width: 600px) {
  .daily-usage-section {
    padding: 16px;
  }
}
</style>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { settlementBody } from '../services/adminBookingUtils.js';
import StaffBookingsView from './StaffBookingsView.vue';
import BookingCounterActions from '../components/BookingCounterActions.vue';
import ShuttleSalesReport from '../components/ShuttleSalesReport.vue';
import MaintenanceActions from '../components/MaintenanceActions.vue';
import { getAdminData } from '../services/adminService.js';
const props = defineProps({ auth: { type: Object, required: true } });
const emit = defineEmits(['session-expired']);
function today() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date());
}
const maintenanceActions = ref(null);
async function maintenanceChanged(message) {
  success.value = message;
  await load();
}
const bookingWorkspace = ref(null);
const counterWork = ref(null);
const counterRefresh = ref(0);
async function counterDone() {
  counterWork.value = null;
  counterRefresh.value++;
  await load();
}
const section = ref('overview');
const tabs = [
  { id: 'overview', label: 'Tổng quan', icon: '◫' },
  { id: 'schedule', label: 'Đặt sân & khách tại quầy', icon: '▤' },
  { id: 'maintenance', label: 'Bảo trì / sửa chữa', icon: '⚒' },
  { id: 'stats', label: 'Thống kê của tôi', icon: '◷' },
];
const date = ref(today());
const actionDialog = ref(null);
const formKind = ref('');
const formError = ref('');
const form = ref({});
const counterOnly = ref(false);
const data = ref(null);
const loading = ref(false);
const saving = ref(false);
const error = ref('');
const success = ref('');
const query = ref('');
const status = ref('');
const page = ref(1);
const selected = ref(null);
const dialog = ref(null);
const mode = ref('detail');
let controller,
  actionController,
  timer,
  disposed = false;
const labels = {
  PENDING: 'Chờ nhận sân',
  NO_SHOW_PENDING: 'Trễ nhận sân',
  CHECKED_IN: 'Đã check-in',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Không đến',
};
const money = (value) => Number(value || 0).toLocaleString('vi-VN') + ' đ';
const time = (value) => value?.slice(0, 5) || '—';
const filtered = computed(() =>
  (data.value?.bookings || []).filter(
    (b) =>
      (!counterOnly.value || b.walkIn) &&
      (!status.value || b.status === status.value) &&
      `${b.id} ${b.customerName} ${b.phone || ''} ${b.courtName}`
        .toLocaleLowerCase('vi')
        .includes(query.value.trim().toLocaleLowerCase('vi')),
  ),
);
const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 10)),
);
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 10, page.value * 10),
);
const recentBookings = computed(() =>
  [...(data.value?.bookings || [])].sort((a, b) => b.id - a.id).slice(0, 5),
);
const activeCourts = computed(() =>
  (data.value?.courts || []).filter((c) => c.active),
);
const playingCourts = computed(
  () =>
    new Set(
      (data.value?.bookings || [])
        .filter((b) => b.status === 'CHECKED_IN')
        .map((b) => b.courtId),
    ).size,
);
const activeMaintenance = computed(() =>
  (data.value?.maintenance || []).filter((m) =>
    ['SCHEDULED', 'IN_PROGRESS', 'PENDING_APPROVAL'].includes(m.status),
  ),
);
const income = computed(() => [
  {
    label: 'Tiền mặt',
    amount: data.value?.myStats?.bookingCash || 0,
    color: '#bb8448',
  },
  {
    label: 'Chuyển khoản',
    amount: data.value?.myStats?.bookingBankTransfer || 0,
    color: '#746b9a',
  },
  {
    label: 'Daily Visitor',
    amount: data.value?.myStats?.dailyVisitorCash || 0,
    color: '#73968a',
  },
]);
const incomeTotal = computed(() =>
  income.value.reduce((sum, item) => sum + Number(item.amount), 0),
);
const incomeMax = computed(() =>
  Math.max(1, ...income.value.map((item) => Number(item.amount))),
);
const courtActivity = computed(() =>
  (data.value?.courts || [])
    .map((court) => ({
      ...court,
      count: (data.value?.bookings || []).filter(
        (b) =>
          b.courtId === court.id &&
          !['CANCELLED', 'NO_SHOW'].includes(b.status),
      ).length,
      playing: (data.value?.bookings || []).some(
        (b) => b.courtId === court.id && b.status === 'CHECKED_IN',
      ),
    }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
    .slice(0, 6),
);
const courtMax = computed(() =>
  Math.max(1, ...courtActivity.value.map((c) => c.count)),
);
const occupancy = computed(() =>
  activeCourts.value.length
    ? Math.round((playingCourts.value / activeCourts.value.length) * 100)
    : 0,
);
const slots = Array.from({ length: 48 }, (_, i) => ({
  start: i * 30,
  label: `${String(Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`,
}));
function minutes(t) {
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}
function cell(court, slot) {
  if (!court.active) return { kind: 'inactive', title: 'Sân ngừng hoạt động' };
  const start = new Date(`${data.value.date}T${slot.label}:00+07:00`).getTime();
  const end = start + 30 * 60000;
  const m = data.value.maintenance.find(
    (m) =>
      m.courtId === court.id &&
      ['SCHEDULED', 'IN_PROGRESS'].includes(m.status) &&
      new Date(`${m.startTime}+07:00`).getTime() < end &&
      (!m.endTime || new Date(`${m.endTime}+07:00`).getTime() > start),
  );
  if (m) return { kind: 'maintenance', title: `Bảo trì: ${m.reason}` };
  const b = data.value.bookings.find(
    (b) =>
      b.courtId === court.id &&
      !['CANCELLED', 'NO_SHOW'].includes(b.status) &&
      minutes(b.startTime) < slot.start + 30 &&
      minutes(b.endTime) > slot.start,
  );
  if (b)
    return {
      kind:
        b.status === 'CHECKED_IN'
          ? 'checked'
          : b.status === 'COMPLETED'
            ? 'done'
            : 'booked',
      title: `#${b.id} · ${b.customerName} · ${time(b.startTime)}–${time(b.endTime)}`,
      booking: b,
    };
  return {
    kind: 'free',
    title: 'Chưa có booking hoặc bảo trì trong khung giờ này',
  };
}
function handle(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Yêu cầu hết thời gian. Tải lại dữ liệu để kiểm tra kết quả.'
    : e.message;
}
async function load() {
  if (saving.value || !date.value) return;
  controller?.abort();
  const current = new AbortController();
  controller = current;
  loading.value = true;
  error.value = '';
  const timeout = setTimeout(() => current.abort(), 15000);
  try {
    const result = await getAdminData(
      `/api/staff/dashboard?date=${date.value}`,
      props.auth.accessToken,
      current.signal,
    );
    if (
      !Array.isArray(result.bookings) ||
      !Array.isArray(result.courts) ||
      !Array.isArray(result.maintenance) ||
      !result.summary
    )
      throw Error('Dữ liệu dashboard không hợp lệ.');
    if (!disposed && controller === current) data.value = result;
  } catch (e) {
    if (!disposed && controller === current) error.value = handle(e);
  } finally {
    clearTimeout(timeout);
    if (controller === current) loading.value = false;
  }
}
const paymentDialog = ref(null);
const paymentBooking = ref(null);
const paymentReceipt = ref(null);
const paymentLoading = ref(false);
const paymentError = ref('');
const paymentMethod = ref('CASH');
const paymentReceived = ref('');
const paymentTotal = computed(
  () =>
    paymentReceipt.value?.totalAmount ?? paymentBooking.value?.totalAmount ?? 0,
);
const paymentChange = computed(() =>
  Math.max(0, Number(paymentReceived.value || 0) - paymentTotal.value),
);
let paymentController;
function closePayment() {
  if (saving.value) return;
  paymentController?.abort();
  paymentDialog.value?.close();
  paymentBooking.value = null;
}
async function manageBooking(b, action = '') {
  if (saving.value || paymentLoading.value) return;
  if (action !== 'settle') {
    await open(b);
    return;
  }
  close();
  paymentBooking.value = b;
  paymentReceipt.value = null;
  paymentError.value = '';
  paymentMethod.value = 'CASH';
  paymentReceived.value = '';
  paymentLoading.value = true;
  await nextTick();
  paymentDialog.value?.showModal();
  paymentController = new AbortController();
  const current = paymentController;
  const timeout = setTimeout(() => current.abort(), 15000);
  try {
    const bookings = await getAdminData(
      '/api/bookings',
      props.auth.accessToken,
      current.signal,
    );
    const fresh = bookings.find((item) => Number(item.id) === Number(b.id));
    if (!fresh) throw Error('Không tìm thấy booking này.');
    if (current.signal.aborted || disposed) return;
    paymentBooking.value = { ...b, ...fresh };
    const receipt = await getAdminData(
      `/api/bookings/${b.id}/receipt`,
      props.auth.accessToken,
      current.signal,
    );
    if (current.signal.aborted || disposed) return;
    paymentReceipt.value = receipt;
    paymentReceived.value = String(receipt.totalAmount ?? fresh.totalAmount);
  } catch (e) {
    if (!current.signal.aborted && !disposed) paymentError.value = handle(e);
    else if (!disposed && paymentBooking.value)
      paymentError.value =
        'Tải hóa đơn hết thời gian. Đóng và mở lại để kiểm tra.';
  } finally {
    clearTimeout(timeout);
    paymentLoading.value = false;
  }
}
function choosePayment(method) {
  paymentMethod.value = method;
  paymentReceived.value = String(paymentTotal.value);
}
async function receivePayment() {
  if (
    saving.value ||
    paymentLoading.value ||
    !paymentReceipt.value ||
    paymentBooking.value?.paidAt ||
    paymentBooking.value?.status !== 'COMPLETED'
  )
    return;
  let body;
  try {
    body = settlementBody(
      paymentMethod.value,
      paymentReceived.value,
      paymentTotal.value,
    );
  } catch (e) {
    paymentError.value = e.message;
    return;
  }
  saving.value = true;
  paymentError.value = '';
  paymentController = new AbortController();
  const timeout = setTimeout(() => paymentController.abort(), 15000);
  try {
    const result = await getAdminData(
      `/api/bookings/${paymentBooking.value.id}/settle`,
      props.auth.accessToken,
      paymentController.signal,
      { method: 'POST', body: JSON.stringify(body) },
    );
    if (disposed) return;
    paymentBooking.value = { ...paymentBooking.value, ...result };
    paymentReceipt.value = null;
    success.value = `Đã nhận tiền booking #${paymentBooking.value.id}.`;
    saving.value = false;
    closePayment();
    await load();
  } catch (e) {
    if (!disposed)
      paymentError.value =
        handle(e) +
        ' Hãy đóng và mở lại hóa đơn để kiểm tra trước khi thu tiếp.';
  } finally {
    clearTimeout(timeout);
    saving.value = false;
  }
}
async function open(b, kind = 'detail') {
  if (saving.value) return;
  if (kind === 'detail') {
    section.value = 'schedule';
    await nextTick();
    await bookingWorkspace.value?.openBookingById(b.id);
    return;
  }
  selected.value = b;
  mode.value = kind;
  await nextTick();
  dialog.value?.showModal();
}
function close() {
  if (saving.value) return;
  dialog.value?.close();
  selected.value = null;
}
function cancelHint(booking) {
  if (!booking?.walkIn)
    return 'Chỉ hủy booking của khách tại quầy bằng thao tác này.';
  if (!['PENDING', 'NO_SHOW_PENDING'].includes(booking.status))
    return 'Booking đã check-in, hoàn tất, hủy hoặc NO_SHOW không thể hủy.';
  const start = new Date(
    `${booking.date}T${booking.startTime}+07:00`,
  ).getTime();
  const now = data.value?.serverTime
    ? new Date(`${data.value.serverTime}+07:00`).getTime()
    : Date.now();
  if (!Number.isFinite(start) || start - now <= 30 * 60000)
    return 'Không được hủy trong 30 phút trước giờ chơi hoặc sau khi đã bắt đầu.';
  return '';
}
function canCancel(booking) {
  return Boolean(booking) && !cancelHint(booking);
}
async function cancelBooking() {
  if (saving.value || loading.value || !canCancel(selected.value)) return;
  const id = selected.value.id;
  saving.value = true;
  error.value = '';
  success.value = '';
  actionController = new AbortController();
  const timeout = setTimeout(() => actionController.abort(), 15000);
  try {
    await getAdminData(
      `/api/bookings/${id}/cancel-walk-in`,
      props.auth.accessToken,
      actionController.signal,
      { method: 'DELETE' },
    );
    if (disposed) return;
    saving.value = false;
    close();
    success.value = `Đã hủy booking tại quầy #${id}.`;
    await load();
  } catch (e) {
    if (!disposed) {
      const actionError = handle(e);
      saving.value = false;
      close();
      await load();
      error.value = actionError + (error.value ? ' ' + error.value : '');
    }
  } finally {
    clearTimeout(timeout);
    saving.value = false;
  }
}
async function checkIn() {
  if (saving.value || !selected.value?.canCheckIn) return;
  const id = selected.value.id;
  saving.value = true;
  error.value = '';
  success.value = '';
  actionController = new AbortController();
  const timeout = setTimeout(() => actionController.abort(), 15000);
  try {
    await getAdminData(
      `/api/bookings/${id}/check-in`,
      props.auth.accessToken,
      actionController.signal,
      { method: 'POST' },
    );
    if (disposed) return;
    saving.value = false;
    close();
    success.value = `Đã check-in booking #${id}.`;
    await load();
  } catch (e) {
    if (!disposed) {
      const actionError = handle(e);
      saving.value = false;
      close();
      await load();
      error.value = actionError + (error.value ? ' ' + error.value : '');
    }
  } finally {
    clearTimeout(timeout);
    saving.value = false;
  }
}
watch(date, () => {
  data.value = null;
  page.value = 1;
  success.value = '';
  load();
});
watch([query, status, counterOnly], () => {
  page.value = 1;
});
watch(pages, (n) => {
  page.value = Math.min(page.value, n);
});
async function openForm(kind) {
  if (kind === 'booking') {
    section.value = 'schedule';
    counterWork.value = { kind: 'create' };
    return;
  }
  if (saving.value || loading.value || !data.value) return;
  formKind.value = kind;
  formError.value = '';
  form.value =
    kind === 'booking'
      ? {
          walkInName: '',
          walkInPhone: '',
          courtIds: [],
          bookingDate: date.value,
          startTime: '18:00',
          endTime: '19:00',
        }
      : {
          courtId: '',
          type: 'SCHEDULED',
          reason: '',
          startTime: `${date.value}T18:00`,
          endTime: `${date.value}T19:00`,
          maintenanceCost: 0,
          emergencyConfirmed: false,
        };
  actionDialog.value?.showModal();
}
function closeForm() {
  if (saving.value) return;
  actionDialog.value?.close();
  formKind.value = '';
  form.value = {};
}
async function submitForm() {
  if (saving.value) return;
  formError.value = '';
  const kind = formKind.value;
  let path, body;
  if (kind === 'booking') {
    const phone = form.value.walkInPhone
      .replace(/\s+/g, '')
      .replace(/^\+84/, '0');
    if (!form.value.walkInName.trim() || !/^0[35789]\d{8}$/.test(phone)) {
      formError.value = 'Nhập họ tên và số điện thoại Việt Nam hợp lệ.';
      return;
    }
    if (
      !form.value.courtIds.length ||
      !form.value.bookingDate ||
      !form.value.startTime ||
      !form.value.endTime ||
      minutes(form.value.endTime) - minutes(form.value.startTime) < 60 ||
      minutes(form.value.startTime) % 30 ||
      minutes(form.value.endTime) % 30
    ) {
      formError.value = 'Chọn sân và khung giờ :00 / :30, tối thiểu 1 giờ.';
      return;
    }
    path = '/api/bookings/walk-in';
    body = {
      walkInName: form.value.walkInName.trim(),
      walkInPhone: phone,
      courtIds: [...form.value.courtIds],
      bookingDate: form.value.bookingDate,
      startTime: form.value.startTime,
      endTime: form.value.endTime,
      quantityTubes: 0,
    };
  } else if (kind === 'maintenance') {
    if (
      !form.value.courtId ||
      !form.value.reason.trim() ||
      !form.value.startTime ||
      (form.value.type === 'SCHEDULED' && !form.value.endTime) ||
      (form.value.endTime && form.value.endTime <= form.value.startTime) ||
      !Number.isSafeInteger(Number(form.value.maintenanceCost)) ||
      Number(form.value.maintenanceCost) < 0
    ) {
      formError.value =
        'Chọn sân, lý do, thời gian hợp lệ và chi phí nguyên không âm.';
      return;
    }
    if (form.value.type === 'EMERGENCY' && !form.value.emergencyConfirmed) {
      formError.value =
        'Xác nhận ảnh hưởng booking trước khi báo sự cố đột xuất.';
      return;
    }
    path = '/api/court-maintenances';
    body = {
      courtId: Number(form.value.courtId),
      type: form.value.type,
      reason: form.value.reason.trim(),
      startTime: form.value.startTime + ':00',
      endTime: form.value.endTime ? form.value.endTime + ':00' : null,
      maintenanceCost: Number(form.value.maintenanceCost),
    };
  } else return;
  saving.value = true;
  actionController = new AbortController();
  const timeout = setTimeout(() => actionController.abort(), 15000);
  try {
    const response = await getAdminData(
      path,
      props.auth.accessToken,
      actionController.signal,
      { method: 'POST', body: JSON.stringify(body) },
    );
    if (disposed) return;
    if (kind === 'booking' && !Array.isArray(response))
      throw Error(
        'Phản hồi booking không hợp lệ. Tải lại để kiểm tra trước khi tạo lại.',
      );
    saving.value = false;
    if (kind === 'booking' && date.value !== body.bookingDate) {
      date.value = body.bookingDate;
      await nextTick();
    }
    closeForm();
    success.value =
      kind === 'booking'
        ? 'Đã tạo booking tại quầy.'
        : 'Đã tạo lịch bảo trì / báo sự cố.';
    await load();
  } catch (e) {
    if (!disposed) formError.value = handle(e);
  } finally {
    clearTimeout(timeout);
    saving.value = false;
  }
}
onMounted(() => {
  load();
  timer = setInterval(() => {
    if (!saving.value && !loading.value && !selected.value && !formKind.value)
      load();
  }, 60000);
});
onUnmounted(() => {
  disposed = true;
  paymentController?.abort();
  controller?.abort();
  actionController?.abort();
  clearInterval(timer);
});
</script>

<template>
  <div class="staff-layout">
    <aside class="staff-sidebar">
      <div class="workspace">
        <span>C</span>
        <div>
          <strong>Carrot Staff</strong><small>Không gian nhân viên</small>
        </div>
      </div>
      <p class="nav-label">ĐIỀU HÀNH</p>
      <nav aria-label="Chức năng nhân viên">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          :class="{ selected: section === tab.id }"
          :aria-current="section === tab.id ? 'page' : undefined"
          @click="section = tab.id"
        >
          <span>{{ tab.icon }}</span
          >{{ tab.label }}
        </button>
      </nav>
      <div class="sidebar-note">
        <small>NHÂN VIÊN ĐANG ĐĂNG NHẬP</small
        ><strong>{{ auth.user.fullName || auth.user.phone }}</strong>
        <p>Thống kê cá nhân theo ngày đã chọn.</p>
      </div>
    </aside>
    <section class="staff-dashboard">
      <header class="topbar">
        <div>
          <p class="eyebrow">CARROT · BẢNG ĐIỀU HÀNH NHÂN VIÊN</p>
          <h1>Chào {{ auth.user.fullName || auth.user.phone }}</h1>
          <p>
            {{ tabs.find((tab) => tab.id === section)?.label }} · Theo ngày đã
            chọn
          </p>
        </div>
        <div class="controls">
          <label
            >Ngày xem<input
              v-model="date"
              type="date"
              required
              :disabled="saving" /></label
          ><button :disabled="loading || saving || !date" @click="load">
            {{ loading ? 'Đang tải…' : '↻ Tải lại' }}
          </button>
        </div>
      </header>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <p v-if="success" class="notice success" role="status">{{ success }}</p>
      <p v-if="loading && !data" class="empty">Đang tải bảng điều hành…</p>
      <template v-if="data">
        <p class="updated">
          Dữ liệu ngày {{ data.date }} · Cập nhật
          {{ data.serverTime.replace('T', ' ').slice(0, 19) }} (giờ Việt Nam)
        </p>
        <section v-if="section === 'overview'" class="overview-space">
          <header class="overview-welcome">
            <div>
              <p>HÔM NAY TRÊN SÂN / {{ data.date }}</p>
              <h2>
                Một ngày chơi hay.<br /><span>Mọi việc trong tầm tay.</span>
              </h2>
              <small
                >Theo dõi lịch sân, khoản đã thu và công việc bảo trì.</small
              >
            </div>
            <div class="overview-mark" aria-hidden="true">
              <span>CARROT</span><strong>ON<br />COURT.</strong
              ><i>STAFF WORKSPACE ↗</i>
            </div>
          </header>
          <div class="overview-metrics">
            <article>
              <span class="metric-symbol" aria-hidden="true">▤</span>
              <p>Booking trong ngày</p>
              <strong>{{ data.summary.total }}</strong
              ><small>{{ data.summary.waiting }} lượt chờ nhận sân</small>
            </article>
            <article>
              <span class="metric-symbol violet" aria-hidden="true">🏸</span>
              <p>Sân đang chơi</p>
              <strong
                >{{ playingCourts }}<em>/ {{ activeCourts.length }}</em></strong
              ><small>Sân đang có booking check-in</small>
            </article>
            <article>
              <span class="metric-symbol sage" aria-hidden="true">₫</span>
              <p>Tiền tôi đã thu</p>
              <strong class="money-metric">{{ money(incomeTotal) }}</strong
              ><small>Theo ngày ghi nhận thanh toán</small>
            </article>
            <article>
              <span class="metric-symbol rose" aria-hidden="true">⚒</span>
              <p>Bảo trì cần theo dõi</p>
              <strong>{{ activeMaintenance.length }}</strong
              ><small>Lịch giao với ngày đã chọn</small>
            </article>
          </div>
          <div class="overview-charts">
            <article class="overview-card">
              <header>
                <div>
                  <p>COURT ACTIVITY</p>
                  <h3>Nhịp chơi trên sân</h3>
                </div>
                <span class="card-pill">{{ occupancy }}% đang chơi</span>
              </header>
              <div class="court-bars">
                <div v-for="court in courtActivity" :key="court.id">
                  <div class="bar-label">
                    <strong>{{ court.name }}</strong
                    ><small
                      >{{ court.count }} booking
                      <i v-if="court.playing">· Đang chơi</i></small
                    >
                  </div>
                  <div class="bar-track">
                    <span
                      :style="{ width: (court.count / courtMax) * 100 + '%' }"
                    ></span>
                  </div>
                </div>
                <p v-if="!courtActivity.length" class="overview-empty">
                  Chưa có dữ liệu sân.
                </p>
              </div>
              <footer>
                6 sân có nhiều booking nhất · Không tính lượt hủy / không đến
              </footer>
            </article>
            <article class="overview-card">
              <header>
                <div>
                  <p>COLLECTION / {{ data.date }}</p>
                  <h3>Khoản thu của tôi</h3>
                </div>
                <strong class="income-total">{{ money(incomeTotal) }}</strong>
              </header>
              <div
                class="income-chart"
                role="img"
                :aria-label="
                  income
                    .map((item) => item.label + ': ' + money(item.amount))
                    .join(', ')
                "
              >
                <div
                  v-for="item in income"
                  :key="item.label"
                  class="income-column"
                >
                  <strong>{{ money(item.amount) }}</strong>
                  <div class="income-column-track">
                    <span
                      :style="{
                        height: (item.amount / incomeMax) * 100 + '%',
                        background: item.color,
                      }"
                    ></span>
                  </div>
                  <small>{{ item.label }}</small>
                </div>
              </div>
              <footer>
                Tiền booking và Daily Visitor đã thu · Chưa gồm hóa đơn bán cầu
                riêng
              </footer>
            </article>
          </div>
          <div class="overview-lower">
            <article class="overview-card recent-card">
              <header>
                <div>
                  <p>RECENT BOOKINGS</p>
                  <h3>5 booking mới nhất</h3>
                </div>
                <button class="overview-link" @click="section = 'schedule'">
                  Xem tất cả ↗
                </button>
              </header>
              <div class="scroll">
                <table class="overview-bookings">
                  <thead>
                    <tr>
                      <th>Booking / Khách</th>
                      <th>Sân / Giờ</th>
                      <th>Trạng thái</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="b in recentBookings" :key="b.id">
                      <td>
                        <strong>{{ b.customerName }}</strong
                        ><small
                          >#{{ b.id }} ·
                          {{ b.walkIn ? 'Tại quầy' : 'Tài khoản' }}</small
                        >
                      </td>
                      <td>
                        <strong>{{ b.courtName }}</strong
                        ><small
                          >{{ time(b.startTime) }} –
                          {{ time(b.endTime) }}</small
                        >
                      </td>
                      <td>
                        <span class="badge" :class="b.status">{{
                          labels[b.status] || b.status
                        }}</span>
                      </td>
                      <td>
                        <button
                          class="overview-detail"
                          :disabled="saving || loading"
                          @click="open(b)"
                        >
                          Chi tiết ↗
                        </button>
                      </td>
                    </tr>
                    <tr v-if="!recentBookings.length">
                      <td colspan="4" class="overview-empty">
                        Chưa có booking trong ngày này.
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <footer>
                Booking của ngày {{ data.date }} · Mới nhất theo mã booking
              </footer>
            </article>
            <article class="overview-card maintenance-preview">
              <header>
                <div>
                  <p>MAINTENANCE</p>
                  <h3>Theo dõi bảo trì</h3>
                </div>
                <button class="overview-link" @click="section = 'maintenance'">
                  Mở ↗
                </button>
              </header>
              <div class="maintenance-preview-list">
                <article v-for="m in activeMaintenance.slice(0, 3)" :key="m.id">
                  <span aria-hidden="true">⚒</span>
                  <div>
                    <strong>{{
                      data.courts.find((c) => c.id === m.courtId)?.name ||
                      '#' + m.courtId
                    }}</strong>
                    <p>{{ m.reason }}</p>
                    <small
                      >{{
                        m.status === 'IN_PROGRESS'
                          ? 'Đang sửa chữa'
                          : m.status === 'PENDING_APPROVAL'
                            ? 'Chờ duyệt'
                            : 'Đã lên lịch'
                      }}
                      · {{ m.startTime?.replace('T', ' ').slice(0, 16) }}</small
                    >
                  </div>
                </article>
                <div v-if="!activeMaintenance.length" class="maintenance-clear">
                  <span aria-hidden="true">✓</span
                  ><strong>Không có lịch cần theo dõi</strong>
                  <p>
                    Chưa ghi nhận bảo trì đang chờ hoặc đang thực hiện trong
                    ngày.
                  </p>
                </div>
              </div>
            </article>
          </div>
        </section>
        <StaffBookingsView
          v-if="['schedule', 'counter'].includes(section)"
          ref="bookingWorkspace"
          :refresh-key="counterRefresh"
          :auth="auth"
          @session-expired="emit('session-expired')"
        />
        <BookingCounterActions
          :auth="auth"
          :work="counterWork"
          @close="counterWork = null"
          @done="counterDone"
          @session-expired="emit('session-expired')"
        />
        <section v-if="section === 'maintenance'" class="panel">
          <header>
            <div>
              <h2>Bảo trì / sửa chữa</h2>
              <p>
                Các lịch giao với ngày {{ data.date }}. STAFF báo sự cố, Admin
                duyệt hoàn tất.
              </p>
            </div>
            <button
              class="primary"
              :disabled="loading || saving"
              @click="openForm('maintenance')"
            >
              + Báo bảo trì / sự cố
            </button>
          </header>
          <div class="scroll">
            <table>
              <thead>
                <tr>
                  <th>Sân</th>
                  <th>Lý do</th>
                  <th>Thời gian</th>
                  <th>Trạng thái</th>
                  <th>Người báo</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in data.maintenance" :key="m.id">
                  <td>
                    {{
                      data.courts.find((c) => c.id === m.courtId)?.name ||
                      '#' + m.courtId
                    }}
                  </td>
                  <td class="reason">{{ m.reason }}</td>
                  <td>
                    {{ m.startTime.replace('T', ' ')
                    }}<small
                      >Đến
                      {{
                        m.endTime?.replace('T', ' ') || 'Chưa xác định'
                      }}</small
                    >
                  </td>
                  <td>
                    {{
                      {
                        SCHEDULED: 'Đã lên lịch',
                        IN_PROGRESS: 'Đang sửa chữa',
                        COMPLETED: 'Hoàn tất',
                        CANCELLED: 'Đã hủy',
                      }[m.status] || m.status
                    }}
                  </td>
                  <td>
                    {{ m.createdByName }}<small>#{{ m.createdBy }}</small>
                  </td>
                  <td>
                    <div class="maintenance-row-actions">
                      <button
                        :disabled="
                          loading ||
                          saving ||
                          ![
                            'SCHEDULED',
                            'IN_PROGRESS',
                            'PENDING_APPROVAL',
                          ].includes(m.status)
                        "
                        @click="
                          maintenanceActions.open(m.id, m.courtId, 'edit')
                        "
                      >
                        Sửa</button
                      ><button
                        class="danger"
                        :disabled="
                          loading ||
                          saving ||
                          ![
                            'SCHEDULED',
                            'IN_PROGRESS',
                            'PENDING_APPROVAL',
                          ].includes(m.status)
                        "
                        @click="
                          maintenanceActions.open(m.id, m.courtId, 'cancel')
                        "
                      >
                        Hủy lịch
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!data.maintenance.length">
                  <td colspan="6" class="empty">
                    Không có lịch bảo trì giao với ngày này.
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <section v-if="section === 'stats' && data.myStats" class="panel">
          <header>
            <div>
              <h2>Thống kê của {{ data.myStats.staffName }}</h2>
              <p>
                Ngày {{ data.date }} · #{{ data.myStats.staffId }} · Theo thời
                điểm thực hiện thao tác.
              </p>
            </div>
          </header>
          <div class="metrics">
            <article>
              <span>Booking đã check-in</span
              ><strong>{{ data.myStats.checkedInBookings }}</strong>
            </article>
            <article>
              <span>Booking chốt hoàn tất</span
              ><strong>{{ data.myStats.completedBookings }}</strong>
            </article>
            <article>
              <span>Đăng ký vãng lai check-in</span
              ><strong>{{ data.myStats.dailyCheckIns }}</strong>
            </article>
            <article>
              <span>Báo bảo trì / sự cố</span
              ><strong>{{ data.myStats.maintenanceReports }}</strong>
            </article>
          </div>
          <div class="scroll">
            <table>
              <thead>
                <tr>
                  <th>Khoản ghi nhận</th>
                  <th>Số tiền</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Tiền mặt booking đã chốt bởi tôi</td>
                  <td>{{ money(data.myStats.bookingCash) }}</td>
                </tr>
                <tr>
                  <td>Chuyển khoản booking đã chốt bởi tôi</td>
                  <td>{{ money(data.myStats.bookingBankTransfer) }}</td>
                </tr>
                <tr>
                  <td>Tiền đánh vãng lai tôi đã thu</td>
                  <td>{{ money(data.myStats.dailyVisitorCash) }}</td>
                </tr>
                <tr>
                  <td><strong>Tổng ghi nhận</strong></td>
                  <td>
                    <strong>{{ money(data.myStats.totalCollected) }}</strong>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="hint">
            Tiền booking tính theo hóa đơn COMPLETED và nhân viên chốt hóa đơn;
            tiền vãng lai theo paidBy/paidAt. Chưa gồm bán hàng tại quầy và
            không thay thế đối soát tiền thực tế.
          </p>
        </section>
      </template>
      <MaintenanceActions
        ref="maintenanceActions"
        :auth="auth"
        :courts="data?.courts || []"
        @changed="maintenanceChanged"
        @session-expired="emit('session-expired')"
      />
      <dialog ref="actionDialog" @cancel.prevent="closeForm">
        <form @submit.prevent="submitForm">
          <header>
            <h2>
              {{
                formKind === 'booking'
                  ? 'Tạo booking tại quầy'
                  : 'Báo bảo trì / sửa chữa'
              }}
            </h2>
            <button
              type="button"
              :disabled="saving"
              aria-label="Đóng"
              @click="closeForm"
            >
              ✕
            </button>
          </header>
          <template v-if="formKind === 'booking'">
            <label
              >Họ tên khách<input
                v-model="form.walkInName"
                required
                maxlength="255"
                :disabled="saving"
            /></label>
            <label
              >Số điện thoại<input
                v-model="form.walkInPhone"
                type="tel"
                required
                maxlength="30"
                :disabled="saving"
            /></label>
            <fieldset :disabled="saving">
              <legend>Chọn sân (có thể chọn nhiều)</legend>
              <label
                v-for="court in data?.courts.filter((c) => c.active)"
                :key="court.id"
                class="check-label"
                ><input
                  v-model="form.courtIds"
                  type="checkbox"
                  :value="court.id"
                />{{ court.name }}</label
              >
            </fieldset>
            <label
              >Ngày chơi<input
                v-model="form.bookingDate"
                type="date"
                :min="today()"
                required
                :disabled="saving"
            /></label>
            <div class="form-row">
              <label
                >Bắt đầu<select v-model="form.startTime" :disabled="saving">
                  <option v-for="slot in slots" :key="slot.label">
                    {{ slot.label }}
                  </option>
                </select></label
              ><label
                >Kết thúc<select v-model="form.endTime" :disabled="saving">
                  <option v-for="slot in slots" :key="slot.label">
                    {{ slot.label }}
                  </option>
                </select></label
              >
            </div>
            <p class="hint">
              Tối thiểu 1 giờ. Backend kiểm tra giá, sân trùng lịch, bảo trì và
              giới hạn ngày đặt. Khách tại quầy được lưu theo số điện thoại,
              không tạo tài khoản người dùng.
            </p>
          </template>
          <template v-else-if="formKind === 'maintenance'">
            <label
              >Sân<select v-model="form.courtId" required :disabled="saving">
                <option value="">Chọn sân</option>
                <option
                  v-for="court in data?.courts"
                  :key="court.id"
                  :value="court.id"
                >
                  {{ court.name }}
                </option>
              </select></label
            >
            <label
              >Loại<select v-model="form.type" :disabled="saving">
                <option value="SCHEDULED">Bảo trì có kế hoạch</option>
                <option value="EMERGENCY">Sự cố đột xuất</option>
              </select></label
            >
            <label
              >Lý do<textarea
                v-model="form.reason"
                required
                maxlength="1000"
                :disabled="saving"
              ></textarea>
            </label>
            <label
              >Bắt đầu<input
                v-model="form.startTime"
                type="datetime-local"
                required
                :disabled="saving"
            /></label>
            <label
              >Kết thúc dự kiến<input
                v-model="form.endTime"
                type="datetime-local"
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
            <label v-if="form.type === 'EMERGENCY'" class="check-label"
              ><input
                v-model="form.emergencyConfirmed"
                type="checkbox"
                required
                :disabled="saving"
              />
              Tôi xác nhận: sự cố có thể hủy booking chưa check-in bị ảnh hưởng
              theo quy tắc backend.</label
            >
          </template>
          <p v-if="formError" class="notice error" role="alert">
            {{ formError }}
          </p>
          <footer>
            <button type="button" :disabled="saving" @click="closeForm">
              Hủy</button
            ><button class="primary" :disabled="saving">
              {{
                saving
                  ? 'Đang lưu…'
                  : formKind === 'booking'
                    ? 'Tạo booking'
                    : 'Gửi báo bảo trì'
              }}
            </button>
          </footer>
        </form>
      </dialog>
      <dialog ref="dialog" @cancel.prevent="close">
        <template v-if="selected"
          ><header>
            <h2>
              {{
                mode === 'cancel'
                  ? 'Xác nhận hủy booking'
                  : mode === 'checkin'
                    ? 'Xác nhận khách đến sân'
                    : 'Chi tiết booking'
              }}
              #{{ selected.id }}
            </h2>
            <button :disabled="saving" aria-label="Đóng" @click="close">
              ✕
            </button>
          </header>
          <dl>
            <dt>Khách hàng</dt>
            <dd>{{ selected.customerName }}</dd>
            <dt>Điện thoại</dt>
            <dd>{{ selected.phone || '—' }}</dd>
            <dt>Sân</dt>
            <dd>{{ selected.courtName }}</dd>
            <dt>Ngày / giờ chơi</dt>
            <dd>
              {{ selected.date }} · {{ time(selected.startTime) }}–{{
                time(selected.endTime)
              }}
            </dd>
            <dt>Trạng thái</dt>
            <dd>{{ labels[selected.status] || selected.status }}</dd>
            <dt>Giá trị booking</dt>
            <dd>{{ money(selected.totalAmount) }}</dd>
            <dt>Check-in lúc</dt>
            <dd>
              {{ selected.checkedInAt?.replace('T', ' ') || 'Chưa check-in' }}
            </dd>
            <dt>Nhân viên check-in</dt>
            <dd>
              {{ selected.checkedInBy ? '#' + selected.checkedInBy : '—' }}
            </dd>
          </dl>
          <p class="hint">
            {{ selected.checkInHint }}. Giá trị booking không phải số tiền chưa
            thanh toán.
          </p>
          <p v-if="mode === 'checkin'">
            Xác nhận khách đã có mặt? Hệ thống ghi nhận tài khoản nhân viên hiện
            tại.
          </p>
          <p v-if="mode === 'cancel'" class="cancel-warning">
            Bạn xác nhận hủy booking này? Lịch sân sẽ được cập nhật sau khi hủy.
            Thao tác này không tự hoàn tiền.
          </p>
          <p v-if="selected.walkIn && cancelHint(selected)" class="hint">
            {{ cancelHint(selected) }}
          </p>
          <footer>
            <button
              v-if="selected.status === 'COMPLETED'"
              class="primary"
              :disabled="saving"
              @click="manageBooking(selected, 'settle')"
            >
              Nhận tiền / Thanh toán
            </button>
            <button :disabled="saving" @click="close">Đóng</button
            ><button
              v-if="selected.canCheckIn && mode !== 'cancel'"
              class="primary"
              :disabled="saving || loading"
              @click="mode === 'checkin' ? checkIn() : (mode = 'checkin')"
            >
              {{
                saving
                  ? 'Đang xử lý…'
                  : mode === 'checkin'
                    ? 'Xác nhận check-in'
                    : 'Check-in'
              }}
            </button>
            <button
              v-if="selected.walkIn && mode !== 'checkin'"
              class="danger"
              :disabled="saving || loading || !canCancel(selected)"
              :title="cancelHint(selected)"
              @click="mode === 'cancel' ? cancelBooking() : (mode = 'cancel')"
            >
              {{
                saving
                  ? 'Đang xử lý…'
                  : mode === 'cancel'
                    ? 'Xác nhận hủy'
                    : 'Hủy booking'
              }}
            </button>
          </footer></template
        >
      </dialog>
    </section>
  </div>
  <dialog
    ref="paymentDialog"
    class="staff-payment"
    aria-labelledby="staff-payment-title"
    @cancel.prevent="closePayment"
  >
    <header>
      <div>
        <p class="payment-kicker">THU TIỀN TẠI QUẦY</p>
        <h2 id="staff-payment-title">Hóa đơn #{{ paymentBooking?.id }}</h2>
      </div>
      <button
        aria-label="Đóng thanh toán"
        :disabled="saving"
        @click="closePayment"
      >
        ✕
      </button>
    </header>
    <template v-if="paymentBooking">
      <div class="payment-context">
        <strong>{{
          paymentBooking.courtName || paymentBooking.court?.name
        }}</strong
        ><span>{{
          paymentBooking.customerName ||
          paymentBooking.visitor?.fullName ||
          paymentBooking.user?.fullName
        }}</span
        ><small
          >{{ paymentBooking.date || paymentBooking.bookingDate }} ·
          {{ time(paymentBooking.startTime) }} –
          {{ time(paymentBooking.endTime) }}</small
        >
      </div>
      <p v-if="paymentLoading" role="status">
        Đang kiểm tra booking và hóa đơn…
      </p>
      <p v-if="paymentError" class="notice error" role="alert">
        {{ paymentError }}
      </p>
      <template v-if="paymentReceipt && !paymentLoading">
        <div class="payment-total">
          <span>{{
            paymentBooking.paidAt ? 'ĐÃ THANH TOÁN' : 'TỔNG CẦN THU'
          }}</span
          ><strong>{{ money(paymentTotal) }}</strong>
        </div>
        <div class="payment-lines">
          <p>
            Tiền sân <strong>{{ money(paymentReceipt.courtAmount) }}</strong>
          </p>
          <p>
            Ống cầu / sản phẩm
            <strong>{{ money(paymentReceipt.shuttlecockAmount) }}</strong>
          </p>
          <p v-for="(item, index) in paymentReceipt.items || []" :key="index">
            {{ item.productName }}
            <span
              >{{ item.quantityTubes || 0 }} ống ·
              {{ money(item.totalAmount) }}</span
            >
          </p>
        </div>
        <p v-if="paymentBooking.paidAt" class="notice success">
          Đã thu bằng
          {{
            paymentBooking.paymentMethod === 'CASH'
              ? 'tiền mặt'
              : 'chuyển khoản'
          }}
          · {{ paymentBooking.paidAt.replace('T', ' ') }}
        </p>
        <p
          v-else-if="paymentBooking.status !== 'COMPLETED'"
          class="notice error"
        >
          Booking chưa hoàn tất, chưa thể nhận tiền.
        </p>
        <form v-else @submit.prevent="receivePayment">
          <fieldset class="staff-pay-methods" :disabled="saving">
            <legend>Phương thức nhận tiền</legend>
            <button
              type="button"
              :class="{ chosen: paymentMethod === 'CASH' }"
              :aria-pressed="paymentMethod === 'CASH'"
              @click="choosePayment('CASH')"
            >
              ₫ <strong>Tiền mặt</strong
              ><small>Nhận trực tiếp tại quầy</small></button
            ><button
              type="button"
              :class="{ chosen: paymentMethod === 'BANK_TRANSFER' }"
              :aria-pressed="paymentMethod === 'BANK_TRANSFER'"
              @click="choosePayment('BANK_TRANSFER')"
            >
              ▣ <strong>Chuyển khoản</strong
              ><small>Xác nhận tiền đã vào tài khoản</small>
            </button>
          </fieldset>
          <label
            >Số tiền thực tế đã nhận (đ)<input
              v-model="paymentReceived"
              type="number"
              :min="paymentTotal"
              step="1"
              required
              :disabled="saving"
          /></label>
          <p v-if="paymentMethod === 'CASH'" class="payment-change">
            Tiền trả khách <strong>{{ money(paymentChange) }}</strong>
          </p>
          <p v-else class="hint">
            Chỉ xác nhận khi đã kiểm tra nhận đủ tiền chuyển khoản.
          </p>
          <footer>
            <button type="button" :disabled="saving" @click="closePayment">
              Để sau</button
            ><button class="primary" :disabled="saving">
              {{ saving ? 'Đang xác nhận…' : 'Xác nhận đã nhận tiền' }}
            </button>
          </footer>
        </form>
      </template>
    </template>
  </dialog>
</template>

<style scoped>
.maintenance-row-actions {
  display: grid;
  grid-template-columns: 75px 95px;
  gap: 8px;
}
.danger {
  color: #a34d35;
  border-color: #e8c7bc;
  background: #fff8f4;
}
.cancel-warning {
  padding: 14px;
  border-radius: 8px;
  background: #fff0e9;
  color: #a45136;
  font-size: 13px;
}
.staff-layout {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  background: #f6f9f6;
  min-height: 75vh;
  color: #234732;
}
.staff-sidebar {
  background: white;
  border-right: 1px solid #e1eae4;
  padding: 30px 18px;
  display: flex;
  flex-direction: column;
}
.workspace {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 30px;
}
.workspace > span {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: #005b35;
  color: white;
  font-weight: 800;
}
.workspace small {
  display: block;
}
.nav-label {
  font-size: 10px;
  letter-spacing: 1.4px;
  color: #85968b;
}
.staff-sidebar nav {
  display: grid;
  gap: 7px;
}
.staff-sidebar nav button {
  display: flex;
  gap: 12px;
  align-items: center;
  text-align: left;
  border: 0;
  padding: 13px 12px;
  color: #6b7c70;
}
.staff-sidebar nav button span {
  font-size: 22px;
  width: 25px;
}
.staff-sidebar nav button.selected {
  background: #e9f5ee;
  color: #005b35;
  font-weight: 700;
}
.sidebar-note {
  margin-top: auto;
  padding-top: 50px;
}
.sidebar-note strong {
  display: block;
  margin: 12px 0;
}
.sidebar-note p {
  font-size: 12px;
  color: #7a8a80;
}
.staff-dashboard {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
}
.counter-tools,
.form-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin: 16px 0;
}
.form-row > label {
  flex: 1;
}
dialog form > label,
dialog .form-row label {
  display: grid;
  gap: 8px;
  margin: 16px 0;
  font-size: 13px;
}
.check-label {
  display: flex !important;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  font-size: 12px;
}
.check-label input {
  flex: none;
}
fieldset {
  border: 1px solid #dce7df;
  border-radius: 8px;
}
textarea {
  font: inherit;
  min-height: 90px;
  border: 1px solid #dce7df;
  border-radius: 8px;
  padding: 10px;
  resize: vertical;
}
.reason {
  white-space: normal;
  min-width: 170px;
  max-width: 300px;
}
.staff-sidebar button {
  font: inherit;
  font-size: 13px;
}
@media (max-width: 760px) {
  .staff-layout {
    display: block;
  }
  .staff-sidebar {
    padding: 14px;
    border-right: 0;
    border-bottom: 1px solid #e1eae4;
  }
  .workspace,
  .nav-label,
  .sidebar-note {
    display: none;
  }
  .staff-sidebar nav {
    display: flex;
    overflow-x: auto;
  }
  .staff-sidebar nav button {
    white-space: nowrap;
    padding: 8px 12px;
  }
}

.staff-dashboard {
  max-width: 1440px;
  margin: auto;
  padding: 32px 24px;
  color: #234732;
  background: #f6f9f6;
  min-height: 65vh;
}
.topbar,
header,
.controls,
footer,
.filters,
.legend {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.topbar,
header {
  justify-content: space-between;
}
h1 {
  font-size: 26px;
  margin: 6px 0;
}
h2 {
  font-size: 18px;
  margin: 0 0 8px;
}
p {
  line-height: 1.6;
}
header p,
.updated,
.hint,
small {
  color: #708276;
  font-size: 12px;
}
.eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #176c46;
}
.controls label {
  display: grid;
  gap: 6px;
  font-size: 12px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 20px 0;
}
.metrics article,
.panel {
  background: #fff;
  border: 1px solid #e1eae4;
  border-radius: 14px;
  padding: 20px;
}
.metrics span {
  font-size: 13px;
}
.metrics strong {
  display: block;
  font-size: 32px;
  color: #176c46;
  margin: 12px 0;
}
small {
  display: block;
  line-height: 1.7;
}
.panel {
  margin: 20px 0;
}
.legend span,
.badge {
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 11px;
}
.legend {
  margin: 12px 0;
}
.free {
  background: #eef8f0;
  color: #276a42;
}
.booked,
.PENDING {
  background: #fff2d5;
  color: #906222;
}
.checked,
.CHECKED_IN {
  background: #d8efe4;
  color: #176c46;
}
.done,
.COMPLETED {
  background: #e4edfc;
  color: #35558e;
}
.maintenance,
.NO_SHOW_PENDING {
  background: #ffe1d4;
  color: #99482e;
}
.inactive,
.CANCELLED,
.NO_SHOW {
  background: #e9eceb;
  color: #68766d;
}
.scroll {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 12px;
  white-space: nowrap;
}
th {
  background: #f1f6f2;
  color: #607768;
  padding: 12px;
}
td {
  padding: 14px 12px;
  border-bottom: 1px solid #edf2ee;
}
.schedule th:first-child {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 110px;
}
.schedule td {
  padding: 3px;
}
.schedule button {
  min-width: 52px;
  width: 100%;
  height: 38px;
  padding: 4px;
  font-size: 10px;
}
.schedule button:disabled {
  opacity: 1;
  cursor: default;
}
.filters {
  margin: 16px 0;
}
.filters input {
  flex: 1;
  min-width: 200px;
}
input,
select,
button {
  font: inherit;
  font-size: 12px;
  border: 1px solid #dce7df;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
  color: #245239;
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
  color: #fff;
  border-color: #176c46;
}
.row-actions {
  display: grid;
  grid-template-columns: 80px 95px 110px;
  gap: 8px;
}
footer {
  justify-content: flex-end;
  margin-top: 18px;
}
footer > span:first-child {
  margin-right: auto;
}
.notice {
  padding: 12px 16px;
  border-radius: 8px;
}
.error {
  background: #ffede7;
  color: #a44d30;
}
.success {
  background: #e7f5ed;
  color: #287749;
}
.empty {
  text-align: center;
  padding: 30px;
  color: #708276;
}
dialog {
  border: 0;
  border-radius: 16px;
  padding: 24px;
  width: min(580px, calc(100vw - 40px));
  max-height: 85vh;
  overflow: auto;
  color: #234732;
  box-sizing: border-box;
}
dialog::backdrop {
  background: #12302170;
}
dl {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 12px;
  font-size: 13px;
}
dt {
  color: #708276;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
input:focus,
select:focus,
button:focus-visible {
  outline: 2px solid #9fc899;
  outline-offset: 2px;
}
@media (max-width: 900px) {
  .metrics {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 600px) {
  .staff-dashboard {
    padding: 20px 12px;
  }
  .panel {
    padding: 14px;
  }
  .metrics {
    gap: 8px;
  }
  .metrics article {
    padding: 14px;
  }
  .filters select {
    width: 100%;
  }
  h1 {
    font-size: 22px;
  }
  dl {
    grid-template-columns: 110px 1fr;
  }
}
</style>

<style scoped>
.staff-payment {
  width: min(560px, calc(100vw - 32px));
  padding: 28px;
  border: 1px solid #e6ded5;
  border-radius: 24px;
  box-shadow: 0 28px 90px #24180d33;
}
.staff-payment::backdrop {
  background: #211b1766;
  backdrop-filter: blur(5px);
}
.payment-kicker {
  color: #ad6922;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 2px;
  margin: 0 0 6px;
}
.payment-context {
  display: grid;
  gap: 6px;
  margin: 20px 0;
  padding: 16px;
  background: #f7f4ef;
  border-radius: 14px;
}
.payment-context strong {
  font-size: 20px;
}
.payment-context small {
  color: #7b7169;
}
.payment-total {
  padding: 22px;
  background: #29231e;
  color: #fff;
  border-radius: 16px;
  display: grid;
  gap: 10px;
}
.payment-total span {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: #ddc5ab;
}
.payment-total strong {
  font-size: clamp(26px, 5vw, 36px);
}
.payment-lines p,
.payment-change {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.payment-lines {
  padding: 4px 2px;
  font-size: 13px;
}
.staff-pay-methods {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  border: 0;
  padding: 0;
  margin: 20px 0;
}
.staff-pay-methods legend {
  margin-bottom: 10px;
  font-weight: 700;
}
.staff-pay-methods button {
  display: grid;
  gap: 8px;
  padding: 16px 12px;
  text-align: left;
  border-radius: 14px;
  background: white;
}
.staff-pay-methods button.chosen {
  border: 2px solid #bf7d36;
  background: #fff7eb;
  color: #70491d;
}
.staff-pay-methods small {
  font-size: 11px;
}
.payment-change {
  padding: 14px;
  background: #f7f4ef;
  border-radius: 10px;
}
@media (max-width: 480px) {
  .staff-payment {
    padding: 18px;
  }
  .staff-pay-methods {
    grid-template-columns: 1fr;
  }
}
</style>

<style scoped>
.counter-intro {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 30px;
  border: 1px solid #e5dacd;
  background: linear-gradient(120deg, #fffaf1, #f1e9df);
  border-radius: 22px;
}
.counter-intro h2 {
  font-size: 30px;
  margin: 10px 0;
}
.counter-intro p {
  line-height: 1.7;
  color: #786653;
}
.counter-symbol {
  font-size: 64px;
}
.counter-action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 24px 0;
}
.counter-action-grid button {
  display: grid;
  gap: 16px;
  text-align: left;
  padding: 24px;
  border-radius: 18px;
  border: 1px solid #e5dacd;
  background: white;
  cursor: pointer;
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}
.counter-action-grid button:hover {
  transform: translateY(-4px);
  box-shadow: 0 14px 30px #3c271b12;
}
.counter-action-grid span {
  font-size: 10px;
  letter-spacing: 1.4px;
  color: #a5692e;
  font-weight: 800;
}
.counter-action-grid strong {
  font-size: 20px;
  color: #33271e;
}
.counter-action-grid small {
  line-height: 1.7;
  color: #82766a;
}
@media (max-width: 800px) {
  .counter-action-grid {
    grid-template-columns: 1fr;
  }
  .counter-symbol {
    display: none;
  }
}
</style>

<style scoped>
.overview-space {
  display: grid;
  gap: 22px;
}
.overview-welcome {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  overflow: hidden;
  background: linear-gradient(120deg, #29231d, #403123);
  padding: 34px 38px;
  border-radius: 22px;
  color: #fff8ed;
}
.overview-welcome p,
.overview-card header p {
  font-size: 9px;
  letter-spacing: 1.8px;
  font-weight: 800;
  margin: 0 0 12px;
  color: #c79c6b;
}
.overview-welcome h2 {
  font-size: clamp(25px, 3vw, 38px);
  line-height: 1.25;
  letter-spacing: -1px;
  margin: 0 0 16px;
}
.overview-welcome h2 span {
  color: #d9b88d;
}
.overview-welcome small {
  color: #bdb0a1;
  font-size: 12px;
}
.overview-mark {
  border-left: 1px solid #c99b6533;
  padding-left: 34px;
  min-width: 160px;
}
.overview-mark > span {
  font-size: 10px;
  letter-spacing: 4px;
  color: #d6b38a;
}
.overview-mark strong {
  display: block;
  font-size: 38px;
  line-height: 1.05;
  margin: 12px 0;
  letter-spacing: -1.5px;
}
.overview-mark i {
  font-size: 8px;
  font-style: normal;
  letter-spacing: 1.5px;
  color: #c69e71;
}
.overview-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}
.overview-metrics article {
  position: relative;
  padding: 22px;
  border: 1px solid #e9e0d4;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 4px 18px #40302004;
}
.metric-symbol {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  background: #fbf0df;
  color: #a77136;
  border-radius: 10px;
  font-size: 17px;
  margin-bottom: 18px;
}
.metric-symbol.violet {
  background: #f0edf8;
  color: #796b9b;
}
.metric-symbol.sage {
  background: #ecf4ed;
  color: #718a77;
}
.metric-symbol.rose {
  background: #faeee8;
  color: #ba7d69;
}
.overview-metrics p {
  font-size: 12px;
  color: #8f7b68;
  margin: 0 0 12px;
}
.overview-metrics strong {
  display: block;
  font-size: 32px;
  line-height: 1.2;
  color: #35291f;
  letter-spacing: -1px;
}
.overview-metrics em {
  font-size: 15px;
  font-style: normal;
  color: #ac9c8b;
  font-weight: 500;
}
.overview-metrics small {
  display: block;
  margin-top: 10px;
  font-size: 10px;
  color: #a49383;
}
.overview-metrics .money-metric {
  font-size: clamp(18px, 2vw, 28px);
  overflow-wrap: anywhere;
}
.overview-charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.overview-card {
  background: white;
  border: 1px solid #e9e0d4;
  border-radius: 18px;
  overflow: hidden;
  min-width: 0;
}
.overview-card > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 24px;
}
.overview-card header h3 {
  margin: 0;
  font-size: 18px;
  color: #3e3025;
  letter-spacing: -0.4px;
}
.overview-card header p {
  color: #a17e55;
  font-size: 8px;
  margin-bottom: 9px;
}
.card-pill {
  white-space: nowrap;
  font-size: 9px;
  color: #976f41;
  background: #fbf4e9;
  padding: 8px 10px;
  border-radius: 30px;
}
.court-bars {
  padding: 0 24px 22px;
  display: grid;
  gap: 16px;
}
.bar-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.bar-label strong {
  font-size: 12px;
  color: #665242;
}
.bar-label small {
  font-size: 10px;
  color: #a19181;
}
.bar-label i {
  font-style: normal;
  color: #788f71;
}
.bar-track {
  height: 8px;
  border-radius: 10px;
  background: #f6f0e8;
  overflow: hidden;
}
.bar-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #c99b65, #e7c99f);
  border-radius: 10px;
  transition: width 0.35s;
}
.overview-card > footer {
  display: block;
  border-top: 1px solid #f0e9df;
  padding: 15px 24px;
  color: #a08c79;
  font-size: 9px;
  line-height: 1.7;
}
.income-total {
  font-size: 18px;
  color: #9b713e;
  white-space: nowrap;
}
.income-chart {
  padding: 6px 24px 24px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 22px;
  align-items: end;
}
.income-column {
  display: grid;
  gap: 12px;
  text-align: center;
  min-width: 0;
}
.income-column > strong {
  font-size: 11px;
  color: #78614c;
  overflow-wrap: anywhere;
}
.income-column-track {
  height: 150px;
  position: relative;
  border-radius: 10px;
  background: repeating-linear-gradient(
    to top,
    #f8f4ee 0,
    #f8f4ee 1px,
    transparent 1px,
    transparent 37px
  );
}
.income-column-track span {
  display: block;
  position: absolute;
  bottom: 0;
  left: 20%;
  width: 60%;
  border-radius: 8px 8px 0 0;
  transition: height 0.4s;
}
.income-column small {
  font-size: 10px;
  color: #9a8674;
}
.overview-lower {
  display: grid;
  grid-template-columns: 1.65fr 1fr;
  gap: 20px;
}
.overview-link {
  border: 0;
  background: none;
  color: #a27440;
  padding: 8px 0;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}
.overview-bookings {
  min-width: 520px;
  width: 100%;
  border-collapse: collapse;
}
.overview-bookings th {
  background: #faf7f2;
  color: #a08c76;
  font-size: 9px;
  font-weight: 700;
  padding: 13px 20px;
}
.overview-bookings td {
  padding: 17px 20px;
  border-bottom: 1px solid #f2ece4;
  font-size: 11px;
}
.overview-bookings td strong {
  font-size: 12px;
  color: #594334;
}
.overview-bookings td small {
  display: block;
  color: #ae9b88;
  font-size: 9px;
  margin-top: 6px;
}
.overview-detail {
  border: 1px solid #eadfcc;
  padding: 8px 10px;
  border-radius: 8px;
  color: #967344;
  background: #fffcf7;
  font-size: 10px;
  cursor: pointer;
  white-space: nowrap;
}
.overview-bookings .badge {
  font-size: 9px;
  white-space: nowrap;
}
.overview-empty {
  padding: 28px;
  color: #aa9580;
  text-align: center;
  font-size: 12px;
}
.maintenance-preview-list {
  padding: 0 24px 24px;
}
.maintenance-preview-list > article {
  display: flex;
  align-items: start;
  gap: 12px;
  padding: 16px 0;
  border-top: 1px solid #f0e8dd;
}
.maintenance-preview-list article > span {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  background: #fbf0e2;
  border-radius: 10px;
  color: #a87f4c;
}
.maintenance-preview-list strong {
  font-size: 13px;
  color: #76553b;
}
.maintenance-preview-list p {
  margin: 7px 0;
  font-size: 11px;
  line-height: 1.6;
  color: #9a826b;
  overflow-wrap: anywhere;
}
.maintenance-preview-list small {
  font-size: 9px;
  color: #ac9780;
}
.maintenance-clear {
  padding: 28px 0;
  text-align: center;
}
.maintenance-clear > span {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  background: #eef4e9;
  border-radius: 50%;
  color: #869c70;
  margin: 0 auto 18px;
  font-size: 24px;
}
.maintenance-clear strong {
  font-size: 12px;
}
@media (max-width: 1100px) {
  .overview-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  .overview-lower {
    grid-template-columns: 1fr;
  }
  .overview-charts {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 600px) {
  .overview-welcome {
    padding: 26px;
  }
  .overview-mark {
    display: none;
  }
  .overview-metrics {
    gap: 10px;
  }
  .overview-metrics article {
    padding: 16px;
  }
  .overview-metrics strong {
    font-size: 27px;
  }
  .overview-card > header {
    padding: 20px;
  }
  .income-total {
    font-size: 14px;
  }
  .income-chart {
    gap: 12px;
    padding: 0 20px 20px;
  }
  .income-column strong {
    font-size: 10px;
  }
  .court-bars {
    padding: 0 20px 20px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .bar-track span,
  .income-column-track span {
    transition: none;
  }
}
</style>

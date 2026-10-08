<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import MaintenanceActions from '../components/MaintenanceActions.vue';
import { getAdminData } from '../services/adminService.js';
const props = defineProps({ auth: { type: Object, required: true } });
const emit = defineEmits(['session-expired']);
function today() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(
    new Date(),
  );
}
const maintenanceActions = ref(null);
async function maintenanceChanged(message) {
  success.value = message;
  await load();
}
const section = ref('overview');
const tabs = [
  { id: 'overview', label: 'Tổng quan', icon: '◫' },
  { id: 'schedule', label: 'Lịch sân', icon: '▤' },
  { id: 'counter', label: 'Khách tại quầy', icon: '♙' },
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
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10)));
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 10, page.value * 10),
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
  return { kind: 'free', title: 'Chưa có booking hoặc bảo trì trong khung giờ này' };
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
async function open(b, kind = 'detail') {
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
  if (!booking?.walkIn) return 'Chỉ hủy booking của khách tại quầy bằng thao tác này.';
  if (!['PENDING', 'NO_SHOW_PENDING'].includes(booking.status))
    return 'Booking đã check-in, hoàn tất, hủy hoặc NO_SHOW không thể hủy.';
  const start = new Date(`${booking.date}T${booking.startTime}+07:00`).getTime();
  const now = data.value?.serverTime
    ? new Date(`${data.value.serverTime}+07:00`).getTime()
    : Date.now();
  if (!Number.isFinite(start) || start - now < 120 * 60000)
    return 'Chỉ hủy được trước giờ chơi ít nhất 2 tiếng.';
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
function openForm(kind) {
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
    const phone = form.value.walkInPhone.replace(/\s+/g, '').replace(/^\+84/, '0');
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
      formError.value = 'Chọn sân, lý do, thời gian hợp lệ và chi phí nguyên không âm.';
      return;
    }
    if (form.value.type === 'EMERGENCY' && !form.value.emergencyConfirmed) {
      formError.value = 'Xác nhận ảnh hưởng booking trước khi báo sự cố đột xuất.';
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
    if (!saving.value && !loading.value && !selected.value && !formKind.value) load();
  }, 60000);
});
onUnmounted(() => {
  disposed = true;
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
        <div><strong>Carrot Staff</strong><small>Không gian nhân viên</small></div>
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
          <p>{{ tabs.find((tab) => tab.id === section)?.label }} · Theo ngày đã chọn</p>
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
        <div v-if="section === 'overview'" class="metrics">
          <article>
            <span>Tổng booking</span><strong>{{ data.summary.total }}</strong
            ><small>Bao gồm booking đã hủy / không đến</small>
          </article>
          <article>
            <span>Chờ check-in</span><strong>{{ data.summary.waiting }}</strong
            ><small>Chờ nhận sân và trễ nhận sân</small>
          </article>
          <article>
            <span>Đang chơi</span><strong>{{ data.summary.playing }}</strong
            ><small>Đã check-in và đang trong giờ chơi</small>
          </article>
          <article>
            <span>Đã hoàn tất</span><strong>{{ data.summary.completed }}</strong
            ><small>Booking đã chốt trạng thái hoàn tất</small>
          </article>
        </div>
        <section v-if="section === 'schedule'" class="panel">
          <header>
            <div>
              <h2>Lịch sân theo ngày</h2>
              <p>Mỗi ô 30 phút. Bấm ô có booking để xem chi tiết.</p>
            </div>
          </header>
          <div class="legend">
            <span class="free">Trống</span><span class="booked">Đã đặt</span
            ><span class="checked">Đã check-in</span><span class="done">Hoàn tất</span
            ><span class="maintenance">Bảo trì</span
            ><span class="inactive">Ngừng hoạt động</span>
          </div>
          <p class="hint">
            Ô trống thể hiện chưa có booking/bảo trì, không thay thế kiểm tra giờ mở cửa
            và điều kiện đặt sân.
          </p>
          <div class="scroll">
            <table class="schedule">
              <thead>
                <tr>
                  <th>Sân / giờ</th>
                  <th v-for="slot in slots" :key="slot.start">{{ slot.label }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="court in data.courts" :key="court.id">
                  <th>{{ court.name }}</th>
                  <td v-for="slot in slots" :key="slot.start">
                    <button
                      :class="cell(court, slot).kind"
                      :title="cell(court, slot).title"
                      :aria-label="
                        court.name + ' ' + slot.label + ': ' + cell(court, slot).title
                      "
                      :disabled="!cell(court, slot).booking || saving || loading"
                      @click="open(cell(court, slot).booking)"
                    >
                      {{
                        cell(court, slot).booking
                          ? '#' + cell(court, slot).booking.id
                          : '·'
                      }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="!data.courts.length" class="empty">Chưa có sân.</p>
        </section>
        <section v-if="['overview', 'counter'].includes(section)" class="panel">
          <header>
            <div>
              <h2>
                {{
                  section === 'counter' ? 'Khách tại quầy / booking' : 'Danh sách booking'
                }}
              </h2>
              <p>
                Check-in từ giờ bắt đầu đến trước 30 phút. NO_SHOW được hệ thống xử lý.
              </p>
            </div>
          </header>
          <div v-if="section === 'counter'" class="counter-tools">
            <button
              class="primary"
              :disabled="loading || saving"
              @click="openForm('booking')"
            >
              + Tạo booking tại quầy</button
            ><label
              ><input v-model="counterOnly" type="checkbox" /> Chỉ khách tại quầy</label
            >
          </div>
          <div class="filters">
            <input
              v-model="query"
              placeholder="Tìm mã, tên, điện thoại hoặc sân…"
              aria-label="Tìm booking"
            /><select v-model="status" aria-label="Lọc trạng thái">
              <option value="">Tất cả trạng thái</option>
              <option v-for="(label, key) in labels" :key="key" :value="key">
                {{ label }}
              </option>
            </select>
          </div>
          <div class="scroll">
            <table>
              <thead>
                <tr>
                  <th>Booking / khách</th>
                  <th>Sân</th>
                  <th>Giờ chơi</th>
                  <th>Giá trị booking</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in shown" :key="b.id">
                  <td>
                    <strong>#{{ b.id }} · {{ b.customerName }}</strong
                    ><small>{{ b.phone || 'Chưa có điện thoại' }}</small>
                  </td>
                  <td>{{ b.courtName }}</td>
                  <td>{{ time(b.startTime) }}–{{ time(b.endTime) }}</td>
                  <td>{{ money(b.totalAmount) }}</td>
                  <td>
                    <span class="badge" :class="b.status">{{
                      labels[b.status] || b.status
                    }}</span
                    ><small>{{ b.checkInHint }}</small>
                  </td>
                  <td>
                    <div class="row-actions">
                      <button :disabled="loading || saving" @click="open(b)">
                        Chi tiết</button
                      ><button
                        class="primary"
                        :disabled="!b.canCheckIn || saving || loading"
                        :title="b.checkInHint"
                        @click="open(b, 'checkin')"
                      >
                        Check-in
                      </button>
                      <button
                        class="danger"
                        :disabled="loading || saving || !canCancel(b)"
                        :title="cancelHint(b) || 'Hủy booking khách tại quầy'"
                        @click="open(b, 'cancel')"
                      >
                        Hủy booking
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!shown.length">
                  <td colspan="6" class="empty">Không có booking phù hợp.</td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer>
            <span>{{ filtered.length }} kết quả</span
            ><button :disabled="page <= 1" @click="page--">Trước</button
            ><span>{{ page }} / {{ pages }}</span
            ><button :disabled="page >= pages" @click="page++">Sau</button>
          </footer>
        </section>
        <section v-if="section === 'maintenance'" class="panel">
          <header>
            <div>
              <h2>Bảo trì / sửa chữa</h2>
              <p>
                Các lịch giao với ngày {{ data.date }}. STAFF báo sự cố, Admin duyệt hoàn
                tất.
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
                      data.courts.find((c) => c.id === m.courtId)?.name || '#' + m.courtId
                    }}
                  </td>
                  <td class="reason">{{ m.reason }}</td>
                  <td>
                    {{ m.startTime.replace('T', ' ')
                    }}<small
                      >Đến {{ m.endTime?.replace('T', ' ') || 'Chưa xác định' }}</small
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
                  </td><td><div class="maintenance-row-actions"><button :disabled="loading || saving || !['SCHEDULED','IN_PROGRESS','PENDING_APPROVAL'].includes(m.status)" @click="maintenanceActions.open(m.id, m.courtId, 'edit')">Sửa</button><button class="danger" :disabled="loading || saving || !['SCHEDULED','IN_PROGRESS','PENDING_APPROVAL'].includes(m.status)" @click="maintenanceActions.open(m.id, m.courtId, 'cancel')">Hủy lịch</button></div></td>
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
                Ngày {{ data.date }} · #{{ data.myStats.staffId }} · Theo thời điểm thực
                hiện thao tác.
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
            Tiền booking tính theo hóa đơn COMPLETED và nhân viên chốt hóa đơn; tiền vãng
            lai theo paidBy/paidAt. Chưa gồm bán hàng tại quầy và không thay thế đối soát
            tiền thực tế.
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
                formKind === 'booking' ? 'Tạo booking tại quầy' : 'Báo bảo trì / sửa chữa'
              }}
            </h2>
            <button type="button" :disabled="saving" aria-label="Đóng" @click="closeForm">
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
                ><input v-model="form.courtIds" type="checkbox" :value="court.id" />{{
                  court.name
                }}</label
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
              Tối thiểu 1 giờ. Backend kiểm tra giá, sân trùng lịch, bảo trì và giới hạn
              ngày đặt. Khách tại quầy được lưu theo số điện thoại, không tạo tài khoản
              người dùng.
            </p>
          </template>
          <template v-else-if="formKind === 'maintenance'">
            <label
              >Sân<select v-model="form.courtId" required :disabled="saving">
                <option value="">Chọn sân</option>
                <option v-for="court in data?.courts" :key="court.id" :value="court.id">
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
              Tôi xác nhận: sự cố có thể hủy booking chưa check-in bị ảnh hưởng theo quy
              tắc backend.</label
            >
          </template>
          <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
          <footer>
            <button type="button" :disabled="saving" @click="closeForm">Hủy</button
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
            <button :disabled="saving" aria-label="Đóng" @click="close">✕</button>
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
            <dd>{{ selected.checkedInAt?.replace('T', ' ') || 'Chưa check-in' }}</dd>
            <dt>Nhân viên check-in</dt>
            <dd>{{ selected.checkedInBy ? '#' + selected.checkedInBy : '—' }}</dd>
          </dl>
          <p class="hint">
            {{ selected.checkInHint }}. Giá trị booking không phải số tiền chưa thanh
            toán.
          </p>
          <p v-if="mode === 'checkin'">
            Xác nhận khách đã có mặt? Hệ thống ghi nhận tài khoản nhân viên hiện tại.
          </p>
          <p v-if="mode === 'cancel'" class="cancel-warning">
            Bạn xác nhận hủy booking này? Lịch sân sẽ được cập nhật sau khi hủy. Thao tác
            này không tự hoàn tiền.
          </p>
          <p v-if="selected.walkIn && cancelHint(selected)" class="hint">
            {{ cancelHint(selected) }}
          </p>
          <footer>
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
</template>

<style scoped>
.maintenance-row-actions{display:grid;grid-template-columns:75px 95px;gap:8px}
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

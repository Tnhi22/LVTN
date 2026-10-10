<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import ShuttleSalesReport from '../components/ShuttleSalesReport.vue';
import DailyBookingsPanel from '../components/DailyBookingsPanel.vue';
import BookingCounterActions from '../components/BookingCounterActions.vue';

import { getAdminData } from '../services/adminService';

import {
  bookingStatuses,
  customerName,
  customerPhone,
  typeName,
  actionReason,
  filterBookings,
  settlementBody,
  bookingTime,
} from '../services/adminBookingUtils';

const props = defineProps({
  auth: { type: Object, required: true },
  initialRange: { type: Object, default: null },
  refreshKey: { type: Number, default: 0 },
});

const emit = defineEmits(['session-expired']);

const rows = ref([]),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  success = ref(''),
  updated = ref('');

const filters = ref({
    query: '',
    from: '',
    to: '',
    status: '',
    type: '',
    source: '',
    payment: '',
  }),
  page = ref(1),
  pageSize = ref(10);

watch(
  () => props.initialRange,
  (r) => {
    if (r) {
      filters.value.from = r.from;
      filters.value.to = r.to;
      page.value = 1;
    }
  },
  { immediate: true, deep: true },
);

const detail = ref(null),
  detailDialog = ref(null),
  receipt = ref(null),
  receiptLoading = ref(false),
  receiptError = ref('');

const confirmation = ref(null),
  confirmDialog = ref(null),
  actionError = ref(''),
  method = ref('CASH'),
  received = ref('');

const salesRefresh = ref(0);
watch(
  () => props.refreshKey,
  () => {
    salesRefresh.value++;
    load();
  },
);
watch(success, () => {
  salesRefresh.value++;
});
const work = ref(null);
defineExpose({ createWalkIn: () => workOn('create'), openBookingById });
async function openBookingById(id, action = '') {
  await load();
  const booking = rows.value.find((row) => row.id === Number(id));
  if (!booking) {
    error.value = 'Không tải được booking #' + id;
    return;
  }
  await openDetail(booking);
  if (action && !reason(booking, action)) ask(booking, action);
}
function workOn(kind, booking = null) {
  if (saving.value) return;
  work.value = { kind, booking };
}
async function workDone(text) {
  const id = detail.value?.id;
  work.value = null;
  success.value = text;
  await load();
  if (id && detail.value?.id === id) await openDetail(detail.value);
}
const now = ref(Date.now());

let timer,
  listController,
  receiptController,
  actionController,
  disposed = false;

const filtered = computed(() => filterBookings(laneRows.value, filters.value));

const lane = ref('all'),
  expandedFilters = ref(false),
  queuePage = ref(1);

const localDay = (stamp) => {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(stamp));

  return ['year', 'month', 'day']
    .map((t) => parts.find((p) => p.type === t).value)
    .join('-');
};

const todayDate = computed(() => localDay(now.value));

const dayRows = computed(() =>
  rows.value.filter((b) => b.bookingDate === todayDate.value),
);

const eligibleRows = computed(() =>
  dayRows.value
    .filter((b) => !reason(b, 'check-in'))
    .sort((a, b) => bookingTime(a) - bookingTime(b)),
);

const queuePages = computed(() =>
  Math.max(1, Math.ceil(eligibleRows.value.length / 6)),
);

const queueRows = computed(() =>
  eligibleRows.value.slice(
    (Math.min(queuePage.value, queuePages.value) - 1) * 6,
    Math.min(queuePage.value, queuePages.value) * 6,
  ),
);

const upcomingCount = computed(
  () =>
    dayRows.value.filter(
      (b) => b.status === 'PENDING' && bookingTime(b) > now.value,
    ).length,
);

const overdueCount = computed(
  () =>
    dayRows.value.filter(
      (b) => b.status === 'PENDING' && bookingTime(b) + 30 * 60000 <= now.value,
    ).length,
);

const lanes = computed(() => [
  { id: 'all', label: 'Tất cả', count: rows.value.length },

  {
    id: 'pending',
    label: 'Chờ nhận sân',
    count: rows.value.filter((b) => b.status === 'PENDING').length,
  },

  {
    id: 'playing',
    label: 'Đang chơi',
    count: rows.value.filter((b) => b.status === 'CHECKED_IN').length,
  },

  {
    id: 'unpaid',
    label: 'Chờ thu tiền',
    count: rows.value.filter((b) => b.status === 'COMPLETED' && !b.paidAt)
      .length,
  },

  {
    id: 'paid',
    label: 'Đã thu tiền',
    count: rows.value.filter((b) => !!b.paidAt).length,
  },
]);

function chooseLane(id) {
  lane.value = id;
  filters.value.status = '';
  filters.value.payment = '';
  page.value = 1;
}

const laneRows = computed(() =>
  rows.value.filter((b) =>
    lane.value === 'pending'
      ? b.status === 'PENDING'
      : lane.value === 'playing'
        ? b.status === 'CHECKED_IN'
        : lane.value === 'unpaid'
          ? b.status === 'COMPLETED' && !b.paidAt
          : lane.value === 'paid'
            ? !!b.paidAt
            : true,
  ),
);

const initials = (b) =>
  customerName(b)
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((x) => x[0])
    .join('')
    .toUpperCase();

const remaining = (b) =>
  Math.max(0, Math.ceil((bookingTime(b) + 30 * 60000 - now.value) / 60000));

const clock = computed(() =>
  new Date(now.value).toLocaleTimeString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
  }),
);

const suggestedAction = (b) =>
  !reason(b, 'check-in')
    ? 'check-in'
    : !reason(b, 'complete')
      ? 'complete'
      : !reason(b, 'settle')
        ? 'settle'
        : null;

watch(lane, () => {
  page.value = 1;
});

const pages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / pageSize.value)),
);

const shown = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);

const typeOptions = computed(() =>
  [...new Set(rows.value.map(typeName).filter(Boolean))].sort(),
);

const paidTotal = computed(() =>
  filtered.value
    .filter((b) => b.paidAt)
    .reduce((sum, b) => sum + (b.totalAmount ?? 0), 0),
);

const money = (n) =>
  n == null
    ? '—'
    : new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        maximumFractionDigits: 0,
      }).format(n);

const date = (d) => (d ? d.split('-').reverse().join('/') : '—');

const time = (t) => t?.slice(0, 5) || '—';

const dateTime = (d) =>
  d
    ? new Date(/[Zz]|[+-]\d\d:\d\d$/.test(d) ? d : `${d}+07:00`).toLocaleString(
        'vi-VN',
        {
          timeZone: 'Asia/Ho_Chi_Minh',
        },
      )
    : '—';

const reason = (b, a) => {
  if (a !== 'cancel') return actionReason(b, a, now.value);
  if (!b?.visitor)
    return 'Chỉ Staff/Admin hủy booking khách tại quầy bằng thao tác này.';
  if (b.status !== 'PENDING') return 'Chỉ hủy booking tại quầy chưa check-in.';
  const start = bookingTime(b);
  if (!Number.isFinite(start) || start - now.value <= 30 * 60000)
    return 'Không được hủy trong 30 phút trước giờ chơi hoặc sau khi bắt đầu.';
  return '';
};

watch(
  filters,
  () => {
    page.value = 1;
  },
  { deep: true },
);

watch(pageSize, () => {
  page.value = 1;
});

watch(pages, (n) => {
  page.value = Math.min(page.value, n);
});

watch(detail, async () => {
  await nextTick();
  if (disposed) return;
  if (detail.value && detailDialog.value && !detailDialog.value.open)
    detailDialog.value.showModal();
  else if (!detail.value) detailDialog.value?.close();
});

watch(confirmation, async () => {
  await nextTick();
  if (disposed) return;
  if (confirmation.value && confirmDialog.value && !confirmDialog.value.open)
    confirmDialog.value.showModal();
  else if (!confirmation.value) confirmDialog.value?.close();
});

function message(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Máy chủ phản hồi quá lâu. Hãy thử lại.'
    : e instanceof TypeError
      ? 'Không kết nối được backend.'
      : e.message;
}

async function request(path, controller, options = {}) {
  const timeout = setTimeout(() => controller.abort(), 15000);
  try {
    return await getAdminData(
      path,
      props.auth.accessToken,
      controller.signal,
      options,
    );
  } finally {
    clearTimeout(timeout);
  }
}

async function load() {
  if (saving.value) return;

  listController?.abort();
  const current = new AbortController();
  listController = current;
  loading.value = true;
  error.value = '';

  try {
    const data = await request('/api/bookings', current);
    if (!Array.isArray(data))
      throw new Error('Danh sách booking không đúng định dạng.');
    if (disposed || listController !== current) return;
    rows.value = data;
    loadCalendar();
    updated.value = new Date().toLocaleTimeString('vi-VN');
    if (detail.value) {
      const latest = data.find((b) => b.id === detail.value.id);
      if (latest) {
        const previous = detail.value;
        detail.value = latest;
        if (
          previous.status !== latest.status ||
          previous.totalAmount !== latest.totalAmount ||
          previous.paidAt !== latest.paidAt ||
          previous.endTime !== latest.endTime
        )
          await openDetail(latest);
      } else closeDetail();
    }
  } catch (e) {
    if (!disposed && listController === current) error.value = message(e);
  } finally {
    if (listController === current) loading.value = false;
  }
}

function resetAll() {
  reset();
  chooseLane('all');
}
function reset() {
  filters.value = {
    query: '',
    from: '',
    to: '',
    status: '',
    type: '',
    source: '',
    payment: '',
  };
}

function today() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const get = (t) => parts.find((p) => p.type === t).value;
  filters.value.from =
    filters.value.to = `${get('year')}-${get('month')}-${get('day')}`;
}

function closeDetail() {
  if (saving.value) return;
  receiptController?.abort();
  detail.value = null;
  receipt.value = null;
  detailDialog.value?.close();
}

function closeConfirmation() {
  if (saving.value) return;
  confirmation.value = null;
  actionError.value = '';
  confirmDialog.value?.close();
}

function outside(event, close) {
  if (event.target !== event.currentTarget) return;
  const r = event.currentTarget.getBoundingClientRect();
  if (
    event.clientX < r.left ||
    event.clientX > r.right ||
    event.clientY < r.top ||
    event.clientY > r.bottom
  )
    close();
}

function setMethod(value) {
  method.value = value;
  if (value === 'BANK_TRANSFER')
    received.value = confirmation.value?.booking.totalAmount ?? '';
}

async function openDetail(b) {
  receiptController?.abort();
  detail.value = b;
  receipt.value = null;
  receiptError.value = '';
  receiptLoading.value = false;

  const current = new AbortController();
  receiptController = current;
  receiptLoading.value = true;

  try {
    const data = await request(`/api/bookings/${b.id}/receipt`, current);
    if (!disposed && receiptController === current && detail.value?.id === b.id)
      receipt.value = data;
  } catch (e) {
    if (!disposed && receiptController === current)
      receiptError.value = message(e);
  } finally {
    if (receiptController === current) receiptLoading.value = false;
  }
}

function ask(b, action) {
  const blocked = reason(b, action);
  if (blocked) {
    error.value = blocked;
    return;
  }

  actionError.value = '';
  method.value = 'CASH';
  received.value = b.totalAmount ?? '';
  confirmation.value = { booking: b, action };
}

const actionLabels = {
  'check-in': 'Check-in nhận sân',
  complete: 'Hoàn thành sân',
  settle: 'Nhận tiền',
  cancel: 'Hủy booking tại quầy',
};

const change = computed(() =>
  Math.max(
    0,
    Number(received.value || 0) -
      (confirmation.value?.booking.totalAmount || 0),
  ),
);

async function execute() {
  if (saving.value || !confirmation.value) return;

  const { booking: b, action } = confirmation.value;

  const blocked = actionReason(b, action, Date.now());
  if (blocked) {
    actionError.value = blocked;
    return;
  }

  let body;

  try {
    if (action === 'settle')
      body = settlementBody(method.value, received.value, b.totalAmount);
  } catch (e) {
    actionError.value = e.message;
    return;
  }

  saving.value = true;
  actionError.value = '';
  success.value = '';
  actionController = new AbortController();

  try {
    const result = await request(
      `/api/bookings/${b.id}/${action === 'cancel' ? 'cancel-walk-in' : action}`,
      actionController,
      {
        method: action === 'cancel' ? 'DELETE' : 'POST',
        ...(body ? { body: JSON.stringify(body) } : {}),
      },
    );

    if (disposed) return;

    const index = rows.value.findIndex((row) => row.id === b.id);

    let latest;

    if (action === 'check-in' || action === 'cancel') latest = result;
    else
      latest = {
        ...b,
        status: result.bookingStatus,
        totalAmount: result.totalAmount,
        ...(action === 'settle'
          ? {
              paidAt: new Date().toISOString(),
              paymentMethod: result.paymentMethod,
              amountReceived: result.amountReceived,
            }
          : { completedAt: result.completedAt }),
      };

    if (index >= 0) rows.value[index] = latest;

    if (detail.value?.id === b.id) detail.value = latest;

    confirmation.value = null;
    success.value = `${actionLabels[action]} thành công cho lịch #${b.id}.`;

    saving.value = false;

    if (action === 'settle') closeDetail();

    await load();

    if (detail.value?.id === b.id) await openDetail(detail.value);
  } catch (e) {
    if (!disposed) actionError.value = message(e);
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  loadCalendar();
  load();
  timer = setInterval(() => {
    now.value = Date.now();
    if (!saving.value && !loading.value && !confirmation.value && !work.value)
      load();
  }, 15000);
});

onUnmounted(() => {
  calendarController?.abort();
  disposed = true;
  clearInterval(timer);
  listController?.abort();
  receiptController?.abort();
  actionController?.abort();
});

const calendarDate = ref(
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' }).format(
    new Date(),
  ),
);
const calendarQuery = ref('');
const calendarData = ref([]);
const calendarError = ref('');
const calendarBusy = ref(false);
let calendarController;
let calendarSequence = 0;
const calendarSlots = Array.from({ length: 48 }, (_, i) => ({
  minute: i * 30,
  label: `${String(Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`,
}));
const calendarCourts = computed(() =>
  calendarData.value.filter((c) =>
    `${c.roomName} ${c.name} ${c.typeName}`
      .toLowerCase()
      .includes(calendarQuery.value.trim().toLowerCase()),
  ),
);
const calendarBookings = computed(() =>
  rows.value.filter((b) => b.bookingDate === calendarDate.value),
);
const calendarLabels = {
  PENDING: 'Chờ nhận sân',
  CHECKED_IN: 'Đang chơi',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Vắng mặt',
  NO_SHOW_PENDING: 'Chờ xử lý vắng',
};
const calendarMinutes = (value) => {
  const [h, m] = String(value || '')
    .slice(0, 5)
    .split(':')
    .map(Number);
  return h * 60 + m;
};
function calendarCell(c, slot) {
  const bookings = calendarBookings.value.filter(
    (b) =>
      b.court?.id === c.id &&
      !['CANCELLED', 'NO_SHOW'].includes(b.status) &&
      calendarMinutes(b.startTime) < slot.minute + 30 &&
      calendarMinutes(b.endTime) > slot.minute,
  );
  if (bookings.length) {
    const b = bookings[0];
    return {
      kind: b.status,
      text: calendarLabels[b.status] || b.status,
      booking: b,
      title: `#${b.id} · ${customerName(b)} · ${b.startTime?.slice(0, 5)}–${b.endTime?.slice(0, 5)} · ${calendarLabels[b.status] || b.status}`,
    };
  }
  const start = new Date(
    `${calendarDate.value}T${slot.label}:00+07:00`,
  ).getTime();
  const busy = (c.busy || []).find(
    (b) =>
      new Date(b.start + (/[Z+]/.test(b.start) ? '' : '+07:00')).getTime() <
        start + 1800000 &&
      (!b.end ||
        new Date(b.end + (/[Z+]/.test(b.end) ? '' : '+07:00')).getTime() >
          start),
  );
  if (busy)
    return {
      kind:
        busy.reason === 'Bảo trì'
          ? 'MAINTENANCE'
          : busy.reason === 'Buổi Daily Visitor'
            ? 'DAILY'
            : 'BUSY',
      text: busy.reason,
      title: busy.reason,
    };
  return {
    kind: c.active ? 'FREE' : 'INACTIVE',
    text: c.active ? 'Trống' : 'Ngừng hoạt động',
    title: c.active
      ? 'Sân trống trong khoảng này'
      : 'Sân/phòng đang ngừng hoạt động',
  };
}
async function loadCalendar() {
  calendarController?.abort();
  const current = new AbortController();
  calendarController = current;
  const seq = ++calendarSequence;
  calendarBusy.value = true;
  calendarError.value = '';
  try {
    const result = await request(
      `/api/courts/schedule?date=${calendarDate.value}`,
      current,
    );
    if (seq !== calendarSequence || disposed) return;
    if (!Array.isArray(result.courts))
      throw new Error('Dữ liệu lịch sân không đúng định dạng.');
    calendarData.value = result.courts;
  } catch (e) {
    if (seq === calendarSequence && !disposed && e.name !== 'AbortError') {
      calendarData.value = [];
      calendarError.value = message(e);
    }
  } finally {
    if (seq === calendarSequence) calendarBusy.value = false;
  }
}
watch(calendarDate, loadCalendar);
</script>

<template>
  <div class="booking-workspace">
    <header class="hero">
      <div class="hero-copy">
        <span class="eyebrow">CARROT / LỊCH ĐẶT SÂN</span>
        <h2>Quản lý lịch sân.<br />Nhận sân và thu tiền.</h2>
        <p>Nhận sân nhanh. Theo dõi trận đấu. Thu tiền rõ ràng.</p>
        <div class="hero-meta">
          <span class="live-dot"></span>{{ date(todayDate) }} · {{ clock }}
          <span class="meta-divider">/</span> Giờ Việt Nam
        </div>
      </div>
      <div class="hero-court" aria-hidden="true">
        <div class="court-line middle"></div>
        <div class="court-line left"></div>
        <div class="court-line right"></div>
        <div class="court-net"></div>
        <span class="court-mark">C</span>
      </div>
      <button
        class="refresh hero-refresh"
        :disabled="loading || saving"
        @click="load"
      >
        <span aria-hidden="true">↻</span>
        {{ loading ? 'Đang tải…' : 'Làm mới' }}
      </button>
    </header>

    <div class="counter-toolbar">
      <button class="primary" @click="workOn('create')">
        ＋ Booking tại quầy</button
      ><button @click="workOn('daily')">🏸 Daily tại quầy</button
      ><button @click="workOn('sale')">Bán cầu riêng · ống / lẻ</button
      ><button @click="workOn('history')">Hóa đơn bán cầu</button>
    </div>
    <BookingCounterActions
      :auth="auth"
      :work="work"
      @close="work = null"
      @done="workDone"
      @session-expired="emit('session-expired')"
    />
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <p v-if="success" class="message success" role="status">✓ {{ success }}</p>

    <ShuttleSalesReport
      :auth="auth"
      :refresh-key="salesRefresh"
      @session-expired="emit('session-expired')"
    />
    <DailyBookingsPanel
      :auth="auth"
      :refresh-key="salesRefresh"
      @session-expired="emit('session-expired')"
    />
    <section class="court-calendar">
      <div class="calendar-heading">
        <div>
          <span class="eyebrow">TOÀN BỘ PHÒNG / SÂN</span>
          <h3>Lịch sân theo giờ</h3>
          <p>
            Mỗi ô là 30 phút. Bấm ô booking để xem chi tiết; kéo ngang để xem đủ
            24 giờ.
          </p>
        </div>
        <div class="calendar-controls">
          <input
            v-model="calendarDate"
            type="date"
            aria-label="Ngày xem lịch sân"
          /><input
            v-model="calendarQuery"
            type="search"
            placeholder="Tìm phòng / sân P1-01…"
            aria-label="Tìm phòng sân"
          /><button @click="loadCalendar" :disabled="calendarBusy">
            {{ calendarBusy ? 'Đang tải…' : '↻ Lịch sân' }}
          </button>
        </div>
      </div>
      <div class="calendar-legend">
        <span
          v-for="(label, key) in {
            FREE: 'Trống',
            PENDING: 'Chờ nhận sân',
            CHECKED_IN: 'Đang chơi',
            COMPLETED: 'Hoàn thành',
            NO_SHOW_PENDING: 'Chờ xử lý vắng',
            DAILY: 'Daily Visitor',
            MAINTENANCE: 'Bảo trì',
            INACTIVE: 'Ngừng hoạt động',
          }"
          :key="key"
          ><i :class="'calendar-' + key"></i>{{ label }}</span
        >
      </div>
      <p v-if="calendarError" role="alert" class="message error">
        {{ calendarError }}
      </p>
      <div v-else class="calendar-scroll" :aria-busy="calendarBusy">
        <table class="calendar-table">
          <thead>
            <tr>
              <th class="calendar-court-col">Phòng / sân</th>
              <th v-for="slot in calendarSlots" :key="slot.minute">
                {{ slot.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in calendarCourts" :key="c.id">
              <th class="calendar-court-col">
                <strong>{{ c.name }}</strong
                ><small>{{ c.roomName }} · {{ c.typeName }}</small>
              </th>
              <td v-for="slot in calendarSlots" :key="slot.minute">
                <button
                  :class="[
                    'calendar-cell',
                    'calendar-' + calendarCell(c, slot).kind,
                  ]"
                  :title="calendarCell(c, slot).title"
                  :aria-label="
                    c.name +
                    ' ' +
                    slot.label +
                    ': ' +
                    calendarCell(c, slot).title
                  "
                  :disabled="!calendarCell(c, slot).booking"
                  @click="openDetail(calendarCell(c, slot).booking)"
                >
                  {{
                    calendarCell(c, slot).booking
                      ? '#' + calendarCell(c, slot).booking.id
                      : calendarCell(c, slot).text
                  }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p
          v-if="!calendarBusy && !calendarCourts.length"
          class="calendar-empty"
        >
          Không có sân phù hợp.
        </p>
      </div>
      <p class="calendar-footnote">
        Booking đã hủy hoặc vắng mặt không giữ chỗ. Màu trạng thái lấy từ
        booking; khoảng Daily và bảo trì lấy từ backend.
      </p>
    </section>

    <div class="metric-grid">
      <button class="metric amber" @click="chooseLane('pending')">
        <span class="metric-icon">◷</span>
        <div>
          <small>Chờ nhận sân</small
          ><strong>{{
            rows.filter((b) => b.status === 'PENDING').length
          }}</strong
          ><span>Trong toàn bộ lịch đặt</span>
        </div>
        <span class="metric-arrow">↗</span></button
      ><button class="metric green" @click="chooseLane('playing')">
        <span class="metric-icon">▶</span>
        <div>
          <small>Đang chơi</small
          ><strong>{{
            rows.filter((b) => b.status === 'CHECKED_IN').length
          }}</strong
          ><span>Sân đã check-in</span>
        </div>
        <span class="metric-arrow">↗</span></button
      ><button class="metric blue" @click="chooseLane('unpaid')">
        <span class="metric-icon">₫</span>
        <div>
          <small>Chờ thu tiền</small
          ><strong>{{
            rows.filter((b) => b.status === 'COMPLETED' && !b.paidAt).length
          }}</strong
          ><span>Đã hoàn thành, chưa thanh toán</span>
        </div>
        <span class="metric-arrow">↗</span>
      </button>
      <article class="metric revenue">
        <span class="metric-icon">✓</span>
        <div>
          <small>Đã thanh toán / bộ lọc</small
          ><strong>{{ money(paidTotal) }}</strong
          ><span>Tổng hóa đơn theo ngày chơi</span>
        </div>
      </article>
    </div>

    <section class="arrival-section">
      <div class="section-heading">
        <div>
          <span class="eyebrow">ƯU TIÊN HÔM NAY</span>
          <h3>
            Nhận sân ngay
            <span class="count-pill">{{ eligibleRows.length }}</span>
          </h3>
          <p>
            Chỉ hiển thị lịch đang trong 30 phút nhận sân. Không áp dụng bộ lọc
            bên dưới.
          </p>
        </div>
        <div class="arrival-notes">
          <span>{{ upcomingCount }} lịch sắp đến</span
          ><span v-if="overdueCount" class="late-note"
            >{{ overdueCount }} lịch chờ đã quá giờ nhận</span
          >
        </div>
      </div>

      <div v-if="queueRows.length" class="arrival-grid">
        <article v-for="b in queueRows" :key="b.id" class="arrival-card">
          <div class="card-top">
            <span class="ready-tag"
              ><span class="live-dot"></span>Đến giờ nhận sân</span
            ><span class="booking-id">#{{ b.id }}</span>
          </div>
          <div class="court-title">
            <strong>{{ b.court?.name || '—' }}</strong
            ><span>{{ typeName(b) }} · {{ b.court?.room?.name }}</span>
          </div>
          <div class="player">
            <span class="avatar">{{ initials(b) }}</span>
            <div>
              <strong>{{ customerName(b) }}</strong
              ><small>{{ customerPhone(b) || 'Chưa có SĐT' }}</small>
            </div>
          </div>
          <div class="slot-line">
            <span
              >{{ time(b.startTime) }} <span class="slot-dash">—</span>
              {{ time(b.endTime) }}</span
            ><strong>{{ money(b.totalAmount) }}</strong>
          </div>
          <div class="deadline">
            <span>Còn {{ remaining(b) }} phút nhận sân</span
            ><span>{{ b.visitor ? 'Tại quầy' : 'Trực tuyến' }}</span>
          </div>
          <div class="card-actions">
            <button
              class="primary checkin-button"
              :disabled="saving || loading || !!reason(b, 'check-in')"
              @click="ask(b, 'check-in')"
            >
              ✓ Check-in nhận sân</button
            ><button
              class="icon-button"
              :disabled="saving"
              @click="openDetail(b)"
              :aria-label="`Chi tiết lịch #${b.id}`"
            >
              ↗
            </button>
          </div>
        </article>
      </div>

      <div v-else class="arrival-empty">
        <span class="empty-symbol">✓</span>
        <div>
          <strong>{{
            loading
              ? 'Đang kiểm tra lịch nhận sân…'
              : error && !rows.length
                ? 'Chưa tải được lịch nhận sân'
                : 'Hiện chưa có khách cần check-in'
          }}</strong>
          <p>
            Lịch đến giờ sẽ xuất hiện tại đây. Bạn vẫn có thể xem tất cả lịch
            phía dưới.
          </p>
        </div>
      </div>

      <div v-if="queuePages > 1" class="queue-pagination">
        <button :disabled="queuePage <= 1" @click="queuePage--">Trước</button
        ><span>{{ Math.min(queuePage, queuePages) }} / {{ queuePages }}</span
        ><button :disabled="queuePage >= queuePages" @click="queuePage++">
          Sau
        </button>
      </div>
    </section>

    <div class="staff-source-tabs" role="group" aria-label="Loại booking">
      <button :class="{ active: !filters.source }" @click="filters.source = ''">
        Tất cả booking
      </button>
      <button
        :class="{ active: filters.source === 'online' }"
        @click="filters.source = 'online'"
      >
        Booking có tài khoản
      </button>
      <button
        :class="{ active: filters.source === 'walk-in' }"
        @click="filters.source = 'walk-in'"
      >
        Booking khách tại quầy
      </button>
    </div>
    <section class="list-panel">
      <div class="section-heading list-heading">
        <div>
          <h3>Tất cả lịch đặt sân</h3>
          <p>
            {{ filtered.length }} lịch phù hợp<span v-if="updated">
              · Cập nhật {{ updated }}</span
            >
          </p>
        </div>
        <button
          class="filter-toggle"
          :aria-expanded="expandedFilters"
          @click="expandedFilters = !expandedFilters"
        >
          ☷ {{ expandedFilters ? 'Thu gọn bộ lọc' : 'Bộ lọc nâng cao' }}
        </button>
      </div>

      <nav class="lane-tabs" aria-label="Nhóm trạng thái booking">
        <button
          v-for="item in lanes"
          :key="item.id"
          :class="{ selected: lane === item.id }"
          :aria-pressed="lane === item.id"
          @click="chooseLane(item.id)"
        >
          {{ item.label }}<span>{{ item.count }}</span>
        </button>
      </nav>

      <p class="hint">
        Danh sách lọc theo ngày đặt sân. Hàng chờ check-in phía trên phục vụ hôm
        nay; doanh thu theo ngày thu tiền xem tại Tổng quan.
      </p>
      <div class="search-row">
        <label class="search-box"
          ><span aria-hidden="true">⌕</span
          ><input
            v-model="filters.query"
            placeholder="Tìm P1-01, P1-02, tên khách, SĐT hoặc mã booking…"
            aria-label="Tìm booking" /></label
        ><button @click="today">Hôm nay</button
        ><button @click="resetAll">Xóa bộ lọc</button>
      </div>

      <div v-if="expandedFilters" class="advanced-filters">
        <label>Từ ngày<input v-model="filters.from" type="date" /></label
        ><label
          >Đến ngày<input
            v-model="filters.to"
            type="date"
            :min="filters.from" /></label
        ><label
          >Loại sân<select v-model="filters.type">
            <option value="">Tất cả loại sân</option>
            <option v-for="t in typeOptions" :key="t">{{ t }}</option>
          </select></label
        ><label
          >Trạng thái<select v-model="filters.status">
            <option value="">Tất cả trạng thái</option>
            <option
              v-for="(label, value) in bookingStatuses"
              :key="value"
              :value="value"
            >
              {{ label }}
            </option>
          </select></label
        ><label
          >Nguồn đặt<select v-model="filters.source">
            <option value="">Tất cả</option>
            <option value="online">Trực tuyến</option>
            <option value="walk-in">Khách tại quầy</option>
          </select></label
        ><label
          >Thanh toán<select v-model="filters.payment">
            <option value="">Tất cả</option>
            <option value="paid">Đã thu tiền</option>
            <option value="unpaid">Chưa thu tiền</option>
          </select></label
        >
      </div>
      <p
        v-if="filters.from && filters.to && filters.from > filters.to"
        class="message error"
      >
        Ngày kết thúc phải từ ngày bắt đầu trở đi.
      </p>

      <div class="table-wrap" :aria-busy="loading">
        <table>
          <thead>
            <tr>
              <th>Khách hàng / mã lịch</th>
              <th>Sân / phòng</th>
              <th>Ngày & giờ chơi</th>
              <th>Trạng thái</th>
              <th class="amount-cell">Tổng tiền</th>
              <th>Thanh toán</th>
              <th class="action-cell">Xử lý</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in shown" :key="b.id">
              <td>
                <div class="table-player">
                  <span class="avatar small-avatar">{{ initials(b) }}</span>
                  <div>
                    <strong>{{ customerName(b) }}</strong
                    ><small>{{ customerPhone(b) || '—' }} · #{{ b.id }}</small
                    ><small>{{
                      b.visitor ? 'Khách tại quầy' : 'Trực tuyến'
                    }}</small>
                  </div>
                </div>
              </td>
              <td>
                <strong class="court-chip">{{ b.court?.name || '—' }}</strong
                ><small
                  >{{ b.court?.room?.name || '—' }} ·
                  {{ typeName(b) || '—' }}</small
                >
              </td>
              <td>
                <strong>{{ time(b.startTime) }} – {{ time(b.endTime) }}</strong
                ><small>{{ date(b.bookingDate) }}</small>
              </td>
              <td>
                <span class="badge" :class="b.status"
                  ><i></i>{{ bookingStatuses[b.status] || b.status }}</span
                ><small
                  v-if="
                    b.status === 'PENDING' && bookingTime(b) + 30 * 60000 <= now
                  "
                  class="late-text"
                  >Quá giờ nhận sân</small
                >
              </td>
              <td class="amount-cell">
                <strong>{{ money(b.totalAmount) }}</strong>
              </td>
              <td>
                <span :class="b.paidAt ? 'paid' : 'unpaid'">{{
                  b.paidAt
                    ? '✓ Đã thu tiền'
                    : ['CANCELLED', 'NO_SHOW'].includes(b.status)
                      ? '—'
                      : 'Chưa thu tiền'
                }}</span
                ><small v-if="b.paidAt">{{
                  b.paymentMethod === 'CASH'
                    ? 'Tiền mặt'
                    : b.paymentMethod === 'BANK_TRANSFER'
                      ? 'Chuyển khoản'
                      : b.paymentMethod || '—'
                }}</small>
              </td>
              <td class="action-cell">
                <div class="row-actions">
                  <button
                    v-if="suggestedAction(b)"
                    :class="
                      suggestedAction(b) === 'settle' ? 'pay-button' : 'primary'
                    "
                    :disabled="saving || loading"
                    @click="ask(b, suggestedAction(b))"
                  >
                    {{ actionLabels[suggestedAction(b)] }}</button
                  ><button
                    class="detail-button"
                    :disabled="saving"
                    @click="openDetail(b)"
                  >
                    Chi tiết ↗
                  </button>
                  <button
                    v-if="b.visitor"
                    class="danger text-button"
                    :disabled="saving || loading || !!reason(b, 'cancel')"
                    :title="reason(b, 'cancel') || 'Hủy booking tại quầy'"
                    @click="ask(b, 'cancel')"
                  >
                    Hủy booking
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!shown.length">
              <td colspan="7" class="empty">
                {{
                  loading
                    ? 'Đang tải lịch đặt…'
                    : error && !rows.length
                      ? 'Chưa tải được dữ liệu.'
                      : 'Không có lịch đặt phù hợp với bộ lọc.'
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <span>{{ filtered.length }} kết quả</span
        ><label
          >Hiển thị<select v-model.number="pageSize">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select></label
        ><button :disabled="page <= 1" @click="page--">←</button
        ><span>{{ page }} / {{ pages }}</span
        ><button :disabled="page >= pages" @click="page++">→</button>
      </div>
    </section>

    <dialog
      ref="detailDialog"
      class="modal detail-modal"
      aria-labelledby="booking-detail-title"
      @cancel.prevent="closeDetail"
      @click="outside($event, closeDetail)"
    >
      <template v-if="detail"
        ><header class="modal-heading">
          <div>
            <span class="eyebrow">THÔNG TIN LỊCH ĐẶT</span>
            <h3 id="booking-detail-title">Booking #{{ detail.id }}</h3>
          </div>
          <button
            class="icon-button"
            :disabled="saving"
            @click="closeDetail"
            aria-label="Đóng chi tiết"
          >
            ✕
          </button>
        </header>
        <div class="detail-banner">
          <div>
            <span class="badge" :class="detail.status">{{
              bookingStatuses[detail.status] || detail.status
            }}</span>
            <h2>{{ detail.court?.name }}</h2>
            <p>{{ detail.court?.room?.name }} · {{ typeName(detail) }}</p>
          </div>
          <div class="detail-slot">
            <strong
              >{{ time(detail.startTime) }} – {{ time(detail.endTime) }}</strong
            ><span>{{ date(detail.bookingDate) }}</span>
          </div>
        </div>
        <div class="detail-player">
          <span class="avatar">{{ initials(detail) }}</span>
          <div>
            <strong>{{ customerName(detail) }}</strong
            ><small
              >{{ customerPhone(detail) || 'Chưa có SĐT' }} ·
              {{ detail.visitor ? 'Khách tại quầy' : 'Trực tuyến' }}</small
            >
          </div>
        </div>

        <div class="counter-toolbar detail-tools">
          <button
            v-if="detail.status === 'PENDING' && !detail.paidAt"
            @click="workOn('edit', detail)"
          >
            Sửa sân / lịch chơi</button
          ><button
            v-if="
              detail.status === 'CHECKED_IN' &&
              !detail.paidAt &&
              bookingTime(detail, true) > now
            "
            @click="workOn('extend', detail)"
          >
            Gia hạn giờ chơi</button
          ><button
            v-if="
              ['PENDING', 'CHECKED_IN', 'COMPLETED'].includes(detail.status) &&
              !detail.paidAt
            "
            @click="workOn('addon', detail)"
          >
            ＋ Thêm ống cầu vào booking</button
          ><button @click="workOn('sale', detail)">
            Bán cầu riêng cho khách này</button
          ><button @click="workOn('history', detail)">Hóa đơn cầu riêng</button>
        </div>
        <div class="operation-list">
          <div
            v-for="a in ['check-in', 'complete', 'settle']"
            :key="a"
            class="operation"
            :class="{ available: !reason(detail, a) }"
          >
            <span class="operation-number">{{
              a === 'check-in' ? '01' : a === 'complete' ? '02' : '03'
            }}</span
            ><button
              :class="a === 'settle' ? 'pay-button' : 'primary'"
              :disabled="saving || !!reason(detail, a)"
              @click="ask(detail, a)"
            >
              {{ actionLabels[a] }}</button
            ><small>{{ reason(detail, a) || 'Sẵn sàng thực hiện' }}</small>
          </div>
        </div>

        <div class="detail-columns">
          <section class="bill">
            <h4>Chi tiết hóa đơn</h4>
            <p v-if="receiptLoading" class="hint">Đang tải hóa đơn…</p>
            <p v-if="receiptError" class="message error">
              {{ receiptError }}
              <button @click="openDetail(detail)">Thử lại</button>
            </p>
            <p
              v-if="!['CHECKED_IN', 'COMPLETED'].includes(detail.status)"
              class="hint"
            >
              Tiền dự kiến từ lịch đặt; hóa đơn xuất cầu có sau khi nhận sân.
            </p>
            <p>
              Tiền sân<strong>{{
                money(
                  receipt?.courtAmount ??
                    (detail.totalAmount == null
                      ? null
                      : detail.totalAmount - (detail.shuttlecockAmount ?? 0)),
                )
              }}</strong>
            </p>
            <p>
              Tiền cầu<strong>{{
                money(receipt?.shuttlecockAmount ?? detail.shuttlecockAmount)
              }}</strong>
            </p>
            <div class="total-box">
              <span>Tổng hóa đơn</span
              ><strong>{{
                money(receipt?.totalAmount ?? detail.totalAmount)
              }}</strong>
            </div>
            <p v-if="!receipt && detail.shuttlecockProduct">
              {{ detail.shuttlecockProduct.name }} ·
              {{ detail.shuttlecockQuantityTubes }} ống
            </p>
            <div v-if="receipt?.items?.length" class="bill-items">
              <div
                v-for="item in receipt.items"
                :key="item.issueId ?? `reserved-${item.productId}`"
                :class="{ cancelled: item.cancelled }"
              >
                <span
                  >{{ item.productName
                  }}<small
                    >{{
                      item.quantityPieces
                        ? item.quantityPieces + ' quả'
                        : (item.quantityTubes || 0) + ' ống'
                    }}
                    · {{ money(item.unitPrice) }} /
                    {{ item.quantityPieces ? 'quả' : 'ống'
                    }}{{
                      item.cancelled
                        ? ' · Đã hủy'
                        : !item.issuedAt
                          ? ' · Đang giữ, chưa giao'
                          : ''
                    }}</small
                  ></span
                ><strong>{{ money(item.totalAmount) }}</strong>
              </div>
            </div>
            <div v-if="detail.paidAt" class="payment-summary">
              <span class="paid">✓ Đã thu tiền</span>
              <p>
                Phương thức<strong>{{
                  detail.paymentMethod === 'CASH'
                    ? 'Tiền mặt'
                    : detail.paymentMethod === 'BANK_TRANSFER'
                      ? 'Chuyển khoản'
                      : detail.paymentMethod || '—'
                }}</strong>
              </p>
              <p>
                Đã nhận<strong>{{ money(detail.amountReceived) }}</strong>
              </p>
              <p>
                Tiền trả khách<strong>{{
                  money(
                    receipt?.changeAmount ??
                      (detail.amountReceived == null ||
                      detail.totalAmount == null
                        ? null
                        : detail.amountReceived - detail.totalAmount),
                  )
                }}</strong>
              </p>
            </div>
          </section>
          <section class="timeline">
            <h4>Tiến trình xử lý</h4>
            <div>
              <i></i
              ><span
                >Đặt sân<small>{{ dateTime(detail.createdAt) }}</small></span
              >
            </div>
            <div :class="{ done: detail.checkedInAt }">
              <i></i
              ><span
                >Check-in nhận sân<small>{{
                  dateTime(detail.checkedInAt)
                }}</small></span
              >
            </div>
            <div :class="{ done: detail.completedAt }">
              <i></i
              ><span
                >Hoàn thành sân<small>{{
                  dateTime(detail.completedAt)
                }}</small></span
              >
            </div>
            <div :class="{ done: detail.paidAt }">
              <i></i
              ><span
                >Nhận tiền<small>{{ dateTime(detail.paidAt) }}</small></span
              >
            </div>
            <div v-if="detail.cancelledAt">
              <i></i
              ><span
                >Đã hủy<small
                  >{{ dateTime(detail.cancelledAt) }} ·
                  {{ detail.cancelledByStaffName || '—' }}</small
                ></span
              >
            </div>
          </section>
        </div>
        <footer class="detail-footer">
          <div>
            <button
              class="danger text-button"
              :disabled="saving || !!reason(detail, 'cancel')"
              @click="ask(detail, 'cancel')"
            >
              Hủy booking tại quầy</button
            ><small>{{ reason(detail, 'cancel') }}</small>
          </div>
          <button @click="closeDetail" :disabled="saving">Đóng</button>
        </footer></template
      >
    </dialog>

    <dialog
      ref="confirmDialog"
      class="modal confirm-modal"
      :class="
        confirmation?.action === 'settle' ? 'payment-modal' : 'checkin-modal'
      "
      aria-labelledby="confirm-booking-title"
      @cancel.prevent="closeConfirmation"
      @click="outside($event, closeConfirmation)"
    >
      <form v-if="confirmation" @submit.prevent="execute">
        <header class="modal-heading">
          <span class="eyebrow">{{
            confirmation.action === 'settle'
              ? 'THANH TOÁN HÓA ĐƠN'
              : 'XÁC NHẬN THAO TÁC'
          }}</span
          ><button
            type="button"
            class="icon-button"
            :disabled="saving"
            @click="closeConfirmation"
            aria-label="Đóng xác nhận"
          >
            ✕
          </button>
        </header>
        <div class="confirmation-symbol">
          {{
            confirmation.action === 'settle'
              ? '₫'
              : confirmation.action === 'cancel'
                ? '!'
                : '✓'
          }}
        </div>
        <h2 id="confirm-booking-title">
          {{
            confirmation.action === 'settle'
              ? 'Ghi nhận tiền đã nhận'
              : actionLabels[confirmation.action]
          }}
        </h2>
        <p class="confirmation-subtitle">
          {{
            confirmation.action === 'check-in'
              ? 'Kiểm tra đúng khách, đúng sân trước khi nhận.'
              : confirmation.action === 'settle'
                ? 'Xác nhận sau khi đã thực nhận tiền.'
                : 'Kiểm tra thông tin lịch trước khi xác nhận.'
          }}
        </p>
        <div class="confirmation-booking">
          <div class="player">
            <span class="avatar">{{ initials(confirmation.booking) }}</span>
            <div>
              <strong>{{ customerName(confirmation.booking) }}</strong
              ><small
                >{{ customerPhone(confirmation.booking) || '—' }} · #{{
                  confirmation.booking.id
                }}</small
              >
            </div>
          </div>
          <div class="confirmation-slot">
            <span
              >{{ confirmation.booking.court?.name
              }}<small
                >{{ confirmation.booking.court?.room?.name }} ·
                {{ typeName(confirmation.booking) }}</small
              ></span
            ><strong
              >{{ time(confirmation.booking.startTime) }} –
              {{ time(confirmation.booking.endTime)
              }}<small>{{
                date(confirmation.booking.bookingDate)
              }}</small></strong
            >
          </div>
        </div>

        <template v-if="confirmation.action === 'settle'"
          ><div class="amount-due">
            <span>TỔNG CẦN THU</span
            ><strong>{{ money(confirmation.booking.totalAmount) }}</strong>
          </div>
          <fieldset class="payment-methods" :disabled="saving">
            <legend>Chọn phương thức thanh toán</legend>
            <label :class="{ chosen: method === 'CASH' }"
              ><input
                type="radio"
                name="payment-method"
                value="CASH"
                :checked="method === 'CASH'"
                @change="setMethod('CASH')"
              /><span
                ><strong>₫ Tiền mặt</strong
                ><small>Nhận trực tiếp tại quầy</small></span
              ></label
            ><label :class="{ chosen: method === 'BANK_TRANSFER' }"
              ><input
                type="radio"
                name="payment-method"
                value="BANK_TRANSFER"
                :checked="method === 'BANK_TRANSFER'"
                @change="setMethod('BANK_TRANSFER')"
              /><span
                ><strong>▣ Chuyển khoản</strong
                ><small>Kiểm tra tiền vào tài khoản</small></span
              ></label
            >
          </fieldset>
          <label class="received-label"
            >Số tiền thực tế đã nhận
            <div class="money-input">
              <input
                v-model="received"
                type="number"
                :min="confirmation.booking.totalAmount"
                step="1"
                required
                :disabled="saving"
              /><span>VND</span>
            </div></label
          >
          <div v-if="method === 'CASH'" class="change-box">
            <span>Tiền trả lại khách</span><strong>{{ money(change) }}</strong>
          </div>
          <p v-else class="hint">
            Số tiền chuyển khoản phải bằng đúng tổng hóa đơn.
          </p></template
        >
        <p v-if="confirmation.action === 'cancel'" class="hint">
          Hủy lịch khách tại quầy và giải phóng phần cầu đang giữ; giữ lịch sử
          booking.
        </p>
        <p v-if="actionError" class="message error" role="alert">
          {{ actionError }}
        </p>
        <footer class="confirm-footer">
          <button type="button" :disabled="saving" @click="closeConfirmation">
            Quay lại</button
          ><button
            :class="confirmation.action === 'settle' ? 'pay-button' : 'primary'"
            :disabled="saving"
          >
            {{
              saving
                ? 'Đang xử lý…'
                : confirmation.action === 'settle'
                  ? '✓ Xác nhận đã nhận tiền'
                  : confirmation.action === 'check-in'
                    ? '✓ Xác nhận check-in'
                    : 'Xác nhận'
            }}
          </button>
        </footer>
      </form>
    </dialog>
  </div>
</template>

<style scoped>
.counter-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin: 18px 0;
}
.counter-toolbar span {
  font-size: 12px;
  color: #81756a;
}
.detail-tools {
  padding: 0 24px;
}
.counter-toolbar button {
  min-height: 40px;
  border: 1px solid #e8ddd0;
  border-radius: 10px;
  padding: 10px 14px;
  cursor: pointer;
}

.booking-workspace {
  --ink: #183c32;
  --green: #16734f;
  --line: #e3eae5;
  --muted: #74847c;
  color: var(--ink);
  font-family: inherit;
  max-width: 1500px;
  margin: auto;
  padding: 24px;
  background: #f5f7f4;
  border-radius: 24px;
}
* {
  box-sizing: border-box;
}
button,
input,
select {
  font: inherit;
}
button {
  cursor: pointer;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 15px;
  background: white;
  color: var(--ink);
  font-weight: 650;
  transition:
    * background * 0.15s,
    transform 0.15s;
}
button:hover:not(:disabled) {
  background: #edf5ee;
  transform: translateY(-1px);
}
button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
input,
select {
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 10px 12px;
  color: var(--ink);
  background: white;
  min-width: 0;
}
input:focus,
select:focus {
  outline: 2px solid #81c59f;
  outline-offset: 2px;
}
h2,
h3,
h4,
p {
  margin: 0;
}
small {
  display: block;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}
.hero {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 25px;
  background: linear-gradient(115deg, #103f31, #1c674b);
  border-radius: 20px;
  color: white;
  padding: 32px;
  margin-bottom: 20px;
}
.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 650px;
}
.eyebrow {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 2px;
  color: #93cbb0;
}
.hero h2 {
  font-size: clamp(24px, 3vw, 36px);
  line-height: 1.2;
  margin: 10px 0;
}
.hero p {
  color: #c1d9cc;
  font-size: 14px;
  line-height: 1.7;
}
.hero-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 20px;
  font-size: 12px;
  color: #d2e6d9;
}
.live-dot {
  width: 7px;
  height: 7px;
  background: #b6e48b;
  border-radius: 50%;
  box-shadow: 0 0 0 4px #ffffff12;
}
.meta-divider {
  opacity: 0.4;
}
.hero-refresh {
  position: relative;
  z-index: 1;
  background: #ffffff13;
  color: white;
  border-color: #ffffff30;
  white-space: nowrap;
}
.hero-refresh:hover:not(:disabled) {
  background: #ffffff25;
}
.hero-court {
  position: absolute;
  width: 350px;
  height: 190px;
  right: 110px;
  top: 0;
  transform: rotate(-20deg);
  opacity: 0.1;
  border: 3px solid white;
}
.court-line {
  position: absolute;
  background: white;
}
.court-line.middle {
  height: 3px;
  width: 100%;
  top: 50%;
}
.court-line.left {
  width: 3px;
  height: 100%;
  left: 25%;
}
.court-line.right {
  width: 3px;
  height: 100%;
  right: 25%;
}
.court-net {
  position: absolute;
  left: 50%;
  height: 100%;
  border-left: 3px dashed white;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 28px;
}
.metric {
  position: relative;
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 6px 12px;
  text-align: left;
  background: white;
  border: 1px solid var(--line);
  border-radius: 15px;
  padding: 20px;
}
.metric-icon {
  grid-row: span 3;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: #edf5ef;
  color: #28734d;
  font-size: 21px;
}
.metric > span:not(.metric-icon) {
  font-size: 12px;
  color: var(--muted);
}
.metric > div {
  display: grid;
  gap: 5px;
}
.metric > div > span {
  font-size: 11px;
  color: var(--muted);
}
.metric strong {
  font-size: 28px;
  letter-spacing: -1px;
}
.metric small {
  font-size: 11px;
}
.metric-arrow {
  position: absolute;
  right: 16px;
  top: 20px;
  color: #8c9b93;
}
.metric.amber .metric-icon {
  background: #fff2da;
  color: #ae741d;
}
.metric.blue .metric-icon {
  background: #eaf0ff;
  color: #456bb9;
}
.metric.revenue strong {
  font-size: 23px;
}
.section-heading,
.list-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}
.section-heading h3,
.list-heading h3 {
  font-size: 20px;
  letter-spacing: -0.4px;
}
.count-pill {
  display: inline-block;
  margin-left: 8px;
  background: #dcefdc;
  padding: 3px 9px;
  border-radius: 20px;
  font-size: 12px;
  color: #287745;
  vertical-align: middle;
}
.section-heading p,
.list-heading p {
  font-size: 12px;
  color: var(--muted);
  margin-top: 6px;
}
.arrival-notes {
  display: flex;
  gap: 10px;
  font-size: 11px;
  color: var(--muted);
}
.late-note {
  color: #b47a34;
}
.arrival-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
}
.arrival-card {
  background: white;
  border: 1px solid #cfdfcf;
  border-top: 3px solid #70a655;
  border-radius: 14px;
  padding: 20px;
  box-shadow: 0 5px 14px #254c3110;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.ready-tag {
  font-size: 10px;
  font-weight: 750;
  background: #eef6e9;
  color: #527b32;
  padding: 5px 8px;
  border-radius: 6px;
}
.booking-id {
  font-size: 11px;
  color: #93a096;
}
.court-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}
.court-title > strong {
  font-size: 23px;
}
.court-title h4 {
  font-size: 23px;
}
.court-title span {
  font-size: 11px;
  color: var(--muted);
}
.player,
.table-player,
.detail-player {
  display: flex;
  align-items: center;
  gap: 10px;
}
.player strong,
.table-player strong {
  font-size: 13px;
}
.avatar {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #eef1e6;
  color: #71844b;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 750;
}
.small-avatar {
  width: 32px;
  height: 32px;
  font-size: 10px;
}
.slot-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid var(--line);
  padding-top: 16px;
  margin-top: 16px;
  font-size: 14px;
  gap: 8px;
}
.slot-dash {
  color: #9caaa0;
}
.deadline {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  color: #9a7839;
  margin: 10px 0 16px;
}
.card-actions {
  display: flex;
  gap: 8px;
}
.primary {
  background: var(--green);
  border-color: var(--green);
  color: white;
}
.primary:hover:not(:disabled) {
  background: #105c3e;
}
.checkin-button {
  flex: 1;
}
.icon-button {
  padding: 8px 12px;
  flex-shrink: 0;
}
.arrival-empty {
  display: flex;
  align-items: center;
  gap: 15px;
  background: #edf1e9;
  border: 1px dashed #ccd8c8;
  border-radius: 14px;
  padding: 25px;
}
.empty-symbol {
  font-size: 24px;
  color: #77966c;
}
.arrival-empty p {
  color: var(--muted);
  font-size: 12px;
  margin-top: 5px;
}
.queue-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  font-size: 12px;
}
.arrival-section {
  margin-bottom: 28px;
}
.list-panel {
  background: white;
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 22px;
}
.lane-tabs {
  display: flex;
  gap: 20px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 18px;
  overflow: auto;
}
.lane-tabs button {
  border: 0;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  padding: 12px 0;
  background: none;
  white-space: nowrap;
  font-size: 12px;
  color: var(--muted);
}
.lane-tabs button.selected {
  border-color: var(--green);
  color: var(--green);
}
.lane-tabs button span {
  margin-left: 5px;
  padding: 2px 6px;
  background: #f0f3ed;
  border-radius: 5px;
  font-size: 10px;
}
.search-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding-left: 12px;
  color: #8b9d92;
}
.search-box input {
  width: 100%;
  border: 0;
  background: transparent;
}
.search-row button,
.filter-toggle {
  font-size: 12px;
}
.advanced-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  background: #f6f8f4;
  padding: 15px;
  border-radius: 12px;
  margin-bottom: 16px;
}
.advanced-filters label {
  display: grid;
  gap: 6px;
  font-size: 11px;
  color: var(--muted);
  flex: 1;
  min-width: 130px;
}
.message {
  padding: 12px 16px;
  border-radius: 10px;
  margin: 12px 0;
  font-size: 13px;
}
.error {
  background: #fff0ee;
  color: #af463d;
}
.success {
  background: #e9f5ea;
  color: #287346;
}
.table-wrap {
  overflow: auto;
}
table {
  border-collapse: collapse;
  width: 100%;
  white-space: nowrap;
  text-align: left;
}
th {
  background: #f7f9f5;
  color: #819086;
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.5px;
  padding: 14px 12px;
}
td {
  padding: 17px 12px;
  border-bottom: 1px solid #edf1eb;
  font-size: 12px;
}
tbody tr:hover {
  background: #fcfdf9;
}
.court-chip {
  display: inline-block;
  background: #eef3e9;
  padding: 5px 8px;
  border-radius: 6px;
  color: #527549;
  font-weight: 750;
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: #edf0ed;
  color: #7c8980;
  padding: 5px 8px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 750;
}
.badge i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.badge.PENDING {
  background: #fff4df;
  color: #ac7a22;
}
.badge.CHECKED_IN {
  background: #e6f4ec;
  color: #28865a;
}
.badge.COMPLETED {
  background: #eaf0fc;
  color: #5879b4;
}
.badge.CANCELLED {
  background: #f6eae9;
  color: #b27871;
}
.badge.NO_SHOW {
  background: #edeaef;
  color: #8d7b93;
}
.late-text {
  color: #b78741;
}
.amount-cell strong {
  font-size: 13px;
}
.paid {
  color: #3c8b60;
}
.unpaid {
  color: #b58a40;
}
.row-actions {
  display: flex;
  gap: 7px;
  justify-content: flex-end;
}
.row-actions button {
  padding: 8px 10px;
  font-size: 11px;
}
.pay-button {
  background: #dfecfd;
  border-color: #bfd3ed;
  color: #315f9a;
}
.pay-button:hover:not(:disabled) {
  background: #ccdef7;
}
.detail-button {
  color: #768a7d;
}
.empty {
  text-align: center;
  padding: 40px;
  color: var(--muted);
}
.pagination {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  padding-top: 18px;
  font-size: 12px;
  color: var(--muted);
}
.pagination > span:first-child {
  margin-right: auto;
}
.pagination label {
  display: flex;
  align-items: center;
  gap: 7px;
}
.pagination select {
  padding: 5px 8px;
}
.pagination button {
  padding: 5px 11px;
}
.modal {
  border: 0;
  border-radius: 20px;
  padding: 0;
  box-shadow: 0 25px 90px #12291f40;
  color: var(--ink);
  max-height: 90vh;
  overflow: auto;
  width: min(850px, calc(100vw - 32px));
}
.modal::backdrop {
  background: #122c266b;
  backdrop-filter: blur(4px);
}
.modal-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  padding: 20px 25px;
  background: white;
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 2;
}
.modal-heading h3 {
  margin-top: 5px;
  font-size: 19px;
}
.modal-heading .eyebrow {
  color: #7c9887;
}
.detail-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  background: #f1f6ed;
  margin: 20px 25px 0;
  padding: 20px;
  border-radius: 13px;
}
.detail-banner h2 {
  font-size: 30px;
  margin: 9px 0 3px;
}
.detail-banner p {
  color: var(--muted);
  font-size: 12px;
}
.detail-slot {
  text-align: right;
  display: grid;
  gap: 8px;
}
.detail-slot strong {
  font-size: 21px;
}
.detail-slot span {
  font-size: 12px;
  color: var(--muted);
}
.detail-player {
  margin: 20px 25px;
}
.operation-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 0 25px 22px;
}
.operation {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fafbf8;
  display: grid;
  gap: 10px;
}
.operation.available {
  background: #f1f8ef;
  border-color: #b8d0b0;
}
.operation-number {
  color: #9baa9d;
  font-size: 11px;
  font-weight: 800;
}
.operation small {
  font-size: 10px;
}
.operation button {
  padding: 10px 6px;
  font-size: 12px;
}
.detail-columns {
  display: grid;
  grid-template-columns: 1.3fr 1fr;
  gap: 22px;
  margin: 0 25px 25px;
}
.detail-columns h4 {
  font-size: 14px;
  margin-bottom: 15px;
}
.bill,
.timeline {
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 18px;
}
.hint {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.7;
}
.total-box {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f2f6ee;
  padding: 15px;
  border-radius: 9px;
  margin-top: 12px;
  font-size: 12px;
}
.total-box strong {
  font-size: 21px;
  color: var(--green);
}
.bill-items {
  list-style: none;
  padding: 0;
  margin: 0;
}
.bill-items > div,
.bill-items li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--line);
}
.bill > p:not(.hint):not(.message),
.payment-summary > p {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 12px 0;
  font-size: 12px;
  color: var(--muted);
}
.bill > p strong,
.payment-summary > p strong {
  color: var(--ink);
}
.cancelled {
  text-decoration: line-through;
  opacity: 0.5;
}
.payment-summary {
  margin-top: 12px;
  font-size: 12px;
}
.timeline > div {
  border-left: 2px solid #e1e8df;
  padding: 0 0 20px 16px;
  font-size: 12px;
  color: #9caaa0;
  position: relative;
}
.timeline > div:before {
  content: '';
  position: absolute;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  left: -6px;
  top: 3px;
  background: #dce4d9;
}
.timeline > div.done {
  color: var(--ink);
}
.timeline > div.done:before {
  background: #579268;
}
.timeline > div:last-child {
  padding-bottom: 0;
}
.detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 25px;
  border-top: 1px solid var(--line);
  position: sticky;
  bottom: 0;
  background: white;
}
.danger {
  color: #ae5348;
  background: #fff5f3;
  border-color: #edd9d4;
}
.text-button {
  border: 0;
  color: var(--muted);
}
.confirm-modal {
  width: min(480px, calc(100vw - 32px));
}
.confirm-modal form {
  padding: 24px;
}
.confirm-modal .modal-heading {
  padding: 0 0 16px;
  position: static;
  border: 0;
}
.confirmation-symbol {
  width: 54px;
  height: 54px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  background: #edf5e9;
  color: #438345;
  font-size: 25px;
  margin: 0 auto 15px;
}
.payment-modal .confirmation-symbol {
  background: #eaf1fd;
  color: #446da7;
}
.confirm-modal h2 {
  text-align: center;
  font-size: 24px;
}
.confirmation-subtitle {
  text-align: center;
  color: var(--muted);
  font-size: 12px;
  margin: 8px 0 22px;
  line-height: 1.6;
}
.confirmation-booking {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f6f8f3;
  padding: 14px;
  border-radius: 12px;
}
.confirmation-booking strong {
  font-size: 13px;
}
.confirmation-slot {
  margin-left: auto;
  text-align: right;
  font-size: 12px;
}
.amount-due {
  display: grid;
  gap: 6px;
  text-align: center;
  margin: 25px 0;
}
.amount-due span {
  font-size: 10px;
  letter-spacing: 1.4px;
  color: var(--muted);
}
.amount-due strong {
  font-size: 36px;
  letter-spacing: -1px;
  color: #315f9a;
}
.payment-methods {
  padding: 0;
  margin: 0 0 20px;
  border: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.payment-methods legend {
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 10px;
}
.payment-methods label {
  border: 1px solid var(--line);
  border-radius: 11px;
  padding: 12px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  cursor: pointer;
}
.payment-methods label.chosen {
  background: #edf3ff;
  border-color: #82a6d2;
}
.payment-methods input {
  accent-color: #456fab;
  margin-top: 3px;
}
.payment-methods strong {
  font-size: 12px;
}
.payment-methods small {
  font-size: 10px;
}
.received-label {
  font-size: 12px;
  font-weight: 700;
}
.money-input {
  display: flex;
  border: 1px solid #cad9e8;
  border-radius: 10px;
  align-items: center;
  margin: 9px 0 12px;
  padding-right: 12px;
  background: #fff;
}
.money-input input {
  width: 100%;
  border: 0;
  font-size: 21px;
  font-weight: 750;
  padding: 14px;
}
.money-input span {
  font-size: 10px;
  color: var(--muted);
}
.change-box {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  background: #f0f6ec;
  border-radius: 9px;
  padding: 13px;
  color: #5c7d4b;
}
.confirm-footer {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}
.confirm-footer > button:last-child {
  flex: 1;
  padding: 13px;
  font-size: 12px;
}
.confirm-footer > button:first-child {
  font-size: 12px;
}

@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .arrival-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .arrival-notes {
    display: none;
  }
}

@media (max-width: 650px) {
  .booking-workspace {
    padding: 12px;
    border-radius: 14px;
  }
  .hero {
    padding: 24px 20px;
    align-items: flex-start;
    flex-direction: column;
  }
  .hero-refresh {
    font-size: 12px;
  }
  .metric-grid {
    gap: 8px;
  }
  .metric {
    padding: 13px;
    grid-template-columns: 1fr;
  }
  .metric-icon {
    display: none;
  }
  .metric > div {
    display: grid;
    gap: 5px;
  }
  .metric > div > span {
    font-size: 11px;
    color: var(--muted);
  }
  .metric strong {
    font-size: 25px;
  }
  .metric.revenue strong {
    font-size: 18px;
  }
  .metric small {
    font-size: 10px;
  }
  .arrival-grid {
    grid-template-columns: 1fr;
  }
  .list-panel {
    padding: 15px;
  }
  .section-heading h3,
  .list-heading h3 {
    font-size: 17px;
  }
  .filter-toggle {
    padding: 8px;
    font-size: 10px;
  }
  .lane-tabs {
    gap: 16px;
  }
  .search-row {
    flex-wrap: wrap;
  }
  .search-box {
    flex-basis: 100%;
  }
  .pagination {
    gap: 8px;
    font-size: 10px;
    flex-wrap: wrap;
  }
  .pagination label {
    margin-left: auto;
  }
  .detail-banner {
    margin: 15px 15px 0;
    padding: 15px;
    align-items: flex-start;
  }
  .detail-slot strong {
    font-size: 15px;
  }
  .detail-banner h2 {
    font-size: 24px;
  }
  .detail-player {
    margin: 15px;
  }
  .operation-list {
    margin: 0 15px 15px;
    gap: 6px;
  }
  .operation {
    padding: 9px;
  }
  .operation button {
    font-size: 10px;
  }
  .operation small {
    font-size: 9px;
  }
  .detail-columns {
    grid-template-columns: 1fr;
    margin: 0 15px 15px;
    gap: 12px;
  }
  .modal-heading {
    padding: 16px;
  }
  .detail-footer {
    padding: 12px 15px;
  }
  .confirm-modal form {
    padding: 20px;
  }
  .confirmation-slot {
    font-size: 10px;
  }
  .payment-methods label {
    padding: 10px 8px;
  }
  .confirm-footer {
    gap: 7px;
  }
  .confirm-footer > button:last-child {
    font-size: 11px;
  }
}
</style>

<style scoped>
.staff-source-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 24px 0;
}
.staff-source-tabs button {
  border: 1px solid #e4ddd4;
  padding: 12px 18px;
  border-radius: 12px;
  background: white;
  color: #655549;
  font-weight: 700;
  cursor: pointer;
}
.staff-source-tabs button.active {
  background: #312821;
  border-color: #312821;
  color: #fff4e5;
}
</style>

<style scoped>
.court-calendar {
  margin: 24px 0;
  padding: 24px;
  border: 1px solid #e4dfd3;
  border-radius: 20px;
  background: #fffdf8;
}
.calendar-heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
  flex-wrap: wrap;
}
.calendar-heading h3 {
  margin: 8px 0;
  font-size: 25px;
  color: #253b2e;
}
.calendar-heading p,
.calendar-footnote {
  color: #7c8177;
  font-size: 12px;
  line-height: 1.7;
}
.calendar-controls {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.calendar-controls input,
.calendar-controls button {
  padding: 10px 12px;
  border: 1px solid #deded2;
  border-radius: 9px;
  background: #fff;
  color: #354a3c;
}
.calendar-legend {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  margin: 18px 0;
  font-size: 11px;
  color: #5e6c60;
}
.calendar-legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.calendar-legend i {
  width: 12px;
  height: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
}
.calendar-scroll {
  overflow: auto;
  max-height: 560px;
  border: 1px solid #e6e5dd;
  border-radius: 12px;
}
.calendar-table {
  border-collapse: separate;
  border-spacing: 0;
  width: max-content;
}
.calendar-table th {
  padding: 12px 6px;
  font-size: 11px;
  background: #f3f1e8;
  color: #677064;
  border-bottom: 1px solid #e0dfd5;
}
.calendar-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
}
.calendar-table .calendar-court-col {
  position: sticky;
  left: 0;
  min-width: 190px;
  max-width: 190px;
  text-align: left;
  padding: 12px 16px;
  z-index: 1;
  background: #fffdf8;
  border-right: 1px solid #e0dfd5;
}
.calendar-table thead .calendar-court-col {
  z-index: 3;
  background: #f3f1e8;
}
.calendar-court-col strong {
  display: block;
  color: #304939;
  font-size: 13px;
}
.calendar-court-col small {
  display: block;
  margin-top: 5px;
  font-size: 10px;
  white-space: normal;
}
.calendar-table td {
  padding: 3px;
  border-bottom: 1px solid #efeee6;
}
.calendar-cell {
  width: 74px;
  min-height: 38px;
  border: 0;
  border-radius: 6px;
  font: inherit;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}
.calendar-cell:disabled {
  opacity: 1;
  cursor: default;
}
.calendar-cell:focus-visible {
  outline: 3px solid #e68b32;
  outline-offset: 1px;
}
.calendar-FREE {
  background: #f5f6ef;
  color: #8b9885;
}
.calendar-PENDING {
  background: #fff0c6;
  color: #9d690b;
}
.calendar-CHECKED_IN {
  background: #d3eddb;
  color: #28613c;
}
.calendar-COMPLETED {
  background: #dfe9f6;
  color: #416281;
}
.calendar-NO_SHOW_PENDING {
  background: #fde1db;
  color: #9a4935;
}
.calendar-DAILY {
  background: #e9def8;
  color: #75549e;
}
.calendar-MAINTENANCE {
  background: #ffe2cb;
  color: #a26934;
}
.calendar-INACTIVE,
.calendar-BUSY {
  background: #e7e7e7;
  color: #737373;
}
.calendar-empty {
  padding: 20px;
  color: #76816f;
}
</style>

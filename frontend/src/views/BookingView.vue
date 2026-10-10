<script setup>
import {
  computed,
  nextTick,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { buildGroups, groupKey } from '../services/customerPortalUtils.js';
import CourtImage from '../components/CourtImage.vue';
import { getCustomerData } from '../services/customerService.js';

const props = defineProps({
  auth: Object,
  page: { type: String, default: 'booking' },
  groupFilter: String,
  roomFilter: String,
  initialProduct: String,
  initialDate: String,
  initialStart: String,
  initialEnd: String,
  initialCourt: String,
});
const normalizeGroup = (value) =>
  ({ DAILYVISITOR: 'DAILY_VISITOR' })[
    String(value || '')
      .replace(/[^a-z0-9]/gi, '')
      .toUpperCase()
  ] || String(value || '').toUpperCase();
const group = ref(normalizeGroup(props.groupFilter));
const room = ref(props.roomFilter || '');
const catalogue = ref({ products: [], dailySchedules: [] });
const dailySessions = ref([]);
const purchaseMode = ref(props.initialProduct ? 'TUBE' : 'NONE');
const productId = ref(
  props.initialProduct ? Number(props.initialProduct) : null,
);
const purchaseQuantity = ref(1);
const acceptedRules = ref(false);
const groups = computed(() => buildGroups(schedule.value?.courts || [], true));
const filteredCourts = computed(() =>
  (schedule.value?.courts || []).filter(
    (c) =>
      (!group.value || groupKey(c) === group.value) &&
      groupKey(c) !== 'DAILY_VISITOR' &&
      (!room.value || String(c.roomId) === room.value),
  ),
);
const selectedProduct = computed(() =>
  catalogue.value.products.find((p) => p.id === productId.value),
);
const productChoices = computed(() =>
  catalogue.value.products.filter(
    (p) => p.availableQuantityTubes > 0 && p.tubePrice > 0,
  ),
);
const extraTotal = computed(() =>
  purchaseMode.value === 'TUBE'
    ? (selectedProduct.value?.tubePrice || 0) * purchaseQuantity.value
    : 0,
);
const purchaseLimit = computed(
  () => selectedProduct.value?.availableQuantityTubes || 0,
);
const purchaseHint = computed(() => {
  if (purchaseMode.value === 'NONE') return '';
  if (selectedCourts.value.length !== 1)
    return 'Mua cầu kèm booking cần chọn đúng một sân.';
  if (purchaseMode.value !== 'TUBE') return 'Chỉ hỗ trợ mua cầu theo ống.';
  if (!selectedProduct.value) return 'Vui lòng chọn loại cầu.';
  if (
    !Number.isInteger(purchaseQuantity.value) ||
    purchaseQuantity.value < 1 ||
    purchaseQuantity.value > purchaseLimit.value
  )
    return 'Số lượng mua vượt mức có thể đặt hoặc không hợp lệ.';
  return '';
});
const suggestions = computed(() => {
  if (
    !schedule.value ||
    group.value === 'DAILY_VISITOR' ||
    date.value > schedule.value.maxBookingDate
  )
    return [];
  const minutes = Math.max(60, duration.value || 60),
    rows = [];
  for (const c of filteredCourts.value) {
    if (!c.active || !c.price) continue;
    for (
      let begin = minute(c.price.openingTime);
      begin + minutes <= minute(c.price.closingTime);
      begin += 30
    ) {
      const toTime = (m) =>
        `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
      const start = instant(date.value, toTime(begin)),
        end = instant(date.value, toTime(begin + minutes));
      if (
        start <= clock.value ||
        c.busy.some(
          (b) =>
            start < (b.end ? new Date(`${b.end}+07:00`).getTime() : Infinity) &&
            end > new Date(`${b.start}+07:00`).getTime(),
        )
      )
        continue;
      rows.push({
        court: c,
        start: toTime(begin),
        end: toTime(begin + minutes),
      });
    }
  }
  return rows
    .sort(
      (a, b) =>
        a.start.localeCompare(b.start) ||
        a.court.name.localeCompare(b.court.name),
    )
    .slice(0, 6);
});
async function chooseSuggestion(suggestion) {
  startTime.value = suggestion.start;
  endTime.value = suggestion.end;
  await nextTick();
  selectedIds.value = [suggestion.court.id];
}
function selectGroup(key) {
  dailyLevel.value = '';
  dailySlotsOpen.value = false;
  group.value = normalizeGroup(key);
  room.value = '';
  selectedIds.value = [];
  acceptedRules.value = false;
}
async function loadCatalogue() {
  try {
    catalogue.value = await request('/api/courts/catalogue');
  } catch (e) {
    error.value = e.message;
  }
}
async function loadDaily() {
  const seq = ++dailyLoadSequence;
  dailyLoading.value = true;
  dailyError.value = '';
  try {
    const data = await request(
      `/api/courts/daily-options?date=${encodeURIComponent(date.value)}`,
    );
    if (seq === dailyLoadSequence) dailySessions.value = data.sessions || [];
  } catch (e) {
    if (seq === dailyLoadSequence) {
      dailySessions.value = [];
      dailyError.value = e.message;
    }
  } finally {
    if (seq === dailyLoadSequence) dailyLoading.value = false;
  }
}
async function registerDaily() {
  if (saving.value || !acceptedRules.value) return;
  saving.value = true;
  error.value = '';
  try {
    await request(
      `/api/daily-visitor-participants/register?sessionId=${dialog.value.session.id}`,
      { method: 'POST' },
    );
    dialog.value = null;
    navigate('my-bookings');
    message.value = 'Đã đăng ký một lượt Daily Visitor.';
    await Promise.allSettled([loadAccount(), loadDaily()]);
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
watch(
  () => props.groupFilter,
  (value) => {
    selectGroup(value || '');
  },
);
watch(
  () => props.roomFilter,
  (value) => {
    room.value = value || '';
    selectedIds.value = [];
  },
);
watch(
  () => props.initialProduct,
  (value) => {
    productId.value = value ? Number(value) : null;
    purchaseMode.value = value ? 'TUBE' : 'NONE';
  },
);
watch(
  () => props.initialDate,
  (value) => {
    if (value) date.value = value;
  },
);
watch(
  () => props.initialStart,
  (value) => {
    if (value) startTime.value = value;
  },
);
watch(
  () => props.initialEnd,
  (value) => {
    if (value) endTime.value = value;
  },
);
watch(
  () => props.initialCourt,
  () => loadSchedule(),
);
watch([purchaseMode, productId, purchaseQuantity], () => {
  acceptedRules.value = false;
});

const emit = defineEmits(['navigate', 'session-expired', 'profile-updated']);
const tabs = [
  { id: 'home', label: 'Trang chủ', icon: '01' },
  { id: 'booking', label: 'Đặt sân', icon: '02' },
  { id: 'my-bookings', label: 'Lịch đặt của tôi', icon: '03' },
  { id: 'history', label: 'Lịch sử đặt sân', icon: '04' },
  { id: 'profile', label: 'Hồ sơ cá nhân', icon: '05' },
];
const loggedIn = computed(() => props.auth?.user?.role === 'CUSTOMER');
const currentTab = computed(() =>
  props.page === 'courts' ? 'booking' : props.page,
);
const title = computed(
  () => tabs.find((t) => t.id === currentTab.value)?.label || 'Trang chủ',
);
const descriptions = {
  home: 'Một lịch chơi mới, một ngày nhiều năng lượng.',
  booking: 'Chọn ngày, giờ và sân phù hợp cho buổi chơi của bạn.',
  'my-bookings': 'Theo dõi lịch chơi sắp tới và các lượt đang nhận sân.',
  history: 'Xem lại những buổi chơi và trạng thái đặt sân của bạn.',
  profile: 'Quản lý thông tin và bảo mật tài khoản.',
};
const vnDate = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(
    new Date(),
  );
const date = ref(props.initialDate || vnDate());
const startTime = ref(props.initialStart || '18:00');
const endTime = ref(props.initialEnd || '19:00');
const schedule = ref(null);
const selectedIds = ref([]);
const bookings = ref([]);
const profile = ref(null);
const loadingSchedule = ref(false);
const loadingAccount = ref(false);
const scheduleError = ref('');
const accountError = ref('');
const message = ref('');
const error = ref('');
const saving = ref(false);
const search = ref('');
const statusFilter = ref('');
const dialog = ref(null);
const dialogElement = ref(null);
const profileForm = reactive({ fullName: '' });
const password = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});
const clock = ref(Date.now());

const halfHours = Array.from(
  { length: 48 },
  (_, i) =>
    `${String(Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`,
);
const bookingFormElement = ref(null);
const bookingFormOpen = ref(false);
const dailyLevel = ref('');
const searchHint = computed(() => {
  if (!date.value || date.value < vnDate())
    return 'Chọn ngày hôm nay hoặc ngày sắp tới.';
  if (
    schedule.value?.maxBookingDate &&
    date.value > schedule.value.maxBookingDate
  )
    return 'Ngày đã chọn vượt hạn đặt sân của backend.';
  if (
    group.value !== 'DAILY_VISITOR' &&
    (duration.value < 60 || duration.value % 30 !== 0)
  )
    return 'Giờ kết thúc phải sau giờ bắt đầu ít nhất 60 phút.';
  return '';
});
const dailySlotsOpen = ref(false);
const dailyError = ref('');
const dailyLoading = ref(false);
const waitlistStates = ref({});
const waitlistLoading = ref(false);
const waitlistError = ref('');
let waitlistSequence = 0;
let waitingTimer;
let waitingRefreshBusy = false;
function offerRemaining(session) {
  const value = waitlistStates.value[session.id]?.offerExpiresAt;
  if (!value) return 0;
  return (
    Math.max(
      0,
      Math.ceil((new Date(`${value}+07:00`).getTime() - clock.value) / 1000),
    ) || 0
  );
}
function offerCountdown(session) {
  const seconds = offerRemaining(session);
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}
function registeredDaily(session) {
  return (
    waitlistStates.value[session.id]?.status === 'CONFIRMED' ||
    bookings.value.some(
      (b) =>
        b.daily &&
        ['CONFIRMED', 'CHECKED_IN'].includes(b.status) &&
        b.sessionStatus !== 'CANCELLED' &&
        b.bookingDate === date.value &&
        b.court?.name === session.schedule?.court?.name &&
        time(b.startTime) === time(session.startTime) &&
        time(b.endTime) === time(session.endTime),
    )
  );
}
function dailyClosedReason(session) {
  if (session.status === 'CANCELLED') {
    if (session.cancelReason === 'NOT_ENOUGH_REGISTERED_PLAYERS')
      return 'Ca đã hủy vì không đủ người đăng ký.';
    if (session.cancelReason === 'NOT_ENOUGH_CHECKED_IN_PLAYERS')
      return 'Ca đã hủy vì không đủ người check-in.';
    return 'Ca đã bị hủy.';
  }
  if (session.status === 'CLOSED') return 'Ca đã đóng.';
  if (instant(date.value, session.startTime) <= clock.value)
    return 'Ca đã bắt đầu, không còn đăng ký.';
  return 'Ca không còn nhận đăng ký hoặc ngày chơi vượt hạn cho phép.';
}
let dailyLoadSequence = 0;
const normalizeLevel = (value) =>
  String(value || '')
    .trim()
    .toUpperCase()
    .replace(/\s/g, '');
const matchingDailySessions = computed(() =>
  dailySessions.value.filter(
    (s) => normalizeLevel(s.schedule?.skillLevel) === dailyLevel.value,
  ),
);
const dailyPrices = computed(() => [
  ...new Set(
    catalogue.value.dailySchedules
      .filter((s) => normalizeLevel(s.skillLevel) === dailyLevel.value)
      .map((s) => s.fixedFee)
      .filter((v) => v != null),
  ),
]);
async function showDailySlots() {
  dailySlotsOpen.value = true;
  await loadDaily();
  if (loggedIn.value) await refreshWaitlists();
}
async function refreshWaitlists() {
  const seq = ++waitlistSequence;
  const token = props.auth?.accessToken;
  const selectedDate = date.value;
  if (!loggedIn.value) {
    waitlistStates.value = {};
    waitlistLoading.value = false;
    return;
  }
  waitlistLoading.value = true;
  waitlistError.value = '';
  const sessions = [...matchingDailySessions.value];
  await Promise.allSettled(
    sessions.map(async (session) => {
      try {
        const result = await request(
          `/api/daily-visitor-waitlists/session/${session.id}/my`,
        );
        if (
          seq === waitlistSequence &&
          token === props.auth?.accessToken &&
          selectedDate === date.value
        )
          waitlistStates.value[session.id] = result;
      } catch (e) {
        if (
          seq !== waitlistSequence ||
          token !== props.auth?.accessToken ||
          selectedDate !== date.value
        )
          return;
        if (e.status === 404) delete waitlistStates.value[session.id];
        else
          waitlistError.value =
            e.message ||
            'Chưa tải được trạng thái danh sách chờ. Vui lòng làm mới.';
      }
    }),
  );
  if (seq === waitlistSequence) waitlistLoading.value = false;
}
async function refreshDailyWaiting() {
  if (
    !dailySlotsOpen.value ||
    saving.value ||
    waitingRefreshBusy ||
    document.hidden
  )
    return;
  waitingRefreshBusy = true;
  try {
    await refreshWaitlists();
    await loadDaily();
  } finally {
    waitingRefreshBusy = false;
  }
}
async function waitlistAction(session, action) {
  if (!loggedIn.value) {
    navigate('login');
    return;
  }
  if (saving.value) return;
  if (
    action === 'confirm' &&
    (offerRemaining(session) <= 0 || dailyUnavailable(session))
  ) {
    error.value =
      'Lời mời đã hết hạn hoặc ca không còn khả dụng. Vui lòng làm mới.';
    return;
  }
  if (
    action === 'join' &&
    (dailyUnavailable(session) ||
      registeredDaily(session) ||
      session.remainingSlots > 0 ||
      ['WAITING', 'OFFERED'].includes(waitlistStates.value[session.id]?.status))
  ) {
    error.value =
      'Ca này chưa phù hợp để tham gia danh sách chờ. Vui lòng làm mới.';
    return;
  }
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    const result = await request(
      `/api/daily-visitor-waitlists/session/${session.id}/${action}`,
      { method: action === 'leave' ? 'DELETE' : 'POST' },
    );
    waitlistStates.value[session.id] = result;
    message.value = result.message || 'Đã cập nhật danh sách chờ.';
    await refreshWaitlists();
    await loadDaily();
    if (action === 'confirm') await loadAccount();
  } catch (e) {
    error.value = e.message;
    await refreshWaitlists();
    await loadDaily();
  } finally {
    saving.value = false;
  }
}
function dailyUnavailable(session) {
  if (date.value < vnDate()) return true;
  if (
    schedule.value?.maxBookingDate &&
    date.value > schedule.value.maxBookingDate
  )
    return true;
  return (
    !['OPEN', 'FULL'].includes(session.status) ||
    instant(date.value, session.startTime) <= clock.value
  );
}
watch(dailyLevel, () => {
  ++waitlistSequence;
  waitlistLoading.value = false;
  waitlistStates.value = {};
  waitlistError.value = '';
  dailySlotsOpen.value = false;
});
function groupBenefits(g) {
  const descriptions = [
    ...new Set(g.courts.map((c) => c.typeDescription?.trim()).filter(Boolean)),
  ];
  return descriptions.length ? descriptions.join(' · ') : g.description;
}
async function searchSchedule() {
  if (searchHint.value) return;
  if (group.value !== 'DAILY_VISITOR') selectGroup('');
  await Promise.allSettled([loadSchedule(), loadDaily()]);
}
async function openBookingForm(c) {
  if (courtHint(c) || searchHint.value) return;
  selectedIds.value = [c.id];
  acceptedRules.value = false;
  bookingFormOpen.value = true;
  await nextTick();
  bookingFormElement.value?.showModal();
}
function reviewBooking() {
  if (
    searchHint.value ||
    purchaseHint.value ||
    !selectedCourts.value.length ||
    selectedCourts.value.some((c) => courtHint(c))
  )
    return;
  bookingFormElement.value?.close();
  if (!loggedIn.value) {
    navigate('login');
    return;
  }
  openDialog({ mode: 'book' });
}

let serverOffset = 0;
let timer;
let scheduleSequence = 0;
const controllers = new Set();
const statusLabels = {
  CONFIRMED: 'Đã đăng ký Daily Visitor',
  PENDING: 'Chờ nhận sân',
  NO_SHOW_PENDING: 'Trễ giờ nhận sân',
  CHECKED_IN: 'Đã nhận sân',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Vắng mặt',
};
const money = (value) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
    value || 0,
  );
const time = (value) => value?.slice(0, 5) || '—';
const formatDate = (value) =>
  value
    ? new Date(`${value}T00:00:00+07:00`).toLocaleDateString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
      })
    : '—';
const minute = (value) =>
  Number(value?.slice(0, 2)) * 60 + Number(value?.slice(3, 5));
const instant = (day, hour) =>
  new Date(`${day}T${time(hour)}:00+07:00`).getTime();
const liveStatuses = ['PENDING', 'NO_SHOW_PENDING', 'CHECKED_IN', 'CONFIRMED'];
const upcoming = computed(() =>
  bookings.value
    .filter((b) => liveStatuses.includes(b.status))
    .sort(
      (a, b) =>
        instant(a.bookingDate, a.startTime) -
        instant(b.bookingDate, b.startTime),
    ),
);
const history = computed(() =>
  bookings.value
    .filter((b) => !liveStatuses.includes(b.status))
    .sort(
      (a, b) =>
        instant(b.bookingDate, b.startTime) -
        instant(a.bookingDate, a.startTime),
    ),
);
const visibleBookings = computed(() =>
  (currentTab.value === 'history' ? history.value : upcoming.value).filter(
    (b) =>
      (!statusFilter.value || b.status === statusFilter.value) &&
      `${b.id} ${b.court?.name || ''} ${b.bookingDate}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
  ),
);
const selectedCourts = computed(() =>
  (schedule.value?.courts || []).filter((c) =>
    selectedIds.value.includes(c.id),
  ),
);
const duration = computed(
  () => minute(endTime.value) - minute(startTime.value),
);
const estimatedTotal = computed(
  () =>
    selectedCourts.value.reduce((sum, c) => sum + estimate(c), 0) +
    extraTotal.value,
);
const fullName = computed(
  () => profile.value?.fullName || props.auth?.user?.fullName || 'Bạn',
);
const initials = computed(() =>
  fullName.value
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((s) => s[0])
    .join('')
    .toUpperCase(),
);
const finishedCount = computed(
  () => history.value.filter((b) => b.status === 'COMPLETED').length,
);

async function request(path, options = {}) {
  const controller = new AbortController();
  controllers.add(controller);
  const timeout = window.setTimeout(() => controller.abort(), 15000);
  try {
    return await getCustomerData(
      path,
      props.auth?.accessToken || '',
      controller.signal,
      options,
    );
  } catch (e) {
    if (e.status === 401) emit('session-expired');
    if (e.name === 'AbortError')
      throw new Error('Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.');
    throw e;
  } finally {
    window.clearTimeout(timeout);
    controllers.delete(controller);
  }
}
async function loadSchedule() {
  const sequence = ++scheduleSequence;
  loadingSchedule.value = true;
  scheduleError.value = '';
  const previousSelected = [...selectedIds.value];
  if (!bookingFormOpen.value) selectedIds.value = [];
  schedule.value = null;
  try {
    const data = await request(
      `/api/courts/schedule?date=${encodeURIComponent(date.value)}`,
    );
    if (sequence === scheduleSequence) {
      schedule.value = data;
      if (bookingFormOpen.value)
        selectedIds.value = previousSelected.filter((id) =>
          data.courts.some((c) => c.id === id),
        );
      if (props.initialCourt) {
        const court = data.courts.find(
          (c) => String(c.id) === props.initialCourt,
        );
        if (court && !courtHint(court)) selectedIds.value = [court.id];
      }
      const serverNow = new Date(`${data.serverTime}+07:00`).getTime();
      if (Number.isFinite(serverNow)) {
        serverOffset = serverNow - Date.now();
        clock.value = Date.now() + serverOffset;
      }
    }
  } catch (e) {
    if (sequence === scheduleSequence) scheduleError.value = e.message;
  } finally {
    if (sequence === scheduleSequence) loadingSchedule.value = false;
  }
}
async function loadAccount() {
  if (!loggedIn.value) return;
  loadingAccount.value = true;
  accountError.value = '';
  const results = await Promise.allSettled([
    request('/api/bookings/my'),
    request('/api/users/me'),
    request('/api/daily-visitor-participants/my'),
  ]);
  bookings.value = [
    ...(results[0].status === 'fulfilled' ? results[0].value : []),
    ...(results[2].status === 'fulfilled' ? results[2].value : []),
  ];
  if (results[1].status === 'fulfilled') {
    profile.value = results[1].value;
    profileForm.fullName = profile.value.fullName || '';
  }
  accountError.value = results
    .filter((r) => r.status === 'rejected')
    .map((r) => r.reason.message)
    .join(' ');
  loadingAccount.value = false;
}
function courtHint(c) {
  if (date.value > schedule.value?.maxBookingDate)
    return 'Ngày chơi vượt hạn đặt sân';
  if (!c.active) return 'Sân tạm ngừng hoạt động';
  if (!c.price) return 'Chưa có bảng giá';
  if (duration.value < 60 || duration.value % 30 !== 0)
    return 'Chọn thời lượng ít nhất 60 phút';
  if (
    minute(startTime.value) < minute(c.price.openingTime) ||
    minute(endTime.value) > minute(c.price.closingTime)
  )
    return 'Ngoài giờ mở cửa';
  const start = instant(date.value, startTime.value);
  const end = instant(date.value, endTime.value);
  if (start <= clock.value) return 'Giờ chơi đã qua';
  const busy = c.busy.find(
    (b) =>
      start < (b.end ? new Date(`${b.end}+07:00`).getTime() : Infinity) &&
      end > new Date(`${b.start}+07:00`).getTime(),
  );
  return busy?.reason || '';
}
function estimate(c) {
  if (!c.price || duration.value <= 0) return 0;
  const start = minute(startTime.value),
    end = minute(endTime.value),
    peak = minute(c.price.peakStartTime);
  return (
    Math.floor(
      (Math.max(0, Math.min(end, peak) - start) * c.price.normalPricePerHour) /
        60,
    ) +
    Math.floor(
      (Math.max(0, end - Math.max(start, peak)) * c.price.peakPricePerHour) /
        60,
    )
  );
}
function toggleCourt(c) {
  if (courtHint(c) || saving.value) return;
  selectedIds.value = selectedIds.value.includes(c.id)
    ? selectedIds.value.filter((id) => id !== c.id)
    : [...selectedIds.value, c.id];
}
function canCancel(b) {
  return (
    ['PENDING', 'NO_SHOW_PENDING', 'CONFIRMED'].includes(b.status) &&
    (!b.daily || !['CANCELLED', 'CLOSED'].includes(b.sessionStatus)) &&
    instant(b.bookingDate, b.startTime) - clock.value > 30 * 60000
  );
}
function navigate(page) {
  search.value = '';
  statusFilter.value = '';
  message.value = '';
  error.value = '';
  emit('navigate', page);
}
function openDialog(value) {
  error.value = '';
  acceptedRules.value = false;
  dialog.value = value;
}
function closeDialog() {
  if (!saving.value) dialog.value = null;
}
function dialogKeys(event) {
  if (event.key === 'Escape') closeDialog();
  if (event.key !== 'Tab') return;
  const nodes = [
    ...dialogElement.value.querySelectorAll(
      'button:not(:disabled), input, select, [tabindex="0"]',
    ),
  ];
  if (!nodes.length) {
    event.preventDefault();
    return;
  }
  const first = nodes[0],
    last = nodes[nodes.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
let previousFocus;
watch(dialog, async (value) => {
  if (value) {
    previousFocus = document.activeElement;
    await nextTick();
    dialogElement.value?.focus();
  } else previousFocus?.focus();
});
async function submitBooking() {
  if (saving.value || !selectedCourts.value.length || !acceptedRules.value)
    return;
  if (purchaseHint.value) {
    error.value = purchaseHint.value;
    return;
  }
  if (selectedCourts.value.some((c) => courtHint(c))) {
    error.value = 'Khung giờ đã thay đổi. Vui lòng chọn lại.';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    const created = await request('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({
        courtIds: selectedIds.value,
        bookingDate: date.value,
        startTime: startTime.value,
        endTime: endTime.value,
        productId: purchaseMode.value === 'NONE' ? null : productId.value,
        quantityTubes:
          purchaseMode.value === 'TUBE' ? purchaseQuantity.value : 0,
        quantityPieces: 0,
      }),
    });
    dialog.value = null;
    navigate('my-bookings');
    message.value = `Đặt sân thành công${Array.isArray(created) ? ` · ${created.length} booking` : ''}. Bạn có thể xem chi tiết bên dưới.`;
    await Promise.allSettled([loadAccount(), loadSchedule()]);
  } catch (e) {
    error.value = e.message;
    await loadSchedule();
  } finally {
    saving.value = false;
  }
}
async function cancelBooking() {
  const b = dialog.value?.booking;
  if (saving.value || !b || !canCancel(b)) return;
  saving.value = true;
  error.value = '';
  try {
    await request(
      b.daily
        ? `/api/daily-visitor-participants/${b.id}/cancel`
        : `/api/bookings/${b.id}`,
      { method: 'DELETE' },
    );
    dialog.value = null;
    message.value = `Đã hủy booking #${b.id}.`;
    await Promise.allSettled([loadAccount(), loadSchedule()]);
  } catch (e) {
    error.value = e.message;
    await loadAccount();
  } finally {
    saving.value = false;
  }
}
async function saveProfile() {
  if (saving.value) return;
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    profile.value = await request('/api/users/me', {
      method: 'PATCH',
      body: JSON.stringify({ fullName: profileForm.fullName.trim() }),
    });
    emit('profile-updated', profile.value);
    message.value = 'Đã cập nhật thông tin cá nhân.';
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
async function changePassword() {
  if (saving.value) return;
  error.value = '';
  message.value = '';
  if (password.newPassword !== password.confirmPassword) {
    error.value = 'Mật khẩu xác nhận không khớp.';
    return;
  }
  saving.value = true;
  try {
    await request('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify(password),
    });
    Object.assign(password, {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    });
    message.value = 'Đổi mật khẩu thành công.';
  } catch (e) {
    error.value = e.message;
  } finally {
    saving.value = false;
  }
}
watch(date, () => {
  ++waitlistSequence;
  waitlistStates.value = {};
  waitlistError.value = '';
  waitlistLoading.value = false;
  dailySlotsOpen.value = false;
  loadSchedule();
  loadDaily();
  acceptedRules.value = false;
});
watch([startTime, endTime], () => {
  if (!bookingFormOpen.value) selectedIds.value = [];
  acceptedRules.value = false;
});
watch(
  () => props.auth?.accessToken,
  () => {
    ++waitlistSequence;
    waitlistStates.value = {};
    waitlistLoading.value = false;
    waitlistError.value = '';
    if (dailySlotsOpen.value) refreshWaitlists();
    bookings.value = [];
    profile.value = null;
    loadAccount();
    loadSchedule();
  },
);
onMounted(() => {
  loadSchedule();
  loadAccount();
  loadCatalogue();
  loadDaily();
  timer = window.setInterval(() => {
    clock.value = Date.now() + serverOffset;
  }, 1000);
  waitingTimer = window.setInterval(refreshDailyWaiting, 15000);
});
onUnmounted(() => {
  ++waitlistSequence;
  window.clearInterval(timer);
  window.clearInterval(waitingTimer);
  controllers.forEach((c) => c.abort());
});
</script>

<template>
  <div
    :class="['customer-shell', { 'booking-cinema': currentTab === 'booking' }]"
  >
    <section class="customer-main">
      <header v-if="currentTab === 'booking'" class="booking-hero">
        <div class="booking-hero-orbit" aria-hidden="true"></div>
        <div class="booking-hero-copy">
          <p class="hero-kicker"><span></span> CARROT / FIND YOUR COURT</p>
          <h1>Chọn lịch đẹp.<br /><em>Chơi hết mình.</em></h1>
          <p>
            Một buổi tập cùng đồng đội. Một trận đấu thật đã.<br />Tìm không
            gian và khung giờ dành riêng cho bạn.
          </p>
          <a href="#booking-search" class="hero-booking-link"
            >Bắt đầu tìm lịch <span aria-hidden="true">↓</span></a
          >
          <div class="hero-booking-meta">
            <span>04 PHÂN KHÚC SÂN</span><span>01 ĐAM MÊ CHUNG</span>
          </div>
        </div>
        <div class="booking-hero-art" aria-hidden="true">
          <span class="hero-art-label">YOUR COURT.<br />YOUR GAME.</span>
          <div class="hero-court-perspective">
            <svg viewBox="0 0 240 340">
              <rect x="15" y="15" width="210" height="310" />
              <path
                d="M35 15v310M205 15v310M15 170h210M15 105h210M15 235h210M120 15v90m0 130v90"
              /></svg
            ><span class="hero-net"></span>
          </div>
          <div class="hero-art-footer">
            <span>CARROT BADMINTON</span><span>LET'S PLAY ↗</span>
          </div>
        </div>
      </header>
      <header
        v-if="currentTab !== 'home' && currentTab !== 'booking'"
        class="customer-heading"
      >
        <div>
          <span class="customer-eyebrow">YOUR COURT · YOUR GAME</span>
          <h1>{{ title }}</h1>
          <p>{{ descriptions[currentTab] }}</p>
        </div>
        <span class="customer-role">{{
          loggedIn ? 'KHÁCH HÀNG' : 'KHÁCH THAM QUAN'
        }}</span>
      </header>
      <div v-if="message" class="customer-alert success" role="status">
        {{ message }}
      </div>
      <div v-if="error && !dialog" class="customer-alert danger" role="alert">
        {{ error }}
      </div>
      <div v-if="accountError" class="customer-alert danger" role="alert">
        {{ accountError }}
        <button :disabled="loadingAccount" @click="loadAccount">Thử lại</button>
      </div>
      <template v-if="currentTab === 'booking'">
        <div class="booking-steps" aria-label="Quy trình đặt sân">
          <div>
            <span>01</span><strong>Chọn ngày & giờ</strong
            ><small>Chủ động lịch chơi</small>
          </div>
          <div>
            <span>02</span><strong>Chọn loại sân</strong
            ><small>Không gian phù hợp</small>
          </div>
          <div>
            <span>03</span><strong>Chọn sân / ca</strong
            ><small>Xem lựa chọn còn trống</small>
          </div>
          <div>
            <span>04</span><strong>Xác nhận đặt lịch</strong
            ><small>Sẵn sàng ra sân</small>
          </div>
        </div>
        <section id="booking-search" class="customer-card booking-search">
          <div class="customer-section-title">
            <div>
              <span class="customer-eyebrow">TÌM LỊCH CHƠI</span>
              <h2>Ngày bạn muốn ra sân.</h2>
              <p>
                Tìm theo ngày và giờ, sau đó chọn một trong bốn loại sân bên
                dưới.
              </p>
            </div>
          </div>
          <form class="booking-search-fields" @submit.prevent="searchSchedule">
            <label
              >Ngày chơi<input
                v-model="date"
                type="date"
                :min="vnDate()"
                :max="schedule?.maxBookingDate"
                required
                :disabled="saving"
            /></label>
            <label v-if="group !== 'DAILY_VISITOR'"
              >Giờ bắt đầu<select v-model="startTime" :disabled="saving">
                <option v-for="value in halfHours" :key="value" :value="value">
                  {{ value }}
                </option>
              </select></label
            >
            <label v-if="group !== 'DAILY_VISITOR'"
              >Giờ kết thúc<select v-model="endTime" :disabled="saving">
                <option v-for="value in halfHours" :key="value" :value="value">
                  {{ value }}
                </option>
              </select></label
            >
            <button
              type="submit"
              class="customer-button primary"
              :disabled="loadingSchedule || saving"
            >
              {{ loadingSchedule ? 'Đang tìm…' : 'Tìm lịch trống ↗' }}
            </button>
          </form>
          <p v-if="searchHint" class="customer-alert danger" role="alert">
            {{ searchHint }}
          </p>
          <p v-if="schedule?.maxBookingDate" class="booking-window">
            Được đặt đến {{ formatDate(schedule.maxBookingDate) }} theo quy định
            đặt sân. Sân thường tối thiểu 60 phút, giờ bắt đầu/kết thúc ở :00
            hoặc :30.
          </p>
          <div class="quick-court-types">
            <button
              v-for="g in groups"
              :key="g.key"
              type="button"
              :class="[
                { active: group === g.key },
                'type-' + g.key.toLowerCase(),
              ]"
              @click="selectGroup(g.key)"
            >
              <span class="quick-type-top"
                ><span class="quick-type-icon" aria-hidden="true">{{
                  g.key === 'PREMIUM'
                    ? '✦'
                    : g.key === 'GOLD'
                      ? '◈'
                      : g.key === 'BASIC'
                        ? '⚡'
                        : '🏸'
                }}</span
                ><span class="quick-type-arrow" aria-hidden="true"
                  >↗</span
                ></span
              ><strong>{{ g.name }}</strong
              ><small>{{
                g.key === 'DAILY_VISITOR'
                  ? 'Xem ca giao lưu'
                  : `${g.courts.filter((c) => !courtHint(c)).length} sân phù hợp`
              }}</small>
            </button>
          </div>
          <p v-if="scheduleError" class="customer-alert danger" role="alert">
            {{ scheduleError }}
          </p>
        </section>
        <div class="court-collections">
          <section
            v-for="(g, i) in groups"
            :key="g.key"
            class="court-collection"
            :class="[
              { expanded: group === g.key },
              'collection-' + g.key.toLowerCase(),
            ]"
          >
            <button
              type="button"
              class="collection-trigger"
              :aria-expanded="group === g.key"
              @click="selectGroup(group === g.key ? '' : g.key)"
            >
              <span class="collection-index">0{{ i + 1 }}</span
              ><span class="collection-title"
                ><small>{{
                  g.key === 'DAILY_VISITOR'
                    ? 'PLAY TOGETHER / GIAO LƯU'
                    : 'CARROT COURT COLLECTION'
                }}</small
                ><strong>{{ g.name }}</strong
                ><span>{{ groupBenefits(g) }}</span></span
              ><span v-if="g.key !== 'DAILY_VISITOR'" class="collection-count"
                >{{ g.courts.length }} sân · {{ g.rooms.length }} phòng</span
              ><span class="collection-arrow">{{
                group === g.key ? '−' : '+'
              }}</span>
            </button>
            <div v-if="group === g.key" class="collection-content">
              <p class="collection-benefits">{{ groupBenefits(g) }}</p>
              <template v-if="g.key === 'DAILY_VISITOR'">
                <p>
                  Chọn ca theo trình độ và lịch backend. Cầu dùng chung theo cấu
                  hình buổi chơi.
                </p>
                <p v-if="!dailyLevel" class="booking-window">
                  Chọn TBY, TB hoặc TB+ để xem các ca phù hợp.
                </p>
                <div class="daily-level-tabs">
                  <button
                    v-for="level in ['TBY', 'TB', 'TB+']"
                    :key="level"
                    type="button"
                    :class="{ active: dailyLevel === level }"
                    @click="dailyLevel = level"
                  >
                    {{ level }}
                  </button>
                </div>
                <section v-if="dailyLevel" class="daily-level-info">
                  <h3>Giao lưu trình độ {{ dailyLevel }}</h3>
                  <p v-if="dailyPrices.length">
                    Giá theo ca:
                    <strong>{{
                      dailyPrices.map((v) => money(v)).join(' · ')
                    }}</strong>
                  </p>
                  <p v-else>Giá sẽ hiển thị theo cấu hình của từng ca.</p>
                  <ul>
                    <li>Chọn đúng trình độ để giao lưu cùng nhóm phù hợp.</li>
                    <li>
                      Giờ bắt đầu và kết thúc cố định theo ca, không tự chọn
                      giờ.
                    </li>
                    <li>
                      Một tài khoản đăng ký một lượt. Cầu dùng chung theo cấu
                      hình buổi chơi.
                    </li>
                    <li>
                      Hủy theo nội quy sân. Khi ca đầy, có thể đăng ký chờ; chỉ
                      có chỗ sau khi được mời và xác nhận.
                    </li>
                  </ul>
                  <button
                    class="customer-button primary"
                    type="button"
                    @click="showDailySlots"
                  >
                    Đặt sân · Xem các ca ↗
                  </button>
                </section>
                <section v-if="dailySlotsOpen" class="daily-slot-panel">
                  <header>
                    <h3>Các ca {{ dailyLevel }} · {{ formatDate(date) }}</h3>
                    <button
                      type="button"
                      class="customer-button secondary"
                      @click="showDailySlots"
                      :disabled="dailyLoading || saving"
                    >
                      Làm mới
                    </button>
                  </header>
                  <p v-if="dailyLoading" role="status">Đang tải các ca…</p>
                  <p
                    v-if="dailyError"
                    class="customer-alert danger"
                    role="alert"
                  >
                    {{ dailyError }}
                  </p>
                  <div class="daily-waiting-guide">
                    <span aria-hidden="true">🏸</span>
                    <div>
                      <strong>Ca đầy? Bạn vẫn có thể đăng ký chờ.</strong>
                      <p>
                        Khi có chỗ trống, hệ thống mời theo thứ tự đăng ký và
                        báo ở chuông. Xác nhận đúng hạn để nhận slot; Waiting
                        chưa phải là booking thành công.
                      </p>
                    </div>
                    <button
                      class="customer-button secondary"
                      @click="navigate(loggedIn ? 'my-bookings' : 'login')"
                    >
                      Danh sách chờ của tôi →
                    </button>
                  </div>
                  <p
                    v-if="waitlistError"
                    class="customer-alert danger"
                    role="alert"
                  >
                    {{ waitlistError }}
                  </p>
                  <div class="daily-session-grid">
                    <article
                      v-for="session in matchingDailySessions"
                      :key="session.id"
                      :class="{
                        'waiting-card':
                          waitlistStates[session.id]?.status === 'WAITING',
                        'offered-card':
                          waitlistStates[session.id]?.status === 'OFFERED' &&
                          offerRemaining(session) > 0,
                      }"
                    >
                      <h3>{{ session.schedule?.court?.name }}</h3>
                      <span class="customer-tag">{{ dailyLevel }}</span>
                      <p>
                        {{ time(session.startTime) }} –
                        {{ time(session.endTime) }} · Giờ cố định
                      </p>
                      <strong>{{
                        session.fixedFee != null
                          ? money(session.fixedFee)
                          : 'Phí theo cấu hình buổi chơi'
                      }}</strong>
                      <p>
                        {{
                          session.remainingSlots > 0
                            ? `Còn ${session.remainingSlots} chỗ`
                            : 'Đã đủ chỗ'
                        }}
                        · {{ session.status }}
                      </p>
                      <p
                        v-if="dailyUnavailable(session)"
                        class="daily-state-note closed"
                      >
                        {{ dailyClosedReason(session) }}
                      </p>
                      <template
                        v-else-if="
                          waitlistStates[session.id]?.status === 'OFFERED'
                        "
                      >
                        <div class="daily-state-note offered">
                          <strong>✨ Đến lượt bạn nhận slot!</strong>
                          <p>
                            {{
                              offerRemaining(session) > 0
                                ? 'Còn ' +
                                  offerCountdown(session) +
                                  ' để xác nhận.'
                                : 'Lời mời đã hết hạn. Hãy làm mới để xem trạng thái.'
                            }}
                          </p>
                        </div>
                        <button
                          class="customer-button primary"
                          :disabled="
                            saving ||
                            waitlistLoading ||
                            offerRemaining(session) <= 0
                          "
                          @click="waitlistAction(session, 'confirm')"
                        >
                          Xác nhận nhận slot
                        </button>
                        <button
                          class="customer-button secondary"
                          :disabled="saving"
                          @click="waitlistAction(session, 'leave')"
                        >
                          Từ chối nhận slot
                        </button>
                      </template>
                      <template
                        v-else-if="
                          waitlistStates[session.id]?.status === 'WAITING'
                        "
                      >
                        <div class="daily-state-note waiting">
                          <strong
                            >Đang chờ · vị trí #{{
                              waitlistStates[session.id].position || '…'
                            }}</strong
                          >
                          <p>
                            Chỗ trống sẽ được ưu tiên theo hàng chờ. Bạn sẽ nhận
                            thông báo khi đến lượt.
                          </p>
                        </div>
                        <button
                          class="customer-button secondary"
                          :disabled="saving"
                          @click="waitlistAction(session, 'leave')"
                        >
                          Rời danh sách chờ
                        </button>
                      </template>
                      <template v-else-if="registeredDaily(session)">
                        <p class="daily-state-note registered">
                          ✓ Bạn đã đăng ký ca này.
                        </p>
                        <button
                          class="customer-button secondary"
                          @click="navigate('my-bookings')"
                        >
                          Xem lịch đặt của tôi →
                        </button>
                      </template>
                      <template v-else>
                        <p
                          v-if="
                            waitlistStates[session.id]?.status === 'EXPIRED'
                          "
                          class="daily-state-note closed"
                        >
                          Lời mời trước đã hết hạn. Quyền đăng ký lại được
                          backend kiểm tra.
                        </p>
                        <button
                          v-if="
                            session.status === 'OPEN' &&
                            session.remainingSlots > 0
                          "
                          class="customer-button primary"
                          :disabled="
                            saving ||
                            dailyLoading ||
                            waitlistLoading ||
                            !!waitlistError
                          "
                          @click="
                            loggedIn
                              ? openDialog({ mode: 'daily', session })
                              : navigate('login')
                          "
                        >
                          {{
                            loggedIn ? 'Đăng ký ca này' : 'Đăng nhập để đăng ký'
                          }}
                        </button>
                        <button
                          v-else
                          class="customer-button secondary waiting-join"
                          :disabled="
                            saving ||
                            dailyLoading ||
                            waitlistLoading ||
                            !!waitlistError
                          "
                          @click="waitlistAction(session, 'join')"
                        >
                          {{
                            waitlistLoading
                              ? 'Đang kiểm tra hàng chờ…'
                              : loggedIn
                                ? 'Tham gia danh sách chờ'
                                : 'Đăng nhập để vào danh sách chờ'
                          }}
                        </button>
                      </template>
                    </article>
                  </div>
                  <p
                    v-if="
                      !dailyLoading &&
                      !dailyError &&
                      !matchingDailySessions.length
                    "
                    class="customer-empty"
                  >
                    Chưa có ca {{ dailyLevel }} được mở cho ngày này. Lịch mẫu
                    không phải ca đã mở; nhân viên cần tạo ca theo nghiệp vụ
                    backend.
                  </p>
                </section>
              </template>
              <template v-else>
                <p v-if="!g.rooms.length" class="customer-empty">
                  Chưa có phòng/sân đang hoạt động trong phân khúc này.
                </p>
                <section v-for="r in g.rooms" :key="r.id" class="booking-room">
                  <header>
                    <div>
                      <small>{{ r.group }}</small>
                      <h3>{{ r.name }}</h3>
                    </div>
                    <span>{{ r.courts.length }} sân</span>
                  </header>
                  <div class="booking-court-list">
                    <article
                      v-for="c in r.courts"
                      :key="c.id"
                      class="booking-court-item"
                    >
                      <div>
                        <span class="customer-tag">{{ c.typeName }}</span>
                        <h4>{{ c.name }}</h4>
                        <p>
                          {{
                            c.typeDescription ||
                            'Thông tin tiện nghi đang cập nhật.'
                          }}
                        </p>
                        <small v-if="c.price"
                          >Giờ thường
                          {{ money(c.price.normalPricePerHour) }}/giờ · Cao điểm
                          {{ money(c.price.peakPricePerHour) }}/giờ<br />Mở cửa
                          {{ time(c.price.openingTime) }} –
                          {{ time(c.price.closingTime) }}</small
                        >
                      </div>
                      <div class="court-action">
                        <strong>{{
                          c.price ? money(estimate(c)) : 'Chưa có giá'
                        }}</strong
                        ><span :class="{ busy: !!courtHint(c) }">{{
                          courtHint(c) || 'Trống trong giờ đã chọn'
                        }}</span
                        ><button
                          type="button"
                          class="customer-button primary"
                          :disabled="!!courtHint(c) || !!searchHint || saving"
                          @click="openBookingForm(c)"
                        >
                          Đặt sân ↗
                        </button>
                      </div>
                      <details v-if="c.busy.length">
                        <summary>Xem các khoảng giờ đã kín / bảo trì</summary>
                        <p v-for="(period, n) in c.busy" :key="n">
                          {{ period.start?.slice(11, 16) }} –
                          {{ period.end?.slice(11, 16) || 'Chưa xác định' }} ·
                          {{ period.reason }}
                        </p>
                      </details>
                    </article>
                  </div>
                </section>
                <details v-if="suggestions.length" class="booking-suggestions">
                  <summary>Khung giờ trống gợi ý khác</summary>
                  <div class="suggestion-grid">
                    <button
                      v-for="item in suggestions"
                      :key="`${item.court.id}-${item.start}`"
                      :disabled="saving"
                      @click="chooseSuggestion(item)"
                    >
                      <strong>{{ item.court.name }}</strong
                      ><span>{{ item.start }} – {{ item.end }}</span>
                    </button>
                  </div>
                </details>
              </template>
            </div>
          </section>
        </div>
        <dialog
          ref="bookingFormElement"
          class="booking-form-dialog"
          @close="bookingFormOpen = false"
          @cancel="bookingFormOpen = false"
        >
          <form @submit.prevent="reviewBooking">
            <header class="booking-form-title">
              <div>
                <span class="customer-eyebrow">ĐẶT SÂN CỦA BẠN</span>
                <h2>{{ selectedCourts[0]?.name }}</h2>
                <p>
                  {{ selectedCourts[0]?.roomName }} ·
                  {{ selectedCourts[0]?.typeName }}
                </p>
              </div>
              <button
                type="button"
                aria-label="Đóng bảng đặt sân"
                @click="bookingFormElement.close()"
              >
                ×
              </button>
            </header>
            <div class="customer-form-grid">
              <label
                >Ngày chơi<input
                  v-model="date"
                  type="date"
                  :min="vnDate()"
                  :max="schedule?.maxBookingDate"
                  required /></label
              ><label
                >Bắt đầu<select v-model="startTime">
                  <option v-for="value in halfHours" :key="value">
                    {{ value }}
                  </option>
                </select></label
              ><label
                >Kết thúc<select v-model="endTime">
                  <option v-for="value in halfHours" :key="value">
                    {{ value }}
                  </option>
                </select></label
              >
            </div>
            <h3>Ống cầu cho buổi chơi</h3>
            <p>
              Không bắt buộc. Chỉ mua theo ống, không bán lẻ trên trang đặt sân.
            </p>
            <div class="customer-form-grid">
              <label
                >Mua kèm<select v-model="purchaseMode">
                  <option value="NONE">Không mua thêm</option>
                  <option value="TUBE">Thêm ống cầu</option>
                </select></label
              ><label v-if="purchaseMode === 'TUBE'"
                >Loại cầu<select v-model="productId">
                  <option :value="null">Chọn ống cầu</option>
                  <option v-for="p in productChoices" :key="p.id" :value="p.id">
                    {{ p.name }} · {{ money(p.tubePrice) }}/ống
                  </option>
                </select></label
              ><label v-if="purchaseMode === 'TUBE'"
                >Số ống<input
                  v-model.number="purchaseQuantity"
                  type="number"
                  min="1"
                  :max="purchaseLimit"
                  step="1"
                  required
              /></label>
            </div>
            <p v-if="!productChoices.length" class="booking-window">
              Hiện chưa có ống cầu còn hàng để chọn mua kèm.
            </p>
            <p
              v-if="
                purchaseHint ||
                searchHint ||
                selectedCourts.some((c) => courtHint(c))
              "
              class="customer-alert danger"
            >
              {{ purchaseHint || searchHint || courtHint(selectedCourts[0]) }}
            </p>
            <dl class="booking-form-total">
              <div>
                <dt>Tiền sân</dt>
                <dd>
                  {{
                    money(
                      selectedCourts.reduce((sum, c) => sum + estimate(c), 0),
                    )
                  }}
                </dd>
              </div>
              <div>
                <dt>Ống cầu</dt>
                <dd>{{ money(extraTotal) }}</dd>
              </div>
              <div>
                <dt>Tổng dự kiến</dt>
                <dd>{{ money(estimatedTotal) }}</dd>
              </div>
            </dl>
            <p class="booking-window">
              Giá và lịch được kiểm tra lại khi xác nhận. Cầu được giữ khi đặt
              thành công và xuất kho khi check-in.
            </p>
            <button
              type="submit"
              class="customer-button primary"
              :disabled="
                !selectedCourts.length ||
                !!purchaseHint ||
                !!searchHint ||
                loadingSchedule ||
                saving ||
                selectedCourts.some((c) => courtHint(c))
              "
            >
              {{
                loggedIn
                  ? 'Kiểm tra & xác nhận đặt sân'
                  : 'Đăng nhập để đặt sân'
              }}
            </button>
          </form>
        </dialog>
        <section class="customer-card booking-policies">
          <h2>Nội quy & chính sách.</h2>
          <ul>
            <li>
              Đặt sân thuê tối thiểu 60 phút, giờ bắt đầu/kết thúc ở :00 hoặc
              :30; chỉ đặt trong thời hạn hệ thống cho phép.
            </li>
            <li>
              Hủy booking chưa check-in khi còn hơn 30 phút trước giờ chơi.
              Booking tại quầy do nhân viên xử lý theo nghiệp vụ riêng.
            </li>
            <li>
              Sau 15 phút chưa nhận sân, booking chuyển sang chờ xác nhận vắng;
              sau 30 phút ghi nhận NO_SHOW và nhả sân.
            </li>
            <li>
              Vi phạm vắng mặt lần đầu: WARNING, không được đặt trong thời hạn 2
              ngày. Vi phạm tiếp theo: SUSPENDED, tài khoản bị khóa cho đến khi
              được xử lý theo nghiệp vụ quản trị.
            </li>
            <li>
              Đặt kèm cầu chỉ giữ hàng. Staff/admin xác nhận check-in mới xuất
              kho; hủy hoặc NO_SHOW trả phần hàng đã giữ.
            </li>
          </ul>
        </section>
      </template>
    </section>
    <Teleport to="body"
      ><div
        v-if="dialog"
        class="customer-modal-overlay"
        @click.self="closeDialog"
      >
        <section
          ref="dialogElement"
          class="customer-modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="customer-dialog-title"
          tabindex="-1"
          @keydown="dialogKeys"
        >
          <div class="customer-section-title">
            <h2 id="customer-dialog-title">
              {{
                dialog.mode === 'daily'
                  ? 'Xác nhận Daily Visitor'
                  : dialog.mode === 'book'
                    ? 'Xác nhận đặt sân'
                    : dialog.mode === 'cancel'
                      ? 'Hủy booking?'
                      : `Booking #${dialog.booking.id}`
              }}
            </h2>
            <button
              class="customer-modal-close"
              :disabled="saving"
              aria-label="Đóng"
              @click="closeDialog"
            >
              ×
            </button>
          </div>
          <div v-if="error" class="customer-alert danger" role="alert">
            {{ error }}
          </div>
          <template v-if="dialog.mode === 'daily'"
            ><p>
              {{ dialog.session.schedule?.court?.name }} ·
              {{ formatDate(date) }} · {{ time(dialog.session.startTime) }} –
              {{ time(dialog.session.endTime) }}
            </p>
            <label class="rules-checkbox"
              ><input
                v-model="acceptedRules"
                type="checkbox"
                :disabled="saving"
              />Tôi đã đọc nội quy và điều kiện hủy trước hơn 30 phút.</label
            >
            <div class="customer-modal-actions">
              <button
                class="customer-button secondary"
                :disabled="saving"
                @click="closeDialog"
              >
                Quay lại</button
              ><button
                class="customer-button primary"
                :disabled="saving || !acceptedRules"
                @click="registerDaily"
              >
                {{ saving ? 'Đang đăng ký…' : 'Xác nhận đăng ký' }}
              </button>
            </div></template
          >
          <template v-else-if="dialog.mode === 'book'"
            ><p>{{ selectedCourts.map((c) => c.name).join(', ') }}</p>
            <p>{{ formatDate(date) }} · {{ startTime }} – {{ endTime }}</p>
            <div class="customer-total">
              <span>Tổng dự kiến</span
              ><strong>{{ money(estimatedTotal) }}</strong>
            </div>
            <p>
              Mỗi sân sẽ tạo một booking riêng. Bạn có thể hủy từng booking khi
              còn hơn 30 phút trước giờ chơi.
            </p>
            <p v-if="purchaseMode !== 'NONE'">
              {{ selectedProduct?.name }} · {{ purchaseQuantity }} ống ·
              {{ money(extraTotal) }}
            </p>
            <label class="rules-checkbox"
              ><input
                v-model="acceptedRules"
                type="checkbox"
                :disabled="saving"
              />Tôi đã đọc nội quy và điều kiện hủy sân.</label
            >
            <div class="customer-modal-actions">
              <button
                class="customer-button secondary"
                :disabled="saving"
                @click="closeDialog"
              >
                Quay lại</button
              ><button
                class="customer-button primary"
                :disabled="
                  saving ||
                  !selectedCourts.length ||
                  !acceptedRules ||
                  !!purchaseHint
                "
                @click="submitBooking"
              >
                {{ saving ? 'Đang đặt…' : 'Xác nhận đặt sân' }}
              </button>
            </div></template
          ><template v-else
            ><dl class="customer-detail">
              <div>
                <dt>Sân</dt>
                <dd>{{ dialog.booking.court?.name }}</dd>
              </div>
              <div>
                <dt>Ngày chơi</dt>
                <dd>{{ formatDate(dialog.booking.bookingDate) }}</dd>
              </div>
              <div>
                <dt>Khung giờ</dt>
                <dd>
                  {{ time(dialog.booking.startTime) }} –
                  {{ time(dialog.booking.endTime) }}
                </dd>
              </div>
              <div>
                <dt>Trạng thái</dt>
                <dd>{{ statusLabels[dialog.booking.status] }}</dd>
              </div>
              <div v-if="dialog.booking.shuttlecockProduct">
                <dt>Cầu mua kèm</dt>
                <dd>
                  {{ dialog.booking.shuttlecockProduct.name }} ·
                  {{
                    dialog.booking.shuttlecockQuantityPieces ||
                    dialog.booking.shuttlecockQuantityTubes
                  }}
                  {{ dialog.booking.shuttlecockQuantityPieces ? 'quả' : 'ống' }}
                </dd>
              </div>
              <div>
                <dt>Thành tiền</dt>
                <dd>
                  {{
                    dialog.booking.daily && dialog.booking.totalAmount == null
                      ? 'Chờ chốt phí'
                      : money(dialog.booking.totalAmount)
                  }}
                </dd>
              </div>
            </dl>
            <template v-if="dialog.mode === 'cancel'"
              ><p>
                Sau khi hủy, sân sẽ được mở cho người khác đặt. Bạn cần tạo
                booking mới nếu muốn chơi lại.
              </p>
              <div class="customer-modal-actions">
                <button
                  class="customer-button secondary"
                  :disabled="saving"
                  @click="closeDialog"
                >
                  Giữ booking</button
                ><button
                  class="customer-button cancel"
                  :disabled="saving || !canCancel(dialog.booking)"
                  @click="cancelBooking"
                >
                  {{ saving ? 'Đang hủy…' : 'Xác nhận hủy' }}
                </button>
              </div></template
            >
            <div v-else class="customer-modal-actions">
              <button class="customer-button secondary" @click="closeDialog">
                Đóng</button
              ><button
                v-if="canCancel(dialog.booking)"
                class="customer-button cancel"
                @click="dialog.mode = 'cancel'"
              >
                Hủy booking
              </button>
            </div></template
          >
        </section>
      </div></Teleport
    >
  </div>
</template>

<style scoped>
.customer-shell {
  --ink: #17221c;
  --green: #005b35;
  --accent: #ff8500;
  --line: #e2e6df;
  max-width: 1320px;
  margin: 28px auto 70px;
  padding: 0 32px;
  color: var(--ink);
  font-family: inherit;
}
.customer-main {
  display: grid;
  gap: 28px;
  min-width: 0;
}
.customer-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 0 28px;
  border-bottom: 1px solid var(--line);
}
.customer-heading h1 {
  margin: 10px 0;
  font-size: clamp(30px, 4vw, 48px);
  font-weight: 900;
  letter-spacing: -1.8px;
  line-height: 1.08;
}
.customer-heading p {
  margin: 0;
  color: #778077;
  font-size: 14px;
  line-height: 1.7;
}
.customer-eyebrow {
  display: block;
  color: #6d786e;
  font-size: 10px;
  font-weight: 850;
  letter-spacing: 2px;
}
.customer-role,
.customer-tag {
  display: inline-block;
  padding: 9px 12px;
  background: #f1f4ee;
  color: #5b6b5e;
  border: 1px solid #dde4d8;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.8px;
  white-space: nowrap;
}
.customer-hero {
  position: relative;
  min-height: 550px;
  display: flex;
  align-items: center;
  overflow: hidden;
  background: #141c18;
  border-radius: 6px;
  isolation: isolate;
  color: white;
}
.customer-hero-photo {
  position: absolute;
  inset: 0 0 0 auto;
  width: 63%;
  height: 100%;
  object-fit: cover;
  object-position: 55% 30%;
  filter: grayscale(1) contrast(1.06);
  z-index: -3;
}
.customer-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -2;
  background: linear-gradient(
    90deg,
    #131c18 12%,
    #131c18f2 34%,
    #131c1860 67%,
    #131c1810
  );
}
.customer-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(0deg, #131c18a6, transparent 45%);
}
.customer-hero-content {
  position: relative;
  padding: 56px;
  width: min(650px, 65%);
  box-sizing: border-box;
}
.customer-hero .customer-eyebrow {
  color: #c9d1c8;
  display: flex;
  gap: 10px;
  align-items: center;
  letter-spacing: 2px;
  font-size: 9px;
}
.sport-dot {
  width: 7px;
  height: 7px;
  background: var(--accent);
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 14px #ff850070;
}
.sport-welcome {
  color: #a7b3a9;
  font-size: 12px;
  margin: 24px 0 12px;
}
.customer-hero h1 {
  font-size: clamp(52px, 6.8vw, 92px);
  font-weight: 950;
  letter-spacing: -5px;
  line-height: 0.98;
  margin: 0 0 26px;
  font-style: italic;
}
.customer-hero h1 span {
  color: var(--accent);
}
.sport-description {
  color: #b8c1b9;
  font-size: 13px;
  line-height: 1.9;
  max-width: 390px;
}
.sport-hero-actions {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: 32px;
  flex-wrap: wrap;
}
.customer-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
  min-height: 43px;
  padding: 12px 19px;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  line-height: 1.3;
  transition:
    background 0.15s,
    transform 0.15s;
}
.customer-button.primary {
  background: var(--green);
  color: white;
}
.customer-button.primary:hover:not(:disabled) {
  background: #00462a;
  transform: translateY(-1px);
}
.customer-hero .customer-button.primary {
  background: var(--accent);
  color: #192219;
  padding: 16px 23px;
  min-height: 52px;
  font-size: 12px;
  letter-spacing: 0.7px;
}
.customer-hero .customer-button.primary:hover {
  background: #ff9a29;
}
.sport-text-button {
  background: none;
  border: none;
  border-bottom: 1px solid #718074;
  padding: 8px 0;
  color: white;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.sport-text-button span {
  margin-left: 16px;
}
.sport-hero-caption {
  position: absolute;
  right: 32px;
  bottom: 30px;
  display: grid;
  text-align: right;
  gap: 6px;
  transform: rotate(-90deg);
  transform-origin: right bottom;
}
.sport-hero-caption span {
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 3px;
}
.sport-hero-caption small {
  font-size: 8px;
  color: #b8c3b8;
  letter-spacing: 2px;
}
.sport-hero-number {
  position: absolute;
  right: 34px;
  top: 27px;
  font-weight: 850;
  font-size: 12px;
  letter-spacing: 2px;
}
.sport-ribbon {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 18px;
  background: #e9efdc;
  color: #344327;
  gap: 14px;
  margin-top: -28px;
  border-radius: 0 0 5px 5px;
  font-size: 11px;
  letter-spacing: 2px;
  font-weight: 900;
}
.sport-ribbon b {
  color: #829365;
  font-size: 20px;
}
.customer-metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-bottom: 1px solid var(--line);
  padding: 10px 0 28px;
}
.customer-metrics article {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 7px 20px;
  padding: 8px 30px;
  border-right: 1px solid var(--line);
}
.customer-metrics article:first-child {
  padding-left: 0;
}
.customer-metrics article:last-child {
  border: 0;
}
.customer-metrics span {
  align-self: center;
  font-size: 12px;
  font-weight: 750;
}
.customer-metrics strong {
  grid-row: 1/3;
  grid-column: 2;
  align-self: center;
  font-size: 42px;
  line-height: 1;
  letter-spacing: -2px;
  font-weight: 900;
}
.customer-metrics small {
  color: #8a9389;
  font-size: 10px;
}
.customer-card {
  background: white;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 30px;
  min-width: 0;
}
.customer-card h2 {
  font-size: 25px;
  letter-spacing: -0.8px;
  margin: 8px 0 10px;
  line-height: 1.2;
  font-weight: 850;
}
.customer-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 24px;
}
.customer-section-title p,
.customer-muted {
  color: #7e877d;
  font-size: 12px;
  line-height: 1.7;
  margin: 0;
}
.customer-button.secondary {
  background: white;
  color: #344e3b;
  border-color: #d8dfd4;
}
.customer-button.secondary:hover:not(:disabled) {
  background: #f1f5ed;
}
.customer-button.cancel {
  color: #b65345;
  background: #fff5f2;
  border-color: #efd6cd;
}
.customer-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
}
.customer-shell button:focus-visible,
.customer-modal button:focus-visible,
.customer-shell input:focus-visible,
.customer-shell select:focus-visible {
  outline: 3px solid #ffab4d;
  outline-offset: 3px;
}
.customer-next {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #f5f7f1;
  border-left: 3px solid var(--green);
  padding: 22px;
}
.customer-next strong {
  font-size: 18px;
}
.customer-next p {
  margin: 8px 0 0;
  color: #7c8776;
  font-size: 12px;
}
.customer-next .customer-status {
  margin-left: auto;
}
.customer-date-block {
  padding: 14px 16px;
  background: white;
  font-size: 12px;
  font-weight: 800;
  color: #4d6849;
  border: 1px solid #e0e7d8;
}
.customer-status {
  display: inline-block;
  padding: 7px 10px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 750;
  white-space: nowrap;
  background: #edf0e8;
  color: #627056;
}
.customer-status.PENDING {
  background: #fff1cf;
  color: #967021;
}
.customer-status.CHECKED_IN,
.customer-status.COMPLETED {
  background: #e7f1e2;
  color: #477443;
}
.customer-status.NO_SHOW_PENDING,
.customer-status.NO_SHOW {
  background: #ffebe5;
  color: #af5845;
}
.customer-status.CANCELLED {
  background: #eff0ed;
  color: #858b80;
}
.customer-price-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}
.customer-price-grid article {
  position: relative;
  padding: 24px;
  background: #f7f8f4;
  border: 1px solid #e3e7dc;
  border-radius: 5px;
  overflow: hidden;
}
.customer-price-grid article::before {
  content: '';
  display: block;
  width: 30px;
  height: 3px;
  background: var(--accent);
  margin-bottom: 19px;
}
.customer-price-grid strong {
  font-size: 19px;
  font-weight: 850;
}
.customer-price-grid small {
  display: block;
  font-size: 10px;
  color: #879080;
  margin: 8px 0;
}
.customer-price-grid p {
  font-weight: 800;
  font-size: 20px;
  margin: 18px 0;
  letter-spacing: -0.6px;
}
.customer-price-grid p span {
  display: block;
  font-size: 10px;
  font-weight: 400;
  letter-spacing: 0;
  color: #84917a;
  margin-top: 5px;
}
.customer-form-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr;
  gap: 20px;
}
.customer-shell label {
  display: grid;
  gap: 10px;
  font-size: 12px;
  font-weight: 750;
  color: #57694f;
}
.customer-shell input,
.customer-shell select {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 46px;
  padding: 12px 14px;
  border: 1px solid #d9dfd2;
  background: #fafbf7;
  border-radius: 4px;
  font: inherit;
  font-size: 13px;
  color: #34492e;
}
.customer-shell input[readonly] {
  color: #8a9381;
  background: #f1f3ed;
}
.customer-booking-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 24px;
  align-items: start;
}
.customer-court-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.customer-court {
  padding: 23px;
  display: grid;
  gap: 13px;
  text-align: left;
  border: 1px solid #dbe3d2;
  border-radius: 6px;
  background: #fff;
  color: #304829;
  font: inherit;
  cursor: pointer;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.customer-court:hover:not(:disabled) {
  border-color: #6b8c59;
  background: #fafcf5;
}
.customer-court.selected {
  border: 2px solid #426b35;
  padding: 22px;
  background: #eef5e6;
}
.customer-court.unavailable {
  color: #979e8e;
  background: #f4f5f1;
  cursor: not-allowed;
}
.customer-court-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.customer-court-top strong {
  font-size: 20px;
}
.customer-court-top > span {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid #d0dcc1;
  border-radius: 50%;
}
.customer-court.selected .customer-court-top > span {
  background: #426b35;
  color: white;
}
.customer-court small {
  color: #89967c;
  font-size: 10px;
}
.customer-court-price {
  font-size: 25px;
  font-weight: 850;
  letter-spacing: -1px;
}
.customer-availability {
  font-size: 11px;
}
.customer-summary {
  position: sticky;
  top: 100px;
  border-top: 3px solid var(--accent);
  background: #fafbf7;
}
.customer-summary h2 {
  font-size: 23px;
}
.customer-summary dl,
.customer-detail {
  margin: 24px 0;
}
.customer-summary dl div,
.customer-detail div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 13px 0;
  border-bottom: 1px solid #e4e8dc;
  font-size: 12px;
}
.customer-summary dt,
.customer-detail dt {
  color: #8a947f;
}
.customer-summary dd,
.customer-detail dd {
  margin: 0;
  text-align: right;
  font-weight: 750;
  overflow-wrap: anywhere;
}
.customer-total {
  border-top: 1px solid #dfe5d4;
  padding-top: 20px;
  display: grid;
  gap: 10px;
}
.customer-total span {
  font-size: 11px;
  color: #809072;
}
.customer-total strong {
  font-size: 34px;
  font-weight: 900;
  letter-spacing: -1.3px;
}
.customer-summary p,
.customer-stack p {
  font-size: 11px;
  line-height: 1.9;
  color: #87947b;
}
.customer-summary .customer-button {
  width: 100%;
  margin-top: 16px;
}
.customer-filters {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
}
.customer-filters input {
  flex: 1;
}
.customer-filters select {
  width: 210px;
}
.customer-filters > span {
  font-size: 11px;
  color: #89947b;
  white-space: nowrap;
}
.customer-table-wrap {
  overflow: auto;
}
.customer-table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
  font-size: 12px;
}
.customer-table th {
  padding: 15px;
  text-align: left;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.7px;
  background: #f1f4eb;
  color: #7d8b70;
}
.customer-table td {
  padding: 22px 15px;
  border-bottom: 1px solid #e9eddf;
}
.customer-table small {
  display: block;
  margin-top: 7px;
  font-size: 11px;
  color: #89957b;
}
.customer-row-actions {
  display: grid;
  grid-template-columns: 80px 65px;
  gap: 8px;
}
.customer-row-actions .customer-button {
  padding: 8px;
  font-size: 11px;
  min-height: 36px;
}
.customer-profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}
.customer-profile-heading {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 26px;
}
.customer-avatar {
  display: grid;
  place-items: center;
  width: 65px;
  height: 65px;
  background: #18241a;
  color: #f4f8eb;
  font-size: 20px;
  font-weight: 850;
  border-radius: 6px;
  flex-shrink: 0;
}
.customer-stack {
  display: grid;
  gap: 20px;
  margin-top: 26px;
}
.customer-stack .customer-button {
  justify-self: start;
}
.customer-empty {
  padding: 44px 20px;
  text-align: center;
  color: #8b9582;
  font-size: 13px;
  line-height: 1.9;
}
.customer-empty .customer-button {
  display: flex;
  width: fit-content;
  margin: 20px auto 0;
}
.customer-alert {
  padding: 14px 18px;
  border: 1px solid;
  border-radius: 5px;
  font-size: 12px;
  line-height: 1.8;
}
.customer-alert.success {
  background: #edf5e6;
  color: #4c733b;
  border-color: #d4e6c6;
}
.customer-alert.danger {
  background: #fff1eb;
  color: #aa5b45;
  border-color: #f1d7ca;
}
.customer-alert button {
  background: none;
  border: 0;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
.customer-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: #14231799;
  display: grid;
  place-items: center;
  padding: 24px;
  backdrop-filter: blur(4px);
}
.customer-modal {
  box-sizing: border-box;
  width: min(520px, 100%);
  max-height: 85vh;
  overflow: auto;
  background: white;
  padding: 32px;
  border-radius: 8px;
  border-top: 4px solid #ff8500;
  color: #24351e;
  box-shadow: 0 25px 80px #0004;
  outline: none;
}
.customer-modal h2 {
  font-size: 24px;
  margin: 0;
  letter-spacing: -0.8px;
}
.customer-modal p {
  font-size: 13px;
  color: #7d8a71;
  line-height: 1.8;
}
.customer-modal .customer-alert {
  margin-bottom: 20px;
}
.customer-modal-close {
  width: 34px;
  height: 34px;
  border: 1px solid #dbe3d1;
  border-radius: 4px;
  background: #f7f9f3;
  color: #68785a;
  font-size: 22px;
  cursor: pointer;
}
.customer-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
}
@media (min-width: 1500px) {
  .customer-hero {
    min-height: 610px;
  }
  .customer-hero h1 {
    font-size: 98px;
  }
}
@media (max-width: 1000px) {
  .customer-shell {
    padding: 0 24px;
  }
  .customer-hero-content {
    padding: 40px;
    width: 72%;
  }
  .customer-hero {
    min-height: 480px;
  }
  .customer-hero h1 {
    font-size: 74px;
    letter-spacing: -4px;
  }
  .customer-booking-layout {
    grid-template-columns: 1fr;
  }
  .customer-summary {
    position: static;
  }
  .customer-price-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .customer-metrics article {
    padding: 8px 18px;
  }
  .customer-metrics strong {
    font-size: 34px;
  }
}
@media (max-width: 700px) {
  .customer-shell {
    padding: 0 16px;
    margin-top: 16px;
  }
  .customer-main {
    gap: 20px;
  }
  .customer-hero {
    min-height: 530px;
    align-items: flex-end;
  }
  .customer-hero-photo {
    width: 100%;
    object-position: 60% 25%;
  }
  .customer-hero::before {
    background: linear-gradient(
      0deg,
      #131c18 3%,
      #131c18df 35%,
      #131c1840 100%
    );
  }
  .customer-hero-content {
    padding: 30px 26px;
    width: 100%;
  }
  .customer-hero h1 {
    font-size: 64px;
    letter-spacing: -3px;
  }
  .customer-hero .customer-eyebrow {
    font-size: 8px;
    letter-spacing: 1.3px;
  }
  .sport-welcome {
    margin-top: 18px;
  }
  .sport-description {
    font-size: 12px;
  }
  .sport-hero-caption {
    display: none;
  }
  .sport-ribbon {
    margin-top: -20px;
    padding: 15px 10px;
    font-size: 8px;
    letter-spacing: 0.8px;
    gap: 8px;
  }
  .sport-ribbon b {
    font-size: 16px;
  }
  .customer-card {
    padding: 22px;
  }
  .customer-card h2 {
    font-size: 22px;
  }
  .customer-heading {
    align-items: start;
    flex-wrap: wrap;
  }
  .customer-heading h1 {
    font-size: 32px;
  }
  .customer-metrics article {
    display: block;
    padding: 5px 14px;
  }
  .customer-metrics span {
    display: block;
    font-size: 10px;
    min-height: 28px;
  }
  .customer-metrics strong {
    display: block;
    font-size: 34px;
    margin: 8px 0;
  }
  .customer-metrics small {
    display: block;
    line-height: 1.5;
    font-size: 9px;
  }
  .customer-next {
    flex-wrap: wrap;
    gap: 16px;
    padding: 18px;
  }
  .customer-next .customer-status {
    margin-left: 0;
  }
  .customer-profile-grid {
    grid-template-columns: 1fr;
  }
  .customer-filters {
    flex-wrap: wrap;
  }
  .customer-filters > span {
    width: 100%;
  }
  .customer-filters select {
    width: 175px;
  }
  .customer-form-grid {
    gap: 14px;
  }
  .customer-section-title {
    flex-wrap: wrap;
  }
}
@media (max-width: 450px) {
  .customer-shell {
    padding: 0 12px;
  }
  .customer-hero h1 {
    font-size: 56px;
  }
  .customer-hero-content {
    padding: 26px 22px;
  }
  .sport-hero-actions {
    gap: 18px;
  }
  .customer-hero .customer-button.primary {
    padding: 14px 18px;
    font-size: 11px;
  }
  .sport-text-button {
    font-size: 11px;
  }
  .customer-court-grid,
  .customer-price-grid {
    grid-template-columns: 1fr;
  }
  .customer-form-grid {
    grid-template-columns: 1fr 1fr;
  }
  .customer-form-grid label:first-child {
    grid-column: 1/-1;
  }
  .customer-filters input,
  .customer-filters select {
    width: 100%;
    flex: auto;
  }
  .customer-modal-overlay {
    padding: 12px;
  }
  .customer-modal {
    padding: 24px;
  }
  .customer-modal-actions {
    flex-wrap: wrap;
  }
  .customer-metrics strong {
    font-size: 29px;
  }
  .sport-ribbon {
    font-size: 7px;
    gap: 6px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .customer-button,
  .customer-court {
    transition: none;
  }
  .customer-button.primary:hover:not(:disabled) {
    transform: none;
  }
}

.customer-type-picker {
  padding: 28px;
  border: 1px solid #e0e6d6;
  border-radius: 6px;
  background: #f8faf3;
}
.customer-type-picker h2 {
  font-size: 26px;
  letter-spacing: -1px;
}
.type-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 22px 0;
}
.type-options button {
  padding: 16px 23px;
  border: 1px solid #d9e3cc;
  background: #fff;
  border-radius: 4px;
  color: #4c6a38;
  font: inherit;
  font-size: 13px;
  font-weight: 850;
  cursor: pointer;
}
.type-options button.active {
  background: #173b24;
  color: white;
  border-color: #173b24;
}
.type-options small {
  display: block;
  font-size: 9px;
  font-weight: 400;
  margin-top: 7px;
  opacity: 0.65;
}
.booking-window {
  font-size: 12px;
  color: #7f926b;
  line-height: 1.8;
}
.customer-type-picker label {
  max-width: 400px;
}
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.suggestion-grid button {
  display: grid;
  gap: 8px;
  text-align: left;
  border: 1px solid #d8e3ca;
  background: #f9fcf3;
  border-radius: 4px;
  padding: 17px;
  color: #3e632c;
  cursor: pointer;
}
.suggestion-grid span {
  font-size: 13px;
  font-weight: 850;
}
.suggestion-grid small {
  font-size: 9px;
  color: #8ba174;
}
.daily-session-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
  margin-top: 24px;
}
.daily-session-grid article {
  padding: 23px;
  border: 1px solid #dce7cf;
  border-radius: 5px;
}
.daily-session-grid h3 {
  font-size: 19px;
}
.daily-session-grid p {
  font-size: 12px;
  line-height: 1.8;
  color: #8ba174;
}
.rules-checkbox {
  display: flex !important;
  align-items: flex-start;
  gap: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #667f52;
}
.rules-checkbox input {
  width: 18px;
  height: 18px;
  min-height: 18px;
  flex-shrink: 0;
  margin-top: 2px;
  accent-color: #31572a;
}
.booking-policies ul {
  padding-left: 20px;
  color: #80966b;
  font-size: 12px;
  line-height: 1.9;
}
.booking-policies li {
  margin: 9px 0;
}
@media (max-width: 700px) {
  .suggestion-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .daily-session-grid {
    grid-template-columns: 1fr;
  }
  .customer-type-picker {
    padding: 20px;
  }
  .type-options button {
    padding: 13px 16px;
  }
}

.booking-search {
  background: #fffdf9;
  border-radius: 20px;
}
.booking-search-fields {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr auto;
  align-items: end;
  gap: 16px;
}
.booking-search-fields label {
  display: grid;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
}
.booking-search-fields input,
.booking-search-fields select {
  width: 100%;
  box-sizing: border-box;
  height: 46px;
  padding: 10px 12px;
  border: 1px solid #dce3d8;
  border-radius: 10px;
  background: #fff;
}
.quick-court-types {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 24px;
}
.quick-court-types button {
  display: grid;
  gap: 8px;
  text-align: left;
  padding: 18px;
  border: 1px solid #dce3d8;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
}
.quick-court-types button.active {
  background: #18251e;
  color: #fff;
}
.quick-court-types small {
  font-size: 11px;
}
.court-collections {
  display: grid;
  gap: 18px;
  margin-block: 24px;
}
.court-collection {
  overflow: hidden;
  background: #fff;
  border: 1px solid #e0e5da;
  border-radius: 20px;
}
.collection-trigger {
  display: flex;
  align-items: center;
  gap: 22px;
  width: 100%;
  text-align: left;
  border: 0;
  padding: 30px;
  background: #fff;
  cursor: pointer;
  color: #18251e;
}
.expanded .collection-trigger {
  background: #18251e;
  color: #fff;
}
.collection-index {
  font-size: 32px;
  font-weight: 800;
  opacity: 0.35;
}
.collection-title {
  display: grid;
  gap: 10px;
  flex: 1;
}
.collection-title small {
  font-size: 9px;
  letter-spacing: 1.5px;
  opacity: 0.6;
}
.collection-title strong {
  font-size: 30px;
}
.collection-title > span {
  font-size: 12px;
  line-height: 1.7;
}
.collection-count {
  font-size: 11px;
}
.collection-arrow {
  font-size: 28px;
  color: #e88b35;
}
.collection-content {
  padding: 28px;
}
.collection-benefits {
  color: #64725f;
  line-height: 1.8;
}
.booking-room {
  border: 1px solid #e3e8df;
  border-radius: 16px;
  margin-top: 20px;
  padding: 22px;
}
.booking-room header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
}
.booking-room h3 {
  margin: 8px 0;
}
.booking-room header small {
  color: #79866c;
}
.booking-court-list {
  display: grid;
  gap: 12px;
}
.booking-court-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  padding: 20px;
  background: #f7f8f3;
  border-radius: 12px;
}
.booking-court-item h4 {
  font-size: 20px;
  margin: 12px 0;
}
.booking-court-item p,
.booking-court-item small {
  font-size: 12px;
  color: #6c7863;
  line-height: 1.8;
}
.court-action {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}
.court-action strong {
  font-size: 22px;
}
.court-action span {
  font-size: 11px;
  color: #397151;
}
.court-action span.busy {
  color: #9b5c43;
}
.booking-court-item details {
  grid-column: 1/-1;
  font-size: 12px;
}
.booking-suggestions {
  margin-top: 22px;
}
.booking-suggestions summary,
.booking-court-item summary {
  cursor: pointer;
}
.daily-level-tabs {
  display: flex;
  gap: 12px;
  margin: 20px 0;
}
.daily-level-tabs button {
  padding: 12px 24px;
  background: #fff;
  border: 1px solid #dce3d8;
  border-radius: 10px;
  cursor: pointer;
}
.daily-level-tabs button.active {
  background: #18251e;
  color: #fff;
}
.booking-form-dialog {
  width: min(760px, calc(100% - 32px));
  max-height: calc(100dvh - 48px);
  overflow-y: auto;
  box-sizing: border-box;
  padding: 32px;
  border: 0;
  border-radius: 22px;
  color: #18251e;
  background: #fffdf9;
}
.booking-form-dialog::backdrop {
  background: #101a14b3;
}
.booking-form-title {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}
.booking-form-title h2 {
  margin: 10px 0;
}
.booking-form-title button {
  align-self: flex-start;
  border: 0;
  background: transparent;
  width: 40px;
  height: 40px;
  font-size: 28px;
  cursor: pointer;
}
.booking-form-total {
  padding: 18px;
  background: #f0f3e9;
  border-radius: 12px;
}
.booking-form-total div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 8px 0;
}
.booking-form-total dd {
  margin: 0;
  font-weight: 700;
}
.booking-form-total div:last-child {
  border-top: 1px solid #dce3d8;
  margin-top: 8px;
  font-size: 20px;
}
@media (max-width: 800px) {
  .booking-search-fields {
    grid-template-columns: repeat(2, 1fr);
  }
  .quick-court-types {
    grid-template-columns: repeat(2, 1fr);
  }
  .collection-trigger {
    padding: 22px;
    gap: 14px;
  }
  .collection-count {
    display: none;
  }
  .collection-content {
    padding: 18px;
  }
}
@media (max-width: 540px) {
  .booking-court-item {
    grid-template-columns: 1fr;
  }
  .court-action {
    align-items: stretch;
  }
  .booking-form-dialog {
    padding: 22px;
  }
  .collection-title strong {
    font-size: 25px;
  }
  .booking-room {
    padding: 16px;
  }
}

.daily-level-info {
  padding: 24px;
  border: 1px solid #e1e7da;
  border-radius: 16px;
  margin-top: 20px;
  background: #f8f9f3;
}
.daily-level-info li {
  line-height: 1.8;
  font-size: 13px;
  color: #5d6b54;
}
.daily-slot-panel {
  margin-top: 24px;
}
.daily-slot-panel > header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.daily-waiting-guide {
  display: flex;
  align-items: center;
  gap: 15px;
  margin: 18px 0;
  padding: 18px 20px;
  border: 1px solid #ddd0ec;
  border-radius: 15px;
  background: linear-gradient(120deg, #f3edfb, #fff8eb);
}
.daily-waiting-guide > span {
  font-size: 27px;
}
.daily-waiting-guide > div {
  flex: 1;
}
.daily-waiting-guide strong {
  color: #553774;
  font-size: 14px;
}
.daily-waiting-guide p {
  margin: 7px 0 0;
  color: #7c6e85;
  font-size: 12px;
  line-height: 1.7;
}
.booking-cinema .daily-session-grid > article.waiting-card {
  border-color: #cbb4df;
  background: #fcf9ff;
}
.booking-cinema .daily-session-grid > article.offered-card {
  border: 2px solid #e2a65e;
  background: #fffaf0;
  box-shadow: 0 10px 28px #c77d2220;
}
.daily-state-note {
  padding: 14px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.7;
}
.daily-state-note p {
  margin: 6px 0 0;
  font-size: 12px;
}
.daily-state-note.waiting {
  background: #f0e8f9;
  color: #654287;
}
.daily-state-note.offered {
  background: #fff0d7;
  color: #9b5d19;
}
.daily-state-note.registered {
  background: #edf3e7;
  color: #476232;
}
.daily-state-note.closed {
  background: #f2eeea;
  color: #796a60;
}
.daily-session-grid article > button {
  margin-top: 7px;
}
@media (max-width: 700px) {
  .daily-waiting-guide {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 16px;
  }
  .daily-waiting-guide > div {
    min-width: 180px;
  }
  .daily-waiting-guide > button {
    width: 100%;
  }
}
</style>

<style scoped>
.booking-cinema {
  --booking-ink: #30261d;
  --booking-muted: #8a7968;
  --booking-accent: #c58d4b;
}
.booking-cinema .customer-main {
  width: 100%;
  min-width: 0;
}
.booking-hero {
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 28px;
  overflow: hidden;
  padding: 52px 48px 34px;
  border-radius: 26px;
  background: linear-gradient(120deg, #211e19, #34271d);
  color: #fff8ed;
  margin-bottom: 22px;
}
.booking-hero-orbit {
  position: absolute;
  z-index: -1;
  width: 540px;
  height: 540px;
  border: 1px solid #b6814033;
  border-radius: 50%;
  right: -130px;
  top: -200px;
  box-shadow:
    0 0 0 90px #d99b4e08,
    0 0 0 180px #d99b4e04;
}
.hero-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 9px;
  letter-spacing: 2px;
  color: #c5a37b;
  font-weight: 800;
  margin: 0 0 22px;
}
.hero-kicker > span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #eb9e4e;
  box-shadow: 0 0 15px #efa444;
}
.booking-hero-copy h1 {
  font-size: clamp(34px, 4.3vw, 60px);
  letter-spacing: -2px;
  margin: 0;
  line-height: 1.15;
  font-weight: 850;
}
.booking-hero-copy h1 em {
  color: #efb778;
  font-style: normal;
}
.booking-hero-copy > p:not(.hero-kicker) {
  color: #bcae9f;
  font-size: 13px;
  line-height: 1.9;
  margin: 23px 0;
}
.hero-booking-link {
  display: inline-flex;
  gap: 28px;
  align-items: center;
  padding: 14px 20px;
  min-height: 47px;
  border-radius: 10px;
  background: #eda65b;
  color: #291b0e;
  font-size: 12px;
  font-weight: 800;
  text-decoration: none;
  transition:
    background 0.2s,
    transform 0.2s;
}
.hero-booking-link:hover {
  background: #ffc17c;
  transform: translateY(-3px);
}
.hero-booking-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-top: 30px;
  font-size: 8px;
  color: #ad9173;
  letter-spacing: 1.4px;
}
.booking-hero-art {
  position: relative;
  min-height: 295px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding-left: 20px;
}
.hero-art-label {
  display: block;
  font-size: 20px;
  line-height: 1.2;
  font-weight: 850;
  letter-spacing: 2px;
  color: #d1ad7d;
}
.hero-court-perspective {
  position: absolute;
  width: 200px;
  height: 280px;
  left: 50%;
  top: 24px;
  transform: translateX(-45%) perspective(800px) rotateX(45deg) rotateZ(-24deg);
  animation: booking-court-float 8s ease-in-out infinite;
}
.hero-court-perspective svg {
  width: 100%;
  height: 100%;
  fill: #bc85471a;
  stroke: #c59b63;
  stroke-width: 1.6;
  filter: drop-shadow(12px 22px 4px #0003);
}
.hero-net {
  position: absolute;
  left: 6%;
  right: 6%;
  top: 50%;
  height: 2px;
  background: #ffe4b2;
  box-shadow: 0 0 20px #efad5b70;
}
.hero-art-footer {
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 8px;
  letter-spacing: 1.5px;
  color: #a68e70;
}
.booking-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  padding: 20px 4px 28px;
}
.booking-steps > div {
  display: grid;
  grid-template-columns: 30px 1fr;
  gap: 4px 12px;
  align-items: center;
}
.booking-steps > div > span {
  grid-row: span 2;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid #e7d5bd;
  background: #faf4e9;
  color: #aa7b44;
  font-size: 10px;
  font-weight: 850;
  border-radius: 50%;
}
.booking-steps strong {
  font-size: 12px;
  color: #66513d;
}
.booking-steps small {
  font-size: 10px;
  color: #aa9681;
}
.booking-cinema .booking-search {
  border: 1px solid #e7dccb;
  padding: 30px;
  border-radius: 20px;
  background: #fffdf9;
  box-shadow: 0 12px 40px #6c4b2306;
  scroll-margin-top: 160px;
}
.booking-cinema .customer-eyebrow {
  color: #ae7c42;
  font-size: 9px;
  letter-spacing: 1.8px;
}
.booking-cinema .customer-section-title h2 {
  color: #3e3024;
  font-size: 28px;
  letter-spacing: -0.8px;
  margin: 10px 0;
}
.booking-cinema .customer-section-title p {
  color: #9c8670;
  font-size: 12px;
}
.booking-cinema .booking-search-fields {
  gap: 16px;
  align-items: end;
  margin-top: 26px;
}
.booking-cinema .booking-search-fields label {
  color: #846c54;
  font-size: 11px;
  font-weight: 700;
}
.booking-cinema .booking-search-fields input,
.booking-cinema .booking-search-fields select {
  height: 49px;
  border: 1px solid #e7dccb;
  border-radius: 10px;
  padding: 12px 14px;
  color: #5c4734;
  background: white;
  font-size: 14px;
}
.booking-cinema .booking-search-fields .primary {
  min-height: 49px;
}
.booking-cinema .customer-button {
  border-radius: 10px;
  min-height: 42px;
  transition:
    transform 0.2s,
    background 0.2s,
    box-shadow 0.2s;
  font-size: 11px;
}
.booking-cinema .customer-button.primary {
  background: #34271b;
  color: #fff4e5;
  border-color: #34271b;
}
.booking-cinema .customer-button.primary:hover:not(:disabled) {
  background: #7a5329;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px #68462215;
}
.booking-cinema .customer-button.secondary {
  background: #fff9f0;
  border-color: #e8d7be;
  color: #927044;
}
.booking-cinema .customer-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.booking-cinema .booking-window {
  font-size: 11px;
  color: #a58c72;
  padding: 12px 0;
  line-height: 1.8;
}
.booking-cinema .quick-court-types {
  gap: 12px;
  margin-top: 20px;
}
.booking-cinema .quick-court-types button {
  --type-accent: #b1844c;
  --type-light: #faf3e8;
  display: grid;
  gap: 12px;
  padding: 20px;
  border: 1px solid #e8dece;
  border-radius: 15px;
  background: white;
  color: #59452e;
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;
}
.booking-cinema .quick-court-types .type-gold {
  --type-accent: #a07933;
  --type-light: #fcf7e4;
}
.booking-cinema .quick-court-types .type-basic {
  --type-accent: #718a69;
  --type-light: #f0f5ec;
}
.booking-cinema .quick-court-types .type-daily_visitor {
  --type-accent: #8b76a4;
  --type-light: #f5eff9;
}
.quick-type-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.quick-type-icon {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: var(--type-light);
  color: var(--type-accent);
  border-radius: 9px;
  font-size: 18px;
}
.quick-type-arrow {
  color: #b5a089;
  font-size: 18px;
}
.booking-cinema .quick-court-types strong {
  font-size: 19px;
  letter-spacing: -0.4px;
}
.booking-cinema .quick-court-types small {
  color: #a18b74;
  font-size: 10px;
}
.booking-cinema .quick-court-types button:hover {
  transform: translateY(-4px);
  border-color: var(--type-accent);
  box-shadow: 0 12px 25px #5f432b0a;
}
.booking-cinema .quick-court-types button.active {
  border-color: var(--type-accent);
  background: var(--type-light);
  color: var(--type-accent);
  box-shadow: inset 0 0 0 1px var(--type-accent);
}
.booking-cinema .court-collections {
  gap: 18px;
  margin: 28px 0;
}
.booking-cinema .court-collection {
  --collection-accent: #b4864b;
  --collection-tint: #faf3e8;
  border: 1px solid #e8dfd1;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 6px 28px #3f2b1604;
}
.booking-cinema .collection-gold {
  --collection-accent: #a58039;
  --collection-tint: #fcf7e7;
}
.booking-cinema .collection-basic {
  --collection-accent: #788c6b;
  --collection-tint: #f2f6ed;
}
.booking-cinema .collection-daily_visitor {
  --collection-accent: #8976a3;
  --collection-tint: #f6f1f9;
}
.booking-cinema .collection-trigger {
  padding: 28px 30px;
  gap: 24px;
  color: #453324;
  background: linear-gradient(110deg, var(--collection-tint), white);
  min-height: 145px;
}
.booking-cinema .collection-trigger:hover {
  background: var(--collection-tint);
}
.booking-cinema .expanded .collection-trigger {
  background: linear-gradient(110deg, #30271f, #403227);
  color: #fff3df;
}
.booking-cinema .collection-index {
  font-size: 38px;
  font-weight: 850;
  opacity: 1;
  color: var(--collection-accent);
  letter-spacing: -2px;
}
.booking-cinema .collection-title {
  gap: 8px;
}
.booking-cinema .collection-title strong {
  font-size: clamp(24px, 2.9vw, 34px);
  letter-spacing: -0.9px;
}
.booking-cinema .collection-title small {
  font-size: 8px;
  letter-spacing: 2px;
  opacity: 0.7;
}
.booking-cinema .collection-title > span {
  max-width: 650px;
  font-size: 11px;
  line-height: 1.8;
  opacity: 0.75;
}
.booking-cinema .collection-count {
  font-size: 10px;
  border: 1px solid #b79a712e;
  border-radius: 24px;
  padding: 9px 12px;
  white-space: nowrap;
}
.booking-cinema .collection-arrow {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid #b79a7133;
  border-radius: 50%;
  color: var(--collection-accent);
  font-size: 24px;
  flex-shrink: 0;
}
.booking-cinema .collection-content {
  padding: 28px;
}
.booking-cinema .collection-benefits {
  background: var(--collection-tint);
  border-left: 3px solid var(--collection-accent);
  padding: 14px 18px;
  border-radius: 0 10px 10px 0;
  color: #8e785f;
  font-size: 12px;
  line-height: 1.9;
  margin: 0 0 24px;
}
.booking-cinema .booking-room {
  padding: 22px;
  border-color: #e9e0d4;
  border-radius: 16px;
  background: #fffcf8;
}
.booking-cinema .booking-room header {
  border-bottom: 1px solid #eee5d8;
  padding-bottom: 16px;
  margin-bottom: 18px;
}
.booking-cinema .booking-room header small {
  color: #ad8c62;
  letter-spacing: 1.5px;
  font-size: 9px;
}
.booking-cinema .booking-room h3 {
  color: #5b412a;
  font-size: 21px;
  letter-spacing: -0.5px;
}
.booking-cinema .booking-room header > span {
  color: #9a7b57;
  font-size: 10px;
  background: #f5ebdd;
  padding: 8px 11px;
  border-radius: 24px;
}
.booking-cinema .booking-court-item {
  position: relative;
  border: 1px solid #e8e0d3;
  background: #fff;
  border-radius: 14px;
  padding: 23px;
  gap: 22px;
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.booking-cinema .booking-court-item:hover {
  border-color: #c9ac80;
  box-shadow: 0 7px 20px #6e4b2108;
}
.booking-cinema .customer-tag {
  color: #ad8250;
  background: #faf1e3;
  border: 1px solid #eee0c8;
  padding: 6px 9px;
  border-radius: 6px;
  font-size: 8px;
  letter-spacing: 0.8px;
}
.booking-cinema .booking-court-item h4 {
  font-size: 29px;
  color: #3e2b1c;
  margin: 14px 0 10px;
  letter-spacing: -1px;
}
.booking-cinema .booking-court-item p {
  max-width: 580px;
  margin: 10px 0;
  color: #a18970;
  font-size: 11px;
  line-height: 1.85;
}
.booking-cinema .booking-court-item small {
  color: #977c5b;
  font-size: 10px;
}
.booking-cinema .court-action {
  padding-left: 24px;
  border-left: 1px solid #eee3d1;
  min-width: 190px;
  justify-content: center;
}
.booking-cinema .court-action strong {
  font-size: 26px;
  color: #a6793f;
  letter-spacing: -0.7px;
}
.booking-cinema .court-action span {
  font-size: 10px;
  color: #7f986f;
}
.booking-cinema .court-action span.busy {
  color: #b1866b;
  max-width: 220px;
  line-height: 1.6;
  text-align: right;
}
.booking-cinema .court-action .customer-button {
  width: 100%;
}
.booking-cinema .booking-court-item details {
  grid-column: 1 / -1;
  border-top: 1px dashed #e6d9c5;
  padding-top: 12px;
  font-size: 10px;
  color: #aa8962;
}
.booking-cinema .booking-suggestions {
  border: 1px dashed #decbaa;
  border-radius: 12px;
  padding: 18px;
  background: #fffcf6;
  color: #8f6b3e;
  margin-top: 20px;
}
.booking-cinema .daily-level-tabs {
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.booking-cinema .daily-level-tabs button {
  padding: 22px;
  border: 1px solid #e3d7ee;
  border-radius: 14px;
  background: #fbf8ff;
  color: #9680ac;
  font-size: 22px;
  font-weight: 850;
}
.booking-cinema .daily-level-tabs button.active {
  background: #5f4b75;
  border-color: #5f4b75;
  color: white;
  box-shadow: 0 8px 20px #715a8c15;
}
.booking-cinema .daily-level-info {
  background: #faf7fd;
  border-color: #e9dfef;
  border-radius: 15px;
  padding: 24px;
}
.booking-cinema .daily-level-info h3 {
  color: #725888;
  font-size: 23px;
  letter-spacing: -0.5px;
}
.booking-cinema .daily-level-info p,
.booking-cinema .daily-level-info li {
  color: #94839e;
  font-size: 12px;
  line-height: 1.9;
}
.booking-cinema .daily-session-grid > article {
  border: 1px solid #e5dcef;
  border-radius: 15px;
  background: #fff;
  padding: 23px;
  box-shadow: 0 6px 20px #6d4b8105;
}
.booking-cinema .daily-session-grid h3 {
  color: #735986;
}
.booking-cinema .daily-slot-panel {
  border-radius: 16px;
  background: #fcfaff;
  border-color: #e6dcee;
  padding: 24px;
}
.booking-cinema .booking-form-dialog {
  border: 1px solid #e5d5be;
  border-radius: 22px;
  padding: 30px;
  box-shadow: 0 30px 100px #32210f33;
  background: #fffdf8;
}
.booking-cinema .booking-form-dialog::backdrop {
  background: #21180f77;
  backdrop-filter: blur(6px);
}
.booking-cinema .booking-form-title {
  border-bottom: 1px solid #eadfcf;
  padding-bottom: 20px;
  margin-bottom: 24px;
}
.booking-cinema .booking-form-title h2 {
  font-size: 32px;
  color: #6a4829;
  letter-spacing: -1px;
}
.booking-cinema .booking-form-title p {
  color: #a68b6b;
  font-size: 12px;
}
.booking-cinema .booking-form-dialog h3 {
  color: #8e6c43;
  font-size: 17px;
}
.booking-cinema .booking-form-dialog p {
  font-size: 12px;
  line-height: 1.8;
  color: #9b8267;
}
.booking-cinema .booking-form-dialog label {
  color: #987d5e;
  font-size: 11px;
}
.booking-cinema .booking-form-dialog input,
.booking-cinema .booking-form-dialog select {
  background: white;
  border-color: #e8dac5;
  border-radius: 10px;
  min-height: 46px;
  color: #634a31;
}
.booking-cinema .booking-form-total {
  padding: 20px;
  background: #f7efdf;
  border-radius: 14px;
  color: #8f6b3c;
}
.booking-cinema .booking-form-total div:last-child {
  border-top: 1px solid #e2cba8;
}
.booking-cinema .booking-form-total div:last-child dd {
  color: #76511f;
  font-size: 27px;
  font-weight: 850;
}
.booking-cinema .booking-form-dialog form > .customer-button {
  width: 100%;
  min-height: 48px;
}
.booking-cinema .booking-policies {
  border: 1px solid #e6dccd;
  background: #fbf7f0;
  border-radius: 18px;
  padding: 28px;
}
.booking-cinema .booking-policies h2 {
  color: #775535;
  font-size: 22px;
  letter-spacing: -0.5px;
}
.booking-cinema .booking-policies li {
  color: #9d856b;
  font-size: 11px;
  line-height: 1.9;
  margin-bottom: 12px;
}
.booking-cinema .customer-empty {
  background: #faf6ee;
  color: #aa9278;
  border-radius: 12px;
}
@keyframes booking-court-float {
  0%,
  100% {
    transform: translateX(-45%) perspective(800px) rotateX(45deg)
      rotateZ(-24deg);
  }
  50% {
    transform: translateX(-45%) perspective(800px) rotateX(45deg)
      rotateZ(-24deg) translateY(-10px);
  }
}
@media (max-width: 1000px) {
  .booking-hero {
    padding: 38px 30px;
  }
  .booking-cinema .booking-court-item {
    grid-template-columns: 1fr auto;
  }
  .booking-cinema .court-action {
    min-width: 165px;
  }
  .booking-steps {
    gap: 10px;
  }
  .booking-steps strong {
    font-size: 11px;
  }
}
@media (max-width: 760px) {
  .booking-hero {
    grid-template-columns: 1fr;
    gap: 16px;
    padding: 34px 26px;
  }
  .booking-hero-art {
    min-height: 210px;
    padding-left: 0;
  }
  .hero-art-label {
    font-size: 16px;
  }
  .hero-court-perspective {
    width: 140px;
    height: 200px;
    top: 0;
  }
  .booking-hero-copy h1 {
    letter-spacing: -1.5px;
  }
  .booking-steps {
    grid-template-columns: repeat(2, 1fr);
    row-gap: 20px;
    padding: 16px 0 24px;
  }
  .booking-cinema .booking-search {
    padding: 23px;
  }
  .booking-cinema .booking-search-fields {
    grid-template-columns: 1fr 1fr;
  }
  .booking-cinema .booking-search-fields > label:first-child {
    grid-column: 1 / -1;
  }
  .booking-cinema .booking-search-fields > button {
    grid-column: 1 / -1;
    width: 100%;
  }
  .booking-cinema .quick-court-types {
    grid-template-columns: 1fr 1fr;
  }
  .booking-cinema .collection-trigger {
    padding: 24px 20px;
    gap: 14px;
  }
  .booking-cinema .collection-index {
    font-size: 28px;
  }
  .booking-cinema .collection-count {
    display: none;
  }
  .booking-cinema .collection-content {
    padding: 20px;
  }
  .booking-cinema .booking-room {
    padding: 18px;
  }
  .booking-cinema .booking-court-item {
    grid-template-columns: 1fr;
    padding: 20px;
  }
  .booking-cinema .court-action {
    border-left: 0;
    border-top: 1px solid #eee3d1;
    padding: 18px 0 0;
    align-items: start;
  }
  .booking-cinema .court-action span.busy {
    text-align: left;
    max-width: 100%;
  }
  .booking-cinema .court-action .customer-button {
    width: 100%;
  }
  .booking-cinema .booking-form-dialog {
    padding: 24px;
  }
  .booking-cinema .daily-slot-panel {
    padding: 18px;
  }
}
@media (max-width: 420px) {
  .booking-cinema .booking-search {
    padding: 20px 16px;
  }
  .booking-cinema .quick-court-types button {
    padding: 15px;
  }
  .booking-cinema .quick-court-types strong {
    font-size: 16px;
  }
  .booking-cinema .collection-title strong {
    font-size: 23px;
  }
  .booking-cinema .collection-title > span {
    font-size: 10px;
  }
  .booking-cinema .collection-index {
    display: none;
  }
  .booking-cinema .collection-content {
    padding: 16px;
  }
  .booking-cinema .daily-level-tabs button {
    padding: 16px;
    font-size: 20px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .hero-court-perspective {
    animation: none;
  }
  .booking-cinema .customer-button,
  .booking-cinema .quick-court-types button,
  .hero-booking-link,
  .booking-cinema .booking-court-item {
    transition: none;
  }
  .booking-cinema .customer-button:hover,
  .booking-cinema .quick-court-types button:hover,
  .hero-booking-link:hover {
    transform: none;
  }
}

.daily-waiting-guide {
  display: flex;
  align-items: center;
  gap: 15px;
  margin: 18px 0;
  padding: 18px 20px;
  border: 1px solid #ddd0ec;
  border-radius: 15px;
  background: linear-gradient(120deg, #f3edfb, #fff8eb);
}
.daily-waiting-guide > span {
  font-size: 27px;
}
.daily-waiting-guide > div {
  flex: 1;
}
.daily-waiting-guide strong {
  color: #553774;
  font-size: 14px;
}
.daily-waiting-guide p {
  margin: 7px 0 0;
  color: #7c6e85;
  font-size: 12px;
  line-height: 1.7;
}
.booking-cinema .daily-session-grid > article.waiting-card {
  border-color: #cbb4df;
  background: #fcf9ff;
}
.booking-cinema .daily-session-grid > article.offered-card {
  border: 2px solid #e2a65e;
  background: #fffaf0;
  box-shadow: 0 10px 28px #c77d2220;
}
.daily-state-note {
  padding: 14px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.7;
}
.daily-state-note p {
  margin: 6px 0 0;
  font-size: 12px;
}
.daily-state-note.waiting {
  background: #f0e8f9;
  color: #654287;
}
.daily-state-note.offered {
  background: #fff0d7;
  color: #9b5d19;
}
.daily-state-note.registered {
  background: #edf3e7;
  color: #476232;
}
.daily-state-note.closed {
  background: #f2eeea;
  color: #796a60;
}
.daily-session-grid article > button {
  margin-top: 7px;
}
@media (max-width: 700px) {
  .daily-waiting-guide {
    align-items: flex-start;
    flex-wrap: wrap;
    padding: 16px;
  }
  .daily-waiting-guide > div {
    min-width: 180px;
  }
  .daily-waiting-guide > button {
    width: 100%;
  }
}
</style>

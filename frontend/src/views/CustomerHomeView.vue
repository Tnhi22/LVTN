<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { getCustomerData } from '../services/customerService.js'

const props = defineProps({ auth: Object, page: { type: String, default: 'home' } })
const emit = defineEmits(['navigate', 'session-expired', 'profile-updated'])
const tabs = [
  { id: 'home', label: 'Trang chủ', icon: '01' },
  { id: 'booking', label: 'Đặt sân', icon: '02' },
  { id: 'my-bookings', label: 'Lịch đặt của tôi', icon: '03' },
  { id: 'history', label: 'Lịch sử đặt sân', icon: '04' },
  { id: 'profile', label: 'Hồ sơ cá nhân', icon: '05' },
]
const loggedIn = computed(() => props.auth?.user?.role === 'CUSTOMER')
const currentTab = computed(() => (props.page === 'courts' ? 'booking' : props.page))
const title = computed(
  () => tabs.find((t) => t.id === currentTab.value)?.label || 'Trang chủ',
)
const descriptions = {
  home: 'Một lịch chơi mới, một ngày nhiều năng lượng.',
  booking: 'Chọn ngày, giờ và sân phù hợp cho buổi chơi của bạn.',
  'my-bookings': 'Theo dõi lịch chơi sắp tới và các lượt đang nhận sân.',
  history: 'Xem lại những buổi chơi và trạng thái đặt sân của bạn.',
  profile: 'Quản lý thông tin và bảo mật tài khoản.',
}
const vnDate = () =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())
const date = ref(vnDate())
const startTime = ref('18:00')
const endTime = ref('19:00')
const schedule = ref(null)
const selectedIds = ref([])
const bookings = ref([])
const profile = ref(null)
const loadingSchedule = ref(false)
const loadingAccount = ref(false)
const scheduleError = ref('')
const accountError = ref('')
const message = ref('')
const error = ref('')
const saving = ref(false)
const search = ref('')
const statusFilter = ref('')
const dialog = ref(null)
const dialogElement = ref(null)
const profileForm = reactive({ fullName: '' })
const password = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const clock = ref(Date.now())
let serverOffset = 0
let timer
let scheduleSequence = 0
const controllers = new Set()
const statusLabels = {
  PENDING: 'Chờ nhận sân',
  NO_SHOW_PENDING: 'Trễ giờ nhận sân',
  CHECKED_IN: 'Đã nhận sân',
  COMPLETED: 'Hoàn tất',
  CANCELLED: 'Đã hủy',
  NO_SHOW: 'Vắng mặt',
}
const money = (value) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
    value || 0,
  )
const time = (value) => value?.slice(0, 5) || '—'
const formatDate = (value) =>
  value
    ? new Date(`${value}T00:00:00+07:00`).toLocaleDateString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
      })
    : '—'
const minute = (value) => Number(value?.slice(0, 2)) * 60 + Number(value?.slice(3, 5))
const instant = (day, hour) => new Date(`${day}T${time(hour)}:00+07:00`).getTime()
const liveStatuses = ['PENDING', 'NO_SHOW_PENDING', 'CHECKED_IN']
const upcoming = computed(() =>
  bookings.value
    .filter((b) => liveStatuses.includes(b.status))
    .sort(
      (a, b) => instant(a.bookingDate, a.startTime) - instant(b.bookingDate, b.startTime),
    ),
)
const history = computed(() =>
  bookings.value
    .filter((b) => !liveStatuses.includes(b.status))
    .sort(
      (a, b) => instant(b.bookingDate, b.startTime) - instant(a.bookingDate, a.startTime),
    ),
)
const visibleBookings = computed(() =>
  (currentTab.value === 'history' ? history.value : upcoming.value).filter(
    (b) =>
      (!statusFilter.value || b.status === statusFilter.value) &&
      `${b.id} ${b.court?.name || ''} ${b.bookingDate}`
        .toLowerCase()
        .includes(search.value.trim().toLowerCase()),
  ),
)
const selectedCourts = computed(() =>
  (schedule.value?.courts || []).filter((c) => selectedIds.value.includes(c.id)),
)
const duration = computed(() => minute(endTime.value) - minute(startTime.value))
const estimatedTotal = computed(() =>
  selectedCourts.value.reduce((sum, c) => sum + estimate(c), 0),
)
const fullName = computed(
  () => profile.value?.fullName || props.auth?.user?.fullName || 'Bạn',
)
const initials = computed(() =>
  fullName.value
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((s) => s[0])
    .join('')
    .toUpperCase(),
)
const finishedCount = computed(
  () => history.value.filter((b) => b.status === 'COMPLETED').length,
)

async function request(path, options = {}) {
  const controller = new AbortController()
  controllers.add(controller)
  const timeout = window.setTimeout(() => controller.abort(), 15000)
  try {
    return await getCustomerData(
      path,
      props.auth?.accessToken || '',
      controller.signal,
      options,
    )
  } catch (e) {
    if (e.status === 401) emit('session-expired')
    if (e.name === 'AbortError')
      throw new Error('Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.')
    throw e
  } finally {
    window.clearTimeout(timeout)
    controllers.delete(controller)
  }
}
async function loadSchedule() {
  const sequence = ++scheduleSequence
  loadingSchedule.value = true
  scheduleError.value = ''
  selectedIds.value = []
  schedule.value = null
  try {
    const data = await request(
      `/api/courts/schedule?date=${encodeURIComponent(date.value)}`,
    )
    if (sequence === scheduleSequence) {
      schedule.value = data
      const serverNow = new Date(`${data.serverTime}+07:00`).getTime()
      if (Number.isFinite(serverNow)) {
        serverOffset = serverNow - Date.now()
        clock.value = Date.now() + serverOffset
      }
    }
  } catch (e) {
    if (sequence === scheduleSequence) scheduleError.value = e.message
  } finally {
    if (sequence === scheduleSequence) loadingSchedule.value = false
  }
}
async function loadAccount() {
  if (!loggedIn.value) return
  loadingAccount.value = true
  accountError.value = ''
  const results = await Promise.allSettled([
    request('/api/bookings/my'),
    request('/api/users/me'),
  ])
  if (results[0].status === 'fulfilled') bookings.value = results[0].value
  if (results[1].status === 'fulfilled') {
    profile.value = results[1].value
    profileForm.fullName = profile.value.fullName || ''
  }
  accountError.value = results
    .filter((r) => r.status === 'rejected')
    .map((r) => r.reason.message)
    .join(' ')
  loadingAccount.value = false
}
function courtHint(c) {
  if (!c.active) return 'Sân tạm ngừng hoạt động'
  if (!c.price) return 'Chưa có bảng giá'
  if (duration.value < 60 || duration.value % 30 !== 0)
    return 'Chọn thời lượng ít nhất 60 phút'
  if (
    minute(startTime.value) < minute(c.price.openingTime) ||
    minute(endTime.value) > minute(c.price.closingTime)
  )
    return 'Ngoài giờ mở cửa'
  const start = instant(date.value, startTime.value)
  const end = instant(date.value, endTime.value)
  if (start <= clock.value) return 'Giờ chơi đã qua'
  const busy = c.busy.find(
    (b) =>
      start < (b.end ? new Date(`${b.end}+07:00`).getTime() : Infinity) &&
      end > new Date(`${b.start}+07:00`).getTime(),
  )
  return busy?.reason || ''
}
function estimate(c) {
  if (!c.price || duration.value <= 0) return 0
  const start = minute(startTime.value),
    end = minute(endTime.value),
    peak = minute(c.price.peakStartTime)
  return (
    Math.floor(
      (Math.max(0, Math.min(end, peak) - start) * c.price.normalPricePerHour) / 60,
    ) +
    Math.floor((Math.max(0, end - Math.max(start, peak)) * c.price.peakPricePerHour) / 60)
  )
}
function toggleCourt(c) {
  if (courtHint(c) || saving.value) return
  selectedIds.value = selectedIds.value.includes(c.id)
    ? selectedIds.value.filter((id) => id !== c.id)
    : [...selectedIds.value, c.id]
}
function canCancel(b) {
  return (
    ['PENDING', 'NO_SHOW_PENDING'].includes(b.status) &&
    instant(b.bookingDate, b.startTime) - clock.value > 30 * 60000
  )
}
function navigate(page) {
  search.value = ''
  statusFilter.value = ''
  message.value = ''
  error.value = ''
  emit('navigate', page)
}
function openDialog(value) {
  error.value = ''
  dialog.value = value
}
function closeDialog() {
  if (!saving.value) dialog.value = null
}
function dialogKeys(event) {
  if (event.key === 'Escape') closeDialog()
  if (event.key !== 'Tab') return
  const nodes = [
    ...dialogElement.value.querySelectorAll(
      'button:not(:disabled), input, select, [tabindex="0"]',
    ),
  ]
  if (!nodes.length) {
    event.preventDefault()
    return
  }
  const first = nodes[0],
    last = nodes[nodes.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
let previousFocus
watch(dialog, async (value) => {
  if (value) {
    previousFocus = document.activeElement
    await nextTick()
    dialogElement.value?.focus()
  } else previousFocus?.focus()
})
async function submitBooking() {
  if (saving.value || !selectedCourts.value.length) return
  if (selectedCourts.value.some((c) => courtHint(c))) {
    error.value = 'Khung giờ đã thay đổi. Vui lòng chọn lại.'
    return
  }
  saving.value = true
  error.value = ''
  try {
    const created = await request('/api/bookings', {
      method: 'POST',
      body: JSON.stringify({
        courtIds: selectedIds.value,
        bookingDate: date.value,
        startTime: startTime.value,
        endTime: endTime.value,
        quantityTubes: 0,
      }),
    })
    dialog.value = null
    navigate('my-bookings')
    message.value = `Đặt sân thành công${Array.isArray(created) ? ` · ${created.length} booking` : ''}. Bạn có thể xem chi tiết bên dưới.`
    await Promise.allSettled([loadAccount(), loadSchedule()])
  } catch (e) {
    error.value = e.message
    await loadSchedule()
  } finally {
    saving.value = false
  }
}
async function cancelBooking() {
  const b = dialog.value?.booking
  if (saving.value || !b || !canCancel(b)) return
  saving.value = true
  error.value = ''
  try {
    await request(`/api/bookings/${b.id}`, { method: 'DELETE' })
    dialog.value = null
    message.value = `Đã hủy booking #${b.id}.`
    await Promise.allSettled([loadAccount(), loadSchedule()])
  } catch (e) {
    error.value = e.message
    await loadAccount()
  } finally {
    saving.value = false
  }
}
async function saveProfile() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    profile.value = await request('/api/users/me', {
      method: 'PATCH',
      body: JSON.stringify({ fullName: profileForm.fullName.trim() }),
    })
    emit('profile-updated', profile.value)
    message.value = 'Đã cập nhật thông tin cá nhân.'
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
async function changePassword() {
  if (saving.value) return
  error.value = ''
  message.value = ''
  if (password.newPassword !== password.confirmPassword) {
    error.value = 'Mật khẩu xác nhận không khớp.'
    return
  }
  saving.value = true
  try {
    await request('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify(password),
    })
    Object.assign(password, {
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
    })
    message.value = 'Đổi mật khẩu thành công.'
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
watch(date, loadSchedule)
watch([startTime, endTime], () => {
  selectedIds.value = []
})
watch(
  () => props.auth?.accessToken,
  () => {
    bookings.value = []
    profile.value = null
    loadAccount()
    loadSchedule()
  },
)
onMounted(() => {
  loadSchedule()
  loadAccount()
  timer = window.setInterval(() => {
    clock.value = Date.now() + serverOffset
  }, 30000)
})
onUnmounted(() => {
  window.clearInterval(timer)
  controllers.forEach((c) => c.abort())
})
</script>

<template>
  <div class="customer-shell">
    <section class="customer-main">
      <header v-if="currentTab !== 'home'" class="customer-heading">
        <div>
          <span class="customer-eyebrow">YOUR COURT · YOUR GAME</span>
          <h1>{{ title }}</h1>
          <p>{{ descriptions[currentTab] }}</p>
        </div>
        <span class="customer-role">{{
          loggedIn ? 'KHÁCH HÀNG' : 'KHÁCH THAM QUAN'
        }}</span>
      </header>
      <div v-if="message" class="customer-alert success" role="status">{{ message }}</div>
      <div v-if="error && !dialog" class="customer-alert danger" role="alert">
        {{ error }}
      </div>
      <div v-if="accountError" class="customer-alert danger" role="alert">
        {{ accountError }}
        <button :disabled="loadingAccount" @click="loadAccount">Thử lại</button>
      </div>
      <template v-if="currentTab === 'home'">
        <section class="customer-hero">
          <img
            class="customer-hero-photo"
            src="/images/leechongw.jpg"
            alt="Vận động viên cầu lông thi đấu trên sân"
            fetchpriority="high"
          />
          <div class="customer-hero-content">
            <span class="customer-eyebrow"
              ><span class="sport-dot"></span> CARROT BADMINTON / PLAY YOUR WAY</span
            >
            <p class="sport-welcome">
              {{
                loggedIn ? `Chào ${fullName}. Lên sân thôi!` : 'Sân đã sẵn sàng. Còn bạn?'
              }}
            </p>
            <h1>VÀO SÂN.<br /><span>HẾT MÌNH.</span></h1>
            <p class="sport-description">
              Một cú đập. Một pha cứu cầu. Một buổi chơi đáng nhớ.<br />Chọn sân, lên lịch
              và mang năng lượng của bạn đến đây.
            </p>
            <div class="sport-hero-actions">
              <button class="customer-button primary" @click="navigate('booking')">
                ĐẶT SÂN NGAY <span>↗</span>
              </button>
              <button
                class="sport-text-button"
                @click="navigate(loggedIn ? 'my-bookings' : 'login')"
              >
                {{ loggedIn ? 'Lịch chơi của tôi' : 'Đăng nhập' }} <span>→</span>
              </button>
            </div>
          </div>
          <div class="sport-hero-caption">
            <span>NO LIMITS. JUST PLAY.</span><small>YOUR COURT. YOUR GAME.</small>
          </div>
          <span class="sport-hero-number" aria-hidden="true">01 /</span>
        </section>
        <div class="sport-ribbon" aria-hidden="true">
          <span>CHỌN GIỜ</span><b>↗</b><span>ĐẶT SÂN</span><b>↗</b><span>LÊN SÂN</span
          ><b>↗</b><span>CHƠI HẾT MÌNH</span>
        </div>
        <div class="customer-metrics">
          <article>
            <span>Lịch đang theo dõi</span
            ><strong>{{ loggedIn ? upcoming.length : '—' }}</strong
            ><small>Chờ nhận sân hoặc đã nhận sân</small>
          </article>
          <article>
            <span>Buổi chơi hoàn tất</span
            ><strong>{{ loggedIn ? finishedCount : '—' }}</strong
            ><small>Lịch sử của riêng bạn</small>
          </article>
          <article>
            <span>Sân đang hoạt động</span
            ><strong>{{
              loadingSchedule
                ? '…'
                : schedule
                  ? schedule.courts.filter((c) => c.active).length
                  : '—'
            }}</strong
            ><small>Kiểm tra giờ trống trước khi đặt</small>
          </article>
        </div>
        <section class="customer-card">
          <div class="customer-section-title">
            <div>
              <span class="customer-eyebrow">NEXT SESSION</span>
              <h2>Cuộc hẹn trên sân.</h2>
              <p>
                {{
                  loggedIn
                    ? 'Đừng bỏ lỡ lịch hẹn trên sân.'
                    : 'Đăng nhập để theo dõi lịch chơi của bạn.'
                }}
              </p>
            </div>
            <button
              class="customer-button secondary"
              @click="navigate(loggedIn ? 'my-bookings' : 'login')"
            >
              {{ loggedIn ? 'Xem lịch đặt' : 'Đăng nhập' }}
            </button>
          </div>
          <div v-if="loadingAccount" class="customer-empty">Đang tải lịch chơi…</div>
          <div v-else-if="upcoming.length" class="customer-next">
            <span class="customer-date-block">{{
              formatDate(upcoming[0].bookingDate)
            }}</span>
            <div>
              <strong>{{ upcoming[0].court?.name }}</strong>
              <p>
                {{ time(upcoming[0].startTime) }} – {{ time(upcoming[0].endTime) }} · #{{
                  upcoming[0].id
                }}
              </p>
            </div>
            <span class="customer-status" :class="upcoming[0].status">{{
              statusLabels[upcoming[0].status]
            }}</span>
          </div>
          <div v-else class="customer-empty">
            Chưa có lịch chơi. Chọn một khung giờ và bắt đầu nhé.
          </div>
        </section>
        <section class="customer-card">
          <div class="customer-section-title">
            <div>
              <span class="customer-eyebrow">FIND YOUR COURT</span>
              <h2>Chọn sân. Bắt nhịp.</h2>
              <p>Giá theo giờ, cập nhật từ bảng giá của sân.</p>
            </div>
            <button class="customer-button secondary" @click="navigate('booking')">
              Xem giờ trống
            </button>
          </div>
          <p v-if="scheduleError" class="customer-alert danger">{{ scheduleError }}</p>
          <div v-else-if="loadingSchedule" class="customer-empty">Đang tải bảng giá…</div>
          <div v-else class="customer-price-grid">
            <article
              v-for="c in (schedule?.courts || []).filter((c) => c.active && c.price)"
              :key="c.id"
            >
              <strong>{{ c.name }}</strong
              ><small>{{ c.roomName }} · {{ c.typeName }}</small>
              <p>{{ money(c.price.normalPricePerHour) }}<span> / giờ thường</span></p>
              <p>
                {{ money(c.price.peakPricePerHour)
                }}<span> / giờ cao điểm từ {{ time(c.price.peakStartTime) }}</span>
              </p>
              <small
                >Mở cửa {{ time(c.price.openingTime) }} –
                {{ time(c.price.closingTime) }}</small
              >
            </article>
          </div>
        </section>
      </template>
      <template v-else-if="currentTab === 'booking'">
        <section class="customer-card">
          <div class="customer-section-title">
            <div>
              <h2>01 / Lên lịch chơi.</h2>
              <p>Tối thiểu 60 phút, bắt đầu và kết thúc ở :00 hoặc :30.</p>
            </div>
            <button
              class="customer-button secondary"
              :disabled="loadingSchedule || saving"
              @click="loadSchedule"
            >
              {{ loadingSchedule ? 'Đang tải…' : 'Làm mới lịch' }}
            </button>
          </div>
          <div class="customer-form-grid">
            <label
              >Ngày chơi<input
                v-model="date"
                :disabled="saving"
                type="date"
                :min="vnDate()"
                required /></label
            ><label
              >Giờ bắt đầu<select v-model="startTime" :disabled="saving">
                <option
                  v-for="i in 48"
                  :key="i"
                  :value="`${String(Math.floor((i - 1) / 2)).padStart(2, '0')}:${(i - 1) % 2 ? '30' : '00'}`"
                >
                  {{
                    `${String(Math.floor((i - 1) / 2)).padStart(2, '0')}:${(i - 1) % 2 ? '30' : '00'}`
                  }}
                </option>
              </select></label
            ><label
              >Giờ kết thúc<select v-model="endTime" :disabled="saving">
                <option
                  v-for="i in 48"
                  :key="i"
                  :value="`${String(Math.floor((i - 1) / 2)).padStart(2, '0')}:${(i - 1) % 2 ? '30' : '00'}`"
                >
                  {{
                    `${String(Math.floor((i - 1) / 2)).padStart(2, '0')}:${(i - 1) % 2 ? '30' : '00'}`
                  }}
                </option>
              </select></label
            >
          </div>
          <p v-if="duration < 60 || duration % 30 !== 0" class="customer-alert danger">
            Giờ kết thúc phải sau giờ bắt đầu ít nhất 60 phút.
          </p>
        </section>
        <div class="customer-booking-layout">
          <section class="customer-card">
            <div class="customer-section-title">
              <div>
                <h2>02 / Chọn sân của bạn.</h2>
                <p>Bạn có thể chọn nhiều sân cho cùng một khung giờ.</p>
              </div>
              <span class="customer-tag">{{ formatDate(date) }}</span>
            </div>
            <p v-if="scheduleError" class="customer-alert danger">{{ scheduleError }}</p>
            <div v-else-if="loadingSchedule" class="customer-empty">
              Đang kiểm tra lịch sân…
            </div>
            <div v-else-if="!schedule?.courts.length" class="customer-empty">
              Chưa có sân để hiển thị.
            </div>
            <div v-else class="customer-court-grid">
              <button
                v-for="c in schedule.courts"
                :key="c.id"
                class="customer-court"
                :class="{
                  selected: selectedIds.includes(c.id),
                  unavailable: !!courtHint(c),
                }"
                :disabled="!!courtHint(c) || saving"
                :aria-pressed="selectedIds.includes(c.id)"
                @click="toggleCourt(c)"
              >
                <span class="customer-court-top"
                  ><strong>{{ c.name }}</strong
                  ><span>{{ selectedIds.includes(c.id) ? '✓' : '＋' }}</span></span
                ><small>{{ c.roomName }} · {{ c.typeName }}</small
                ><span class="customer-court-price">{{
                  c.price ? money(estimate(c)) : 'Chưa có giá'
                }}</span
                ><span class="customer-availability">{{
                  courtHint(c) || 'Còn trống trong giờ đã chọn'
                }}</span
                ><small v-if="c.price"
                  >Mở cửa {{ time(c.price.openingTime) }} –
                  {{ time(c.price.closingTime) }}</small
                >
              </button>
            </div>
          </section>
          <aside class="customer-card customer-summary">
            <span class="customer-eyebrow">BUỔI CHƠI CỦA BẠN</span>
            <h2>Thông tin đặt sân</h2>
            <dl>
              <div>
                <dt>Ngày</dt>
                <dd>{{ formatDate(date) }}</dd>
              </div>
              <div>
                <dt>Khung giờ</dt>
                <dd>{{ startTime }} – {{ endTime }}</dd>
              </div>
              <div>
                <dt>Thời lượng</dt>
                <dd>{{ duration > 0 ? duration : 0 }} phút</dd>
              </div>
              <div>
                <dt>Sân đã chọn</dt>
                <dd>
                  {{ selectedCourts.map((c) => c.name).join(', ') || 'Chưa chọn sân' }}
                </dd>
              </div>
            </dl>
            <div class="customer-total">
              <span>Tổng tiền dự kiến</span><strong>{{ money(estimatedTotal) }}</strong>
            </div>
            <p>
              Giá cuối cùng được xác nhận khi đặt thành công. Lịch trống có thể thay đổi
              khi người khác đặt sân.
            </p>
            <button
              v-if="loggedIn"
              class="customer-button primary"
              :disabled="
                !selectedCourts.length ||
                loadingSchedule ||
                saving ||
                selectedCourts.some((c) => courtHint(c))
              "
              @click="openDialog({ mode: 'book' })"
            >
              Tiếp tục đặt sân</button
            ><button v-else class="customer-button primary" @click="navigate('login')">
              Đăng nhập để đặt sân
            </button>
          </aside>
        </div>
      </template>
      <template v-else-if="['my-bookings', 'history'].includes(currentTab)">
        <section v-if="!loggedIn" class="customer-card customer-empty">
          <h2>Đăng nhập để xem lịch đặt</h2>
          <p>Lịch đặt và lịch sử chỉ hiển thị cho tài khoản của bạn.</p>
          <button class="customer-button primary" @click="navigate('login')">
            Đăng nhập
          </button>
        </section>
        <section v-else class="customer-card">
          <div class="customer-section-title">
            <div>
              <h2>
                {{
                  currentTab === 'history' ? 'Các lượt đã kết thúc' : 'Lịch chơi của bạn'
                }}
              </h2>
              <p>
                {{
                  currentTab === 'history'
                    ? 'Hoàn tất, đã hủy và vắng mặt.'
                    : 'Hủy booking khi còn hơn 30 phút trước giờ chơi.'
                }}
              </p>
            </div>
            <button
              class="customer-button secondary"
              :disabled="loadingAccount || saving"
              @click="loadAccount"
            >
              Làm mới
            </button>
          </div>
          <div class="customer-filters">
            <input
              v-model="search"
              aria-label="Tìm booking"
              placeholder="Tìm mã booking, sân hoặc ngày…"
            /><select v-model="statusFilter" aria-label="Lọc trạng thái">
              <option value="">Tất cả trạng thái</option>
              <option
                v-for="s in currentTab === 'history'
                  ? ['COMPLETED', 'CANCELLED', 'NO_SHOW']
                  : liveStatuses"
                :key="s"
                :value="s"
              >
                {{ statusLabels[s] }}
              </option></select
            ><span>{{ visibleBookings.length }} booking</span>
          </div>
          <div v-if="loadingAccount" class="customer-empty">Đang tải booking…</div>
          <div v-else-if="!visibleBookings.length" class="customer-empty">
            Không có booking phù hợp.<button
              v-if="currentTab !== 'history'"
              class="customer-button primary"
              @click="navigate('booking')"
            >
              Đặt sân mới
            </button>
          </div>
          <div v-else class="customer-table-wrap">
            <table class="customer-table">
              <thead>
                <tr>
                  <th>Booking / sân</th>
                  <th>Ngày / khung giờ</th>
                  <th>Thành tiền</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in visibleBookings" :key="b.id">
                  <td>
                    <strong>{{ b.court?.name || 'Sân' }}</strong
                    ><small>#{{ b.id }}</small>
                  </td>
                  <td>
                    <strong>{{ formatDate(b.bookingDate) }}</strong
                    ><small>{{ time(b.startTime) }} – {{ time(b.endTime) }}</small>
                  </td>
                  <td>
                    <strong>{{ money(b.totalAmount) }}</strong>
                  </td>
                  <td>
                    <span class="customer-status" :class="b.status">{{
                      statusLabels[b.status] || b.status
                    }}</span>
                  </td>
                  <td>
                    <div class="customer-row-actions">
                      <button
                        class="customer-button secondary"
                        @click="openDialog({ mode: 'detail', booking: b })"
                      >
                        Chi tiết</button
                      ><button
                        v-if="currentTab !== 'history'"
                        class="customer-button cancel"
                        :disabled="!canCancel(b) || saving"
                        :title="
                          canCancel(b)
                            ? 'Hủy booking'
                            : 'Chỉ hủy booking chưa nhận sân và còn hơn 30 phút trước giờ chơi'
                        "
                        @click="openDialog({ mode: 'cancel', booking: b })"
                      >
                        Hủy
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
      <template v-else-if="currentTab === 'profile'">
        <section v-if="!loggedIn" class="customer-card customer-empty">
          <h2>Hồ sơ của bạn</h2>
          <p>Đăng nhập để quản lý tài khoản.</p>
          <button class="customer-button primary" @click="navigate('login')">
            Đăng nhập
          </button>
        </section>
        <div v-else-if="loadingAccount" class="customer-card customer-empty">
          Đang tải hồ sơ…
        </div>
        <div v-else-if="profile" class="customer-profile-grid">
          <section class="customer-card">
            <div class="customer-profile-heading">
              <span class="customer-avatar">{{ initials }}</span>
              <div>
                <h2>{{ fullName }}</h2>
                <span class="customer-tag">{{
                  profile.status === 'ACTIVE'
                    ? 'Đang hoạt động'
                    : profile.status === 'WARNING'
                      ? 'Đang cảnh báo'
                      : profile.status === 'SUSPENDED'
                        ? 'Tạm khóa'
                        : profile.status
                }}</span>
              </div>
            </div>
            <form class="customer-stack" @submit.prevent="saveProfile">
              <label
                >Họ và tên<input
                  v-model="profileForm.fullName"
                  required
                  maxlength="255"
                  autocomplete="name"
                  :disabled="saving" /></label
              ><label
                >Số điện thoại<input
                  :value="profile.phone || 'Chưa cập nhật'"
                  readonly /></label
              ><label
                >Email<input :value="profile.email || 'Chưa cập nhật'" readonly
              /></label>
              <p>
                Số điện thoại và email dùng để định danh tài khoản. Liên hệ quầy nếu cần
                thay đổi.
              </p>
              <button
                class="customer-button primary"
                :disabled="saving || !profileForm.fullName.trim()"
              >
                {{ saving ? 'Đang xử lý…' : 'Lưu thông tin' }}
              </button>
            </form>
          </section>
          <section class="customer-card">
            <h2>Bảo mật tài khoản</h2>
            <p class="customer-muted">Đổi mật khẩu định kỳ để bảo vệ lịch đặt của bạn.</p>
            <form
              v-if="profile.authProvider !== 'GOOGLE'"
              class="customer-stack"
              @submit.prevent="changePassword"
            >
              <label
                >Mật khẩu hiện tại<input
                  v-model="password.currentPassword"
                  type="password"
                  required
                  autocomplete="current-password"
                  :disabled="saving" /></label
              ><label
                >Mật khẩu mới<input
                  v-model="password.newPassword"
                  type="password"
                  required
                  minlength="8"
                  maxlength="72"
                  autocomplete="new-password"
                  :disabled="saving" /></label
              ><label
                >Xác nhận mật khẩu mới<input
                  v-model="password.confirmPassword"
                  type="password"
                  required
                  minlength="8"
                  maxlength="72"
                  autocomplete="new-password"
                  :disabled="saving" /></label
              ><button class="customer-button primary" :disabled="saving">
                Đổi mật khẩu
              </button>
            </form>
            <p v-else>
              Bạn đăng nhập bằng Google. Quản lý mật khẩu tại tài khoản Google của bạn.
            </p>
          </section>
        </div>
      </template>
    </section>
    <Teleport to="body"
      ><div v-if="dialog" class="customer-modal-overlay" @click.self="closeDialog">
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
                dialog.mode === 'book'
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
          <div v-if="error" class="customer-alert danger" role="alert">{{ error }}</div>
          <template v-if="dialog.mode === 'book'"
            ><p>{{ selectedCourts.map((c) => c.name).join(', ') }}</p>
            <p>{{ formatDate(date) }} · {{ startTime }} – {{ endTime }}</p>
            <div class="customer-total">
              <span>Tổng dự kiến</span><strong>{{ money(estimatedTotal) }}</strong>
            </div>
            <p>
              Mỗi sân sẽ tạo một booking riêng. Bạn có thể hủy từng booking khi còn hơn 30
              phút trước giờ chơi.
            </p>
            <div class="customer-modal-actions">
              <button
                class="customer-button secondary"
                :disabled="saving"
                @click="closeDialog"
              >
                Quay lại</button
              ><button
                class="customer-button primary"
                :disabled="saving || !selectedCourts.length"
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
              <div>
                <dt>Thành tiền</dt>
                <dd>{{ money(dialog.booking.totalAmount) }}</dd>
              </div>
            </dl>
            <template v-if="dialog.mode === 'cancel'"
              ><p>
                Sau khi hủy, sân sẽ được mở cho người khác đặt. Bạn cần tạo booking mới
                nếu muốn chơi lại.
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
              <button class="customer-button secondary" @click="closeDialog">Đóng</button
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
    background: linear-gradient(0deg, #131c18 3%, #131c18df 35%, #131c1840 100%);
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
</style>

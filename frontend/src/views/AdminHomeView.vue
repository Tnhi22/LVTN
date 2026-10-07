<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AdminCourtsView from './AdminCourtsView.vue'
import AdminBookingsView from './AdminBookingsView.vue'
import AdminInventoryView from './AdminInventoryView.vue'
import AdminMaintenanceView from './AdminMaintenanceView.vue'
import AdminUsersView from './AdminUsersView.vue'
import { getAdminData } from '../services/adminService'
const props = defineProps({ auth: { type: Object, required: true } })
const emit = defineEmits(['session-expired'])
const dayString = (date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
const today = dayString(new Date())
const validDay = value => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0,10) === value
const period = ref('month')
const selectedDay = ref(today)
const selectedMonth = ref(today.slice(0,7))
const selectedYear = ref(Number(today.slice(0,4)))
const from = ref(today.slice(0, 8) + '01')
const to = ref(today)
const applied = ref({ from: from.value, to: to.value })
const section = ref('overview')
const loading = ref(false)
const errors = ref({})
const data = ref({})
const updated = ref('')
const search = ref('')
let controller
const tabs = [{ id: 'overview', label: 'Tổng quan', icon: '◫' }, { id: 'courts', label: 'Sân & bảng giá', icon: '▤' }, { id: 'bookings', label: 'Đặt sân', icon: '▣' }, { id: 'activity', label: 'Hoạt động', icon: '◷' }, { id: 'inventory', label: 'Kho cầu', icon: '▦' }, { id: 'maintenance', label: 'Bảo trì sân', icon: '⚒' }, { id: 'staff', label: 'Nhân viên', icon: '♙' }, {id:'users',label:'Người dùng',icon:'♧'}]
const money = (value) => value == null ? '—' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value)
const number = (value) => value == null ? '—' : new Intl.NumberFormat('vi-VN').format(value)
const dateLabel = (value) => value ? new Date(value.length === 10 ? `${value}T00:00:00` : value).toLocaleDateString('vi-VN') : '—'
const labels = { IN_STOCK: 'Đủ hàng', LOW_STOCK: 'Sắp hết', OUT_OF_STOCK: 'Hết hàng', IN_PROGRESS: 'Đang bảo trì', SCHEDULED: 'Đã lên lịch', OVERDUE: 'Quá hạn', DUE_SOON: 'Sắp đến hạn', ACTIVE: 'Hoạt động', INACTIVE: 'Ngừng hoạt động', LOCKED: 'Đã khóa' }
const stats = computed(() => [
  { label: 'Doanh thu đã thu', value: money(data.value.finance?.totalRevenue), note: 'Đặt sân + đánh vãng lai', icon: '₫', tone: 'green' },
  { label: 'Chi phí bảo trì', value: money(data.value.finance?.maintenanceExpense), note: 'Chi phí sửa chữa đã hoàn tất', icon: '⚒', tone: 'orange' },
  { label: 'Thu trừ sửa chữa', value: money(data.value.finance?.balance), note: 'Doanh thu trừ chi phí bảo trì', icon: '↗', tone: 'blue' },
  { label: 'Lịch đặt trong kỳ', value: number(data.value.activity?.totalNormalBookings), note: `${number(data.value.activity?.PENDING)} lịch đang chờ`, icon: '◷', tone: 'purple' },
])
function applyPeriod() {
  if (period.value === 'day') { from.value = selectedDay.value; to.value = selectedDay.value }
  else if (period.value === 'month') {
    from.value = selectedMonth.value + '-01'
    const [y,m] = selectedMonth.value.split('-').map(Number)
    to.value = selectedMonth.value + '-' + String(new Date(Date.UTC(y,m,0)).getUTCDate()).padStart(2,'0')
  } else {
    const y = Number(selectedYear.value)
    if (!Number.isInteger(y) || y < 1900 || y > 9998) { errors.value = { filter:'Chọn năm từ 1900 đến 9998.' }; return }
    from.value = String(y) + '-01-01'; to.value = String(y) + '-12-31'
  }
  load()
}
async function load() {
  if (!validDay(from.value) || !validDay(to.value) || from.value > to.value || (new Date(to.value) - new Date(from.value)) / 86400000 > 365) {
    errors.value = { filter: 'Chọn khoảng ngày hợp lệ, tối đa 366 ngày.' }; return
  }
  controller?.abort()
  const current = new AbortController()
  controller = current
  const timeout = window.setTimeout(() => current.abort(), 15000)
  loading.value = true
  errors.value = {}
  data.value = {}
  const range = new URLSearchParams({ from: from.value, to: to.value })
  applied.value = { from: from.value, to: to.value }
  const endpoints = { finance: `/api/admin/finance/summary?${range}`, staff: `/api/admin/staff-performance?${range}`, overview: `/api/admin/overview?${range}` }
  const results = await Promise.allSettled(Object.entries(endpoints).map(async ([key, path]) => [key, await getAdminData(path, props.auth.accessToken, current.signal)]))
  window.clearTimeout(timeout)
  if (controller !== current) return
  results.forEach((result, index) => {
    const key = Object.keys(endpoints)[index]
    if (result.status === 'fulfilled') data.value[key] = result.value[1]
    else {
      errors.value[key] = result.reason.name === 'AbortError' ? 'Máy chủ phản hồi quá lâu. Hãy thử tải lại.' : result.reason instanceof TypeError ? 'Không kết nối được máy chủ. Kiểm tra backend và CORS.' : result.reason.message
      if (result.reason.status === 401) emit('session-expired')
    }
  })
  data.value.activity = data.value.overview?.activity
  data.value.inventorySummary = data.value.overview?.inventorySummary
  data.value.maintenance = data.value.overview?.maintenanceSummary
  updated.value = new Date().toLocaleTimeString('vi-VN')
  loading.value = false
}
onMounted(applyPeriod)
onUnmounted(() => { controller?.abort(); controller = null })
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="workspace"><span class="workspace-icon">C</span><div><strong>Carrot Admin</strong><small>Không gian quản trị</small></div></div>
      <p class="nav-label">QUẢN LÝ SÂN CẦU</p>
      <nav aria-label="Quản trị"><button v-for="tab in tabs" :key="tab.id" :class="{ selected: section === tab.id }" :aria-current="section === tab.id ? 'page' : undefined" @click="section = tab.id"><span aria-hidden="true">{{ tab.icon }}</span>{{ tab.label }}<b v-if="section === tab.id">›</b></button></nav>
      <div class="sidebar-note"><span>PLAY · CONNECT · GROW</span><strong>Mỗi ngày, một nhịp sân mới.</strong><p>Theo dõi hoạt động và chăm sóc trải nghiệm người chơi.</p></div>
    </aside>
    <div class="admin-main" :aria-busy="loading">
      <header class="dashboard-heading"><div><p class="eyebrow">CARROT BADMINTON / QUẢN TRỊ</p><h1>{{ tabs.find(tab => tab.id === section)?.label }}</h1><p>Chào {{ auth.user.fullName || 'quản trị viên' }}, cùng theo dõi sân cầu của bạn.</p></div><button class="refresh" :disabled="loading" @click="load">↻ {{ loading ? 'Đang tải…' : 'Làm mới' }}</button></header>
      <form class="filter-panel" @submit.prevent="applyPeriod"><div class="period-buttons"><button v-for="item in [{id:'day',label:'Ngày'},{id:'month',label:'Tháng'},{id:'year',label:'Năm'}]" :key="item.id" type="button" :class="{primary:period===item.id}" @click="period=item.id">{{item.label}}</button></div><div class="date-fields"><label v-if="period==='day'">Chọn ngày<input v-model="selectedDay" type="date" required /></label><label v-else-if="period==='month'">Chọn tháng<input v-model="selectedMonth" type="month" required /></label><label v-else>Chọn năm<input v-model="selectedYear" type="number" min="1900" max="9998" step="1" required /></label><button class="primary" :disabled="loading">Xem thống kê</button></div></form>
      <div v-if="Object.keys(errors).length" class="error-box" role="alert"><strong>Một số dữ liệu chưa tải được</strong><p v-for="(error, key) in errors" :key="key">{{ error }}</p><button @click="load" :disabled="loading">Thử lại</button></div>
      <p class="scope-note">Kỳ thống kê: {{ dateLabel(applied.from) }} – {{ dateLabel(applied.to) }}. Doanh thu theo ngày thu tiền; chi phí sửa chữa theo ngày hoàn tất. Tồn kho, trạng thái sân và nhân viên là hiện tại.</p>
      <div v-if="section!=='overview'" class="module-period"><strong>Tóm tắt kỳ đã chọn</strong><span v-if="section==='bookings'">{{number(data.activity?.totalNormalBookings)}} lịch · {{money(data.finance?.normalBookingRevenue)}} đã thu</span><span v-else-if="section==='inventory'">{{number(data.overview?.inventoryPeriod?.receivedTubes)}} ống đã nhận · {{money(data.overview?.inventoryPeriod?.importValue)}} giá trị nhập</span><span v-else-if="section==='maintenance'">{{number(data.overview?.maintenancePeriod?.completed)}} lần hoàn tất · {{money(data.finance?.maintenanceExpense)}} sửa chữa</span><span v-else-if="section==='courts'">Hiện tại: {{number(data.overview?.activeCourts)}} sân hoạt động · {{number(data.overview?.inactiveCourts)}} sân ngừng hoạt động</span><span v-else>Hoạt động từ {{dateLabel(applied.from)}} đến {{dateLabel(applied.to)}}</span></div>
      <AdminCourtsView v-if="section === 'courts'" :auth="auth" :initial-range="applied" @session-expired="emit('session-expired')" />
      <AdminBookingsView v-if="section === 'bookings'" :auth="auth" :initial-range="applied" @session-expired="emit('session-expired')" />
      <template v-if="section === 'overview'">
        <div class="stats-grid"><article v-for="stat in stats" :key="stat.label" class="stat-card"><div class="stat-top"><span>{{ stat.label }}</span><span class="stat-icon" :class="stat.tone">{{ stat.icon }}</span></div><strong>{{ loading ? '…' : stat.value }}</strong><small>{{ stat.note }}</small></article></div>
        <div class="module-grid">
          <article class="panel"><div class="panel-title"><h2>Đặt sân</h2><span class="tag">TRONG KỲ</span></div><strong class="module-value">{{number(data.activity?.totalNormalBookings)}} lịch</strong><p>Chờ: {{number(data.activity?.PENDING)}} · Check-in: {{number(data.activity?.CHECKED_IN)}}</p><p>Hoàn tất: {{number(data.activity?.COMPLETED)}} · Hủy: {{number(data.activity?.CANCELLED)}} · Không đến: {{number(data.activity?.NO_SHOW)}}</p><button class="primary" @click="section='bookings'">Xem chi tiết →</button></article>
          <article class="panel"><div class="panel-title"><h2>Kho cầu</h2><span class="tag">TRONG KỲ</span></div><strong class="module-value">{{money(data.overview?.inventoryPeriod?.importValue)}}</strong><p>{{number(data.overview?.inventoryPeriod?.receivedBatches)}} lô · {{number(data.overview?.inventoryPeriod?.receivedTubes)}} ống đã nhận</p><p>Hiện tại: {{number(data.inventorySummary?.totalAvailableTubes)}} ống khả dụng</p><p>Xuất: {{number(data.overview?.inventoryPeriod?.issuedTubes)}} ống + {{number(data.overview?.inventoryPeriod?.issuedPieces)}} quả lẻ</p><button class="primary" @click="section='inventory'">Xem chi tiết →</button></article>
          <article class="panel"><div class="panel-title"><h2>Bảo trì & sửa chữa</h2><span class="tag">TRONG KỲ</span></div><strong class="module-value">{{money(data.finance?.maintenanceExpense)}}</strong><p>{{number(data.overview?.maintenancePeriod?.completed)}} lần hoàn tất · {{number(data.overview?.maintenancePeriod?.scheduled)}} lịch bắt đầu trong kỳ</p><p v-if="data.overview?.maintenancePeriod?.missingCosts" class="warning">{{number(data.overview.maintenancePeriod.missingCosts)}} lần hoàn tất chưa ghi chi phí; tổng chi chưa đầy đủ.</p><button class="primary" @click="section='maintenance'">Xem chi tiết →</button></article>
          <article class="panel"><h2>Sân & bảng giá</h2><strong class="module-value">{{number(data.overview?.activeCourts)}} sân hoạt động</strong><p>{{number(data.overview?.inactiveCourts)}} sân ngừng hoạt động · Trạng thái hiện tại</p><button class="primary" @click="section='courts'">Xem chi tiết →</button></article>
          <article class="panel"><h2>Hoạt động</h2><strong class="module-value">{{number(data.activity?.totalDailyVisitorSessions)}} buổi vãng lai</strong><p>{{number(data.activity?.dailyVisitorCheckedInSlots)}} suất check-in trong kỳ</p><button class="primary" @click="section='activity'">Xem chi tiết →</button></article>
          <article class="panel"><h2>Nhân viên</h2><strong class="module-value">{{number(data.overview?.activeStaff)}} đang hoạt động</strong><p>Đối chiếu người thu tiền và đơn hoàn tất trong kỳ.</p><button class="primary" @click="section='staff'">Xem chi tiết →</button></article>
        </div>
        <div class="overview-grid"><section class="panel"><div class="panel-title"><div><h2>Đối chiếu doanh thu & sửa chữa</h2><p>Ghi nhận trong kỳ đã chọn</p></div><span class="tag">VND</span></div><div class="revenue-breakdown"><p>Booking đã thu <strong>{{money(data.finance?.normalBookingRevenue)}}</strong></p><p>Đánh vãng lai đã thu <strong>{{money(data.finance?.dailyVisitorRevenue)}}</strong></p><p>Tổng thu <strong>{{money(data.finance?.totalRevenue)}}</strong></p><p>Sửa chữa đã hoàn tất <strong>{{money(data.finance?.maintenanceExpense)}}</strong></p><p>Thu trừ sửa chữa <strong>{{money(data.finance?.balance)}}</strong></p></div><p class="footnote">Chênh lệch này chưa trừ giá vốn cầu, lương và chi phí vận hành; không phải lợi nhuận ròng. Giá trị nhập kho được thống kê riêng.</p></section>
        <section class="panel"><div class="panel-title"><div><h2>Cần theo dõi</h2><p>Cập nhật từ hoạt động sân</p></div><span class="live-dot"></span></div><button class="alert-row" @click="section = 'activity'"><span class="alert-symbol orange">◷</span><div><strong>Lịch đặt đang chờ</strong><small>Kiểm tra lịch đặt trong kỳ</small></div><b>{{ number(data.activity?.PENDING) }}</b></button><button class="alert-row" @click="section = 'inventory'"><span class="alert-symbol purple">▦</span><div><strong>Sản phẩm sắp hết</strong><small>Sản phẩm đang bán, còn ít ống khả dụng</small></div><b>{{ number(data.inventorySummary?.lowStockProducts) }}</b></button><button class="alert-row" @click="section = 'maintenance'"><span class="alert-symbol orange">⚒</span><div><strong>Lịch bảo trì trễ bắt đầu</strong><small>Lịch đã qua giờ bắt đầu, còn chờ xử lý</small></div><b>{{ number(data.maintenance?.overdueScheduledCount) }}</b></button><div class="revenue-breakdown"><p>Đặt sân thường <strong>{{ money(data.finance?.normalBookingRevenue) }}</strong></p><p>Đánh vãng lai <strong>{{ money(data.finance?.dailyVisitorRevenue) }}</strong></p></div></section></div>
      </template>
      <section v-if="section === 'activity'" class="panel"><div class="panel-title"><div><h2>Hoạt động trong kỳ</h2><p>{{dateLabel(applied.from)}} – {{dateLabel(applied.to)}}</p></div></div><div class="activity-grid"><article v-for="item in [{ key: 'totalNormalBookings', label: 'Tổng lịch đặt' }, { key: 'PENDING', label: 'Đang chờ' }, { key: 'CHECKED_IN', label: 'Đã check-in' }, { key: 'CANCELLED', label: 'Đã hủy' }, { key: 'NO_SHOW', label: 'Không đến' }, { key: 'paidNormalBookings', label: 'Đơn thu tiền trong kỳ' }, { key: 'totalDailyVisitorSessions', label: 'Buổi đánh vãng lai' }, { key: 'COMPLETED', label: 'Lịch đã hoàn tất' }, { key: 'dailyVisitorCheckedInSlots', label: 'Suất vãng lai check-in' }]" :key="item.key"><small>{{ item.label }}</small><strong>{{ number(data.activity?.[item.key]) }}</strong></article></div></section>
      <AdminInventoryView v-if="section === 'inventory'" :auth="auth" :initial-range="applied" @session-expired="emit('session-expired')" />
      <AdminMaintenanceView v-if="section === 'maintenance'" :auth="auth" :initial-range="applied" @session-expired="emit('session-expired')" />
      <AdminUsersView v-if="section === 'staff'" role="STAFF" :auth="auth" @session-expired="emit('session-expired')" @changed="load" />
      <AdminUsersView v-if="section === 'users'" role="CUSTOMER" :auth="auth" @session-expired="emit('session-expired')" @changed="load" />
      <section v-if="section === 'staff'" class="panel"><div class="panel-title"><div><h2>Hoạt động nhân viên</h2><p>Kết quả trong kỳ · {{dateLabel(applied.from)}} – {{dateLabel(applied.to)}}</p></div></div><div class="table-wrap"><table><thead><tr><th>Nhân viên</th><th>Trạng thái</th><th>Đơn hoàn tất</th><th>Đăng ký vãng lai đã thu</th><th>Tiền mặt</th><th>Chuyển khoản</th><th>Thu vãng lai*</th><th>Tổng thu</th></tr></thead><tbody><tr v-for="item in data.staff || []" :key="item.staffId"><td><strong>{{ item.fullName }}</strong></td><td><span class="badge" :class="item.status">{{ labels[item.status] || item.status }}</span></td><td>{{ number(item.completedBookings) }}</td><td>{{ number(item.paidDailyVisitorRegistrations) }}</td><td>{{ money(item.cashCollected) }}</td><td>{{ money(item.bankTransferCollected) }}</td><td>{{money(item.dailyVisitorCollected)}}</td><td><strong>{{ money(item.totalCollected) }}</strong></td></tr><tr v-if="!data.staff?.length"><td colspan="8" class="empty">{{ data.staff ? 'Chưa có nhân viên.' : 'Chưa tải được dữ liệu nhân viên.' }}</td></tr></tbody></table></div></section>
      <p v-if="section==='staff'" class="footnote">* Thu vãng lai chưa có trường phương thức thanh toán, được hiển thị riêng. Tiền mặt/chuyển khoản là thu booking.</p>
      <p class="updated" role="status">{{ loading ? 'Đang tải dữ liệu…' : updated ? `Lần tải gần nhất: ${updated}` : '' }}</p>
    </div>
  </div>
</template>

<style scoped>
.module-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:18px;margin-bottom:22px}.module-value{display:block;font-size:23px;margin:14px 0;color:#075e38}.module-grid p{font-size:12px;color:#75867a;line-height:1.7}.module-grid button{margin-top:10px}.module-period{display:flex;gap:18px;flex-wrap:wrap;background:#e9f5ee;padding:14px;border-radius:10px;margin-bottom:20px;font-size:12px}.warning{color:#ad6924!important}@media(max-width:1100px){.module-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:600px){.module-grid{grid-template-columns:1fr}}

.admin-layout{display:grid;grid-template-columns:230px minmax(0,1fr);background:#f5f8f6;min-height:75vh;color:#1b3528}.admin-sidebar{background:white;border-right:1px solid #e1eae4;padding:30px 18px;display:flex;flex-direction:column}.workspace{display:flex;align-items:center;gap:10px;margin-bottom:36px}.workspace-icon{background:#005b35;color:white;width:40px;height:40px;border-radius:13px;display:grid;place-items:center;font-weight:800}.workspace small{display:block;color:#77867d;font-size:11px}.nav-label{font-size:10px;letter-spacing:1.4px;color:#85968b;padding-left:12px}.admin-sidebar nav{display:grid;gap:7px}.admin-sidebar nav button{border:0;background:transparent;color:#6b7c70;padding:13px 12px;display:flex;align-items:center;gap:12px;text-align:left;border-radius:10px;font-size:14px}.admin-sidebar nav button span{font-size:22px;width:25px}.admin-sidebar nav button b{margin-left:auto}.admin-sidebar nav button.selected{background:#e9f5ee;color:#005b35;font-weight:700}.sidebar-note{margin-top:auto;padding:55px 12px 0}.sidebar-note span{font-size:9px;letter-spacing:1.3px;color:#7c9283}.sidebar-note strong{display:block;font-size:17px;margin:12px 0}.sidebar-note p{font-size:12px;color:#7a8a80}.admin-main{padding:32px clamp(16px,3vw,44px);min-width:0}.dashboard-heading{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:24px}.eyebrow{font-size:10px;letter-spacing:1.8px;font-weight:700;color:#799181;margin:0 0 8px}h1{font-size:30px;letter-spacing:-1px;line-height:1.2;margin:0 0 10px}.dashboard-heading p:last-child{color:#75867a;font-size:13px;margin:0}.refresh,.period-buttons button,.primary,.error-box button{border:1px solid #dce7df;background:white;border-radius:8px;padding:9px 14px;color:#245239;font-size:12px;font-weight:600}.primary{background:#005b35;color:white;border-color:#005b35}.filter-panel{background:white;border:1px solid #e3ebe5;border-radius:12px;padding:14px 18px;display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.period-buttons{display:flex;gap:6px}.period-buttons button:hover{background:#e9f5ee}.date-fields{display:flex;align-items:end;gap:10px}.date-fields label{font-size:10px;color:#77877d;display:grid;gap:4px}.date-fields input,.search{border:1px solid #dce7df;border-radius:7px;padding:7px 9px;color:#294e38;background:white;font-size:12px;min-width:0}.scope-note,.footnote{font-size:11px;color:#819086}.scope-note{margin:13px 0 20px}.stats-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px;margin-bottom:22px}.stat-card{background:white;border:1px solid #e3ebe5;padding:20px;border-radius:13px}.stat-top{display:flex;align-items:center;justify-content:space-between;font-size:12px;color:#73867a;gap:8px}.stat-icon,.alert-symbol{display:grid;place-items:center;width:34px;height:34px;border-radius:10px;font-size:20px;flex-shrink:0}.green{background:#eaf6ee;color:#167345}.orange{background:#fff3e4;color:#bc7421}.blue{background:#edf3ff;color:#4b77bb}.purple{background:#f2edff;color:#8665b5}.stat-card>strong{font-size:clamp(19px,1.8vw,26px);display:block;margin:15px 0 8px;overflow-wrap:anywhere;letter-spacing:-.7px}.stat-card small{font-size:10px;color:#8a988f}.overview-grid{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(0,1fr);gap:20px}.panel{background:white;border:1px solid #e3ebe5;border-radius:14px;padding:23px;min-width:0}.panel-title{display:flex;justify-content:space-between;align-items:center;gap:14px;margin-bottom:22px;flex-wrap:wrap}h2{font-size:16px;margin:0 0 5px}.panel-title p{font-size:11px;margin:0;color:#8a988f}.tag{background:#edf6f0;padding:4px 10px;border-radius:6px;color:#508161;font-size:11px}.live-dot{width:8px;height:8px;border-radius:50%;background:#77b98c}.chart{display:flex;gap:9px;height:210px;overflow-x:auto;padding-top:10px;border-bottom:1px solid #e8efea}.chart-column{flex:1;min-width:15px;text-align:center;display:flex;flex-direction:column}.bar-space{height:170px;display:flex;align-items:end;justify-content:center;background:repeating-linear-gradient(to top,transparent 0,transparent 41px,#f0f4f1 42px,#f0f4f1 43px)}.bar{width:72%;max-width:30px;background:#39936b;border-radius:5px 5px 0 0;min-height:1px}.chart-column small{font-size:9px;color:#8b9c90;margin-top:10px}.alert-row{display:flex;align-items:center;gap:12px;border:0;border-bottom:1px solid #eef2ef;width:100%;padding:16px 0;background:white;text-align:left;color:#294e38}.alert-row div{flex:1}.alert-row strong{display:block;font-size:12px}.alert-row small{font-size:10px;color:#8a988f}.alert-row>b{font-size:21px}.revenue-breakdown{background:#f6faf7;padding:10px 14px;border-radius:10px;margin-top:18px}.revenue-breakdown p{display:flex;justify-content:space-between;gap:12px;font-size:11px;color:#7b8d80}.revenue-breakdown strong{color:#315c40}.updated{text-align:right;color:#8a9c8e;font-size:10px;margin:20px 0}.error-box{background:#fff4ec;border:1px solid #f1d7bf;border-radius:10px;padding:14px;margin-top:15px;font-size:12px;color:#914e22}.error-box p{margin:4px 0}.activity-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.activity-grid article{background:#f6faf7;border-radius:10px;padding:20px}.activity-grid small{display:block;color:#718977}.activity-grid strong{font-size:30px;color:#176540}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;text-align:left;font-size:12px;white-space:nowrap}th{background:#f6faf7;font-size:10px;color:#74887b;font-weight:600;padding:13px}td{padding:16px 13px;border-bottom:1px solid #edf2ee}.cell-sub{display:block;color:#82958a;font-size:10px}.badge{padding:5px 9px;border-radius:6px;background:#edf1ef;color:#667d70;font-size:10px}.IN_STOCK,.ACTIVE{background:#e9f5ed;color:#267645}.LOW_STOCK,.DUE_SOON,.SCHEDULED{background:#fff3e2;color:#a87220}.OUT_OF_STOCK,.OVERDUE,.LOCKED{background:#fff0ee;color:#b95748}.IN_PROGRESS{background:#eaf1ff;color:#4a72ac}.empty{text-align:center;color:#88998e;padding:48px 12px;font-size:13px;white-space:normal}.search{max-width:100%;width:220px}details{margin-top:16px;font-size:12px;color:#52725d}summary{cursor:pointer}button:disabled{opacity:.6;cursor:wait}@media(min-width:1600px){.admin-main{max-width:1450px;width:100%;margin:auto}}@media(max-width:1100px){.admin-layout{grid-template-columns:190px minmax(0,1fr)}.stats-grid{grid-template-columns:repeat(2,1fr)}.overview-grid{grid-template-columns:1fr}.admin-main{padding:24px 20px}}@media(max-width:760px){.admin-layout{display:block}.admin-sidebar{padding:14px;border-right:0;border-bottom:1px solid #e1eae4}.workspace,.nav-label,.sidebar-note{display:none}.admin-sidebar nav{display:flex;overflow-x:auto}.admin-sidebar nav button{white-space:nowrap;padding:8px 12px;font-size:12px}.admin-sidebar nav button span{font-size:18px;width:auto}.admin-sidebar nav button b{display:none}.dashboard-heading{align-items:start}.dashboard-heading h1{font-size:25px}.eyebrow{font-size:9px}.dashboard-heading p:last-child{font-size:12px}.admin-main{padding:22px 14px}.filter-panel{padding:12px}.date-fields{width:100%;flex-wrap:wrap}.date-fields label{flex:1}.date-fields input{width:100%}.stats-grid{gap:10px}.stat-card{padding:14px}.stat-top{font-size:11px}.stat-icon{width:27px;height:27px;font-size:16px}.stat-card>strong{font-size:20px}.panel{padding:17px}.activity-grid{grid-template-columns:repeat(2,1fr)}.scope-note{line-height:1.8}}
</style>

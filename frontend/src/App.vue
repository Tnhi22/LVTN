<script setup>
import { nextTick, onMounted, onUnmounted, ref, computed } from 'vue'
import LandingView from './views/LandingView.vue'
import LoginView from './views/LoginView.vue'
import AdminHomeView from './views/AdminHomeView.vue'
const AUTH_STORAGE_KEY = 'carrot.auth'
const publicNavigation = [
  { id: 'home', label: 'Trang chủ' },
  { id: 'courts', label: 'Sân cầu' },
  { id: 'booking', label: 'Đặt sân' },
  { id: 'contact', label: 'Liên hệ' },
]
const activePage = ref('home')
const menuOpen = ref(false)
const notificationsOpen = ref(false)
const accountOpen = ref(false)
const chatOpen = ref(false)
const notice = ref('')
const auth = ref(null)
const isAdmin = computed(() => auth.value?.user?.role === 'ADMIN')
const navigation = computed(() => isAdmin.value
  ? [{ id: 'admin', label: 'Quản trị' }, { id: 'customer-preview', label: 'Xem trang khách hàng' }]
  : publicNavigation)

const notificationArea = ref(null)
const notificationButton = ref(null)
const accountArea = ref(null)
const accountButton = ref(null)
const chatButton = ref(null)
const chatInput = ref(null)
const chatBody = ref(null)
const message = ref('')
const messages = ref([
  {
    id: 1,
    role: 'assistant',
    text:
      'Chào bạn! Mình là trợ lý Carrot. Bạn có thể hỏi về ngày, giờ, sân, giá, thời gian chơi, trình độ và cầu lông.',
  },
])
const suggestions = [
  'Tìm sân tối mai từ 18h đến 20h',
  'Có buổi chơi ghép cho người mới không?',
]
const roleLabels = {
  CUSTOMER: 'Khách hàng',
  STAFF: 'Nhân viên',
  ADMIN: 'Quản trị viên',
}
let expirationTimer = null
function getTokenExpiration(token) {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload
      .replace(/-/g, '+')
      .replace(/_/g, '/')
    const paddedBase64 = base64.padEnd(
      Math.ceil(base64.length / 4) * 4,
      '=',
    )
    const decoded = JSON.parse(window.atob(paddedBase64))
    return typeof decoded.exp === 'number'
      ? decoded.exp * 1000
      : null
  } catch {
    return null
  }
}
function clearExpirationTimer() {
  if (expirationTimer !== null) {
    window.clearTimeout(expirationTimer)
    expirationTimer = null
  }
}
function removeStoredAuth() {
  try {
    window.sessionStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    // Vẫn có thể đăng xuất nếu trình duyệt chặn bộ nhớ phiên.
  }
}
function clearAuth() {
  clearExpirationTimer()
  auth.value = null
  accountOpen.value = false
  removeStoredAuth()
}
function checkSession() {
  if (!auth.value) return
  const expiresAt = getTokenExpiration(auth.value.accessToken)
  if (expiresAt !== null && expiresAt <= Date.now()) {
    clearAuth()
    notice.value = 'Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại nhé.'
  }
}
function scheduleExpiration() {
  clearExpirationTimer()
  if (!auth.value) return
  const expiresAt = getTokenExpiration(auth.value.accessToken)
  if (expiresAt === null) return
  const remaining = expiresAt - Date.now()
  if (remaining <= 0) {
    checkSession()
    return
  }
  expirationTimer = window.setTimeout(() => {
    checkSession()
    if (auth.value) {
      scheduleExpiration()
    }
  }, Math.min(remaining, 2147483647))
}
function restoreAuth() {
  try {
    const stored = window.sessionStorage.getItem(AUTH_STORAGE_KEY)
    if (!stored) return
    const data = JSON.parse(stored)
    const valid =
      typeof data.accessToken === 'string' &&
      data.accessToken.length > 0 &&
      data.user?.id != null &&
      ['CUSTOMER', 'STAFF', 'ADMIN'].includes(data.user?.role)
    if (!valid) {
      removeStoredAuth()
      return
    }
    const expiresAt = getTokenExpiration(data.accessToken)
    if (expiresAt !== null && expiresAt <= Date.now()) {
      removeStoredAuth()
      return
    }
    auth.value = data
    scheduleExpiration()
  } catch {
    removeStoredAuth()
  }
}
function closeHeaderPanels() {
  menuOpen.value = false
  notificationsOpen.value = false
  accountOpen.value = false
}
function syncPage() {
  const page = window.location.hash.slice(1)
  // Cho liên kết hỗ trợ bàn phím giữ nguyên trang đang xem.
  if (page === 'page-content') return
  checkSession()
  const validPage =
    page === 'login' ||
    page === 'home' ||
    (page === 'customer-preview' && isAdmin.value) ||
    (page === 'admin' && isAdmin.value) ||
    navigation.value.some((item) => item.id === page)
  activePage.value = validPage ? page : 'home'
  if (page === 'admin' && !isAdmin.value) {
    activePage.value = auth.value ? 'home' : 'login'
  } else if (isAdmin.value && activePage.value === 'home') {
    activePage.value = 'admin'
  }
  closeHeaderPanels()
  notice.value = ''
  if (!['home', 'login', 'admin', 'customer-preview'].includes(activePage.value)) {
    notice.value = 'Trang này sẽ được bổ sung ở bước tiếp theo.'
  }
  window.scrollTo({
    top: 0,
    behavior: 'auto',
  })
}
function navigateTo(page) {
  closeHeaderPanels()
  if (window.location.hash === `#${page}`) {
    syncPage()
  } else {
    window.location.hash = page
  }
}
function selectNavigation(item) {
  closeHeaderPanels()
  if (window.location.hash === `#${item.id}`) {
    syncPage()
  }
}
async function handleSessionExpired() {
  clearAuth()
  navigateTo('login')
  await nextTick()
  notice.value = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
}

function showLogin() {
  navigateTo('login')
}
async function handleLoginSuccess(result) {
  auth.value = result
  let stored = true
  try {
    window.sessionStorage.setItem(
      AUTH_STORAGE_KEY,
      JSON.stringify(result),
    )
  } catch {
    stored = false
  }
  scheduleExpiration()
  navigateTo(result.user.role === 'ADMIN' ? 'admin' : 'home')
  // Đợi điều hướng xong để thông báo không bị syncPage xóa.
  await nextTick()
  const name = result.user.fullName || result.user.phone || 'bạn'
  notice.value = stored
    ? `Đăng nhập thành công. Chào ${name}!`
    : `Chào ${name}! Đã đăng nhập, nhưng trình duyệt không lưu được phiên khi tải lại trang.`
}
async function logout() {
  clearAuth()
  closeHeaderPanels()
  navigateTo('home')
  await nextTick()
  notice.value = 'Bạn đã đăng xuất.'
}
function toggleMenu() {
  notificationsOpen.value = false
  accountOpen.value = false
  menuOpen.value = !menuOpen.value
}
function toggleNotifications() {
  menuOpen.value = false
  accountOpen.value = false
  notificationsOpen.value = !notificationsOpen.value
}
function toggleAccount() {
  checkSession()
  if (!auth.value) {
    showLogin()
    return
  }
  menuOpen.value = false
  notificationsOpen.value = false
  accountOpen.value = !accountOpen.value
}
async function toggleChat() {
  closeHeaderPanels()
  chatOpen.value = !chatOpen.value
  if (chatOpen.value) {
    await nextTick()
    chatInput.value?.focus()
  }
}
async function sendMessage(text = message.value) {
  const content = text.trim()
  if (!content || content.length > 1000) return
  messages.value.push({
    id: messages.value.length + 1,
    role: 'user',
    text: content,
  })
  message.value = ''
  messages.value.push({
    id: messages.value.length + 1,
    role: 'assistant',
    text:
      'Chat AI đang được hoàn thiện nên chưa thể tra cứu hoặc trả lời câu hỏi này.',
  })
  await nextTick()
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight
  }
  chatInput.value?.focus()
}
function handleOutsideClick(event) {
  if (
    notificationsOpen.value &&
    !notificationArea.value?.contains(event.target)
  ) {
    notificationsOpen.value = false
  }
  if (
    accountOpen.value &&
    !accountArea.value?.contains(event.target)
  ) {
    accountOpen.value = false
  }
}
function handleEscape(event) {
  if (event.key !== 'Escape') return
  if (chatOpen.value) {
    chatOpen.value = false
    chatButton.value?.focus()
    return
  }
  if (accountOpen.value) {
    accountOpen.value = false
    accountButton.value?.focus()
    return
  }
  if (notificationsOpen.value) {
    notificationsOpen.value = false
    notificationButton.value?.focus()
    return
  }
  menuOpen.value = false
}
onMounted(() => {
  restoreAuth()
  syncPage()
  window.addEventListener('hashchange', syncPage)
  window.addEventListener('focus', checkSession)
  document.addEventListener('click', handleOutsideClick)
  document.addEventListener('keydown', handleEscape)
})
onUnmounted(() => {
  clearExpirationTimer()
  window.removeEventListener('hashchange', syncPage)
  window.removeEventListener('focus', checkSession)
  document.removeEventListener('click', handleOutsideClick)
  document.removeEventListener('keydown', handleEscape)
})
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#page-content">
      Chuyển đến nội dung
    </a>
    <header class="site-header">
      <div class="container header-inner">
        <a
          href="#home"
          class="brand"
          aria-label="Carrot Badminton - Trang chủ"
          @click="closeHeaderPanels"
        >
          <img
            src="/images/logo.png"
            alt="Carrot Badminton"
          />
        </a>
        <span v-if="isAdmin" class="admin-label">QUẢN TRỊ</span>
        <nav
          id="main-navigation"
          class="main-navigation"
          :class="{ open: menuOpen }"
          aria-label="Điều hướng chính"
        >
          <a
            v-for="item in navigation"
            :key="item.id"
            :href="`#${item.id}`"
            :class="{ active: activePage === item.id }"
            :aria-current="
              activePage === item.id ? 'page' : undefined
            "
            @click="selectNavigation(item)"
          >
            {{ item.label }}
          </a>
        </nav>
        <div class="header-actions">
          <div
            ref="notificationArea"
            class="notification-area"
          >
            <button
              ref="notificationButton"
              class="icon-button notification-button"
              type="button"
              aria-label="Thông báo"
              :aria-expanded="notificationsOpen"
              aria-controls="notification-panel"
              @click="toggleNotifications"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="1.8"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path
                  d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"
                />
                <path d="M10 21h4" />
              </svg>
            </button>
            <section
              v-if="notificationsOpen"
              id="notification-panel"
              class="notification-panel"
              aria-labelledby="notification-title"
            >
              <div class="panel-heading">
                <h2 id="notification-title">Thông báo</h2>
                <button
                  class="panel-close"
                  type="button"
                  aria-label="Đóng thông báo"
                  @click="notificationsOpen = false"
                >
                  ×
                </button>
              </div>
              <div class="notification-empty">
                <span aria-hidden="true">○</span>
                <strong>Thông báo của bạn</strong>
                <template v-if="!auth">
                  <p>
                    Đăng nhập để theo dõi các cập nhật về lịch chơi.
                  </p>
                  <button
                    class="small-button"
                    type="button"
                    @click="showLogin"
                  >
                    Đăng nhập
                  </button>
                </template>
                <p v-else>
                  Tính năng nhận thông báo đang được kết nối.
                </p>
              </div>
            </section>
          </div>
          <button
            v-if="!auth"
            class="login-button"
            type="button"
            @click="showLogin"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <circle cx="12" cy="8" r="4" />
              <path d="M5 21v-2a7 7 0 0 1 14 0v2" />
            </svg>
            <span>Đăng nhập</span>
          </button>
          <div
            v-else
            ref="accountArea"
            class="account-area"
          >
            <button
              ref="accountButton"
              class="login-button account-button"
              type="button"
              :aria-expanded="accountOpen"
              aria-controls="account-panel"
              :aria-label="`Tài khoản ${
                auth.user.fullName || auth.user.phone || ''
              }`"
              @click="toggleAccount"
            >
              <span class="account-avatar" aria-hidden="true">
                {{
                  (auth.user.fullName || auth.user.phone || 'U')
                    .trim()
                    .charAt(0)
                    .toUpperCase()
                }}
              </span>
              <span class="account-name">
                {{ auth.user.fullName || auth.user.phone }}
              </span>
              <span class="account-arrow" aria-hidden="true">
                ▾
              </span>
            </button>
            <section
              v-if="accountOpen"
              id="account-panel"
              class="account-panel"
              aria-label="Thông tin tài khoản"
            >
              <div class="account-details">
                <strong>
                  {{ auth.user.fullName || 'Tài khoản của bạn' }}
                </strong>
                <span v-if="auth.user.phone">
                  {{ auth.user.phone }}
                </span>
                <span v-else-if="auth.user.email">
                  {{ auth.user.email }}
                </span>
                <small class="account-role">
                  {{ roleLabels[auth.user.role] }}
                </small>
              </div>
              <button
                class="logout-button"
                type="button"
                @click="logout"
              >
                Đăng xuất
              </button>
            </section>
          </div>
          <button
            class="icon-button menu-toggle"
            type="button"
            :aria-label="menuOpen ? 'Đóng menu' : 'Mở menu'"
            :aria-expanded="menuOpen"
            aria-controls="main-navigation"
            @click="toggleMenu"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <path
                v-if="menuOpen"
                d="M6 6l12 12M18 6L6 18"
              />
              <path
                v-else
                d="M4 6h16M4 12h16M4 18h16"
              />
            </svg>
          </button>
        </div>
      </div>
    </header>
    <main
      id="page-content"
      class="page-content"
      tabindex="-1"
    >
      <div
        v-if="notice"
        class="container notice"
        role="status"
      >
        <span>{{ notice }}</span>
        <button
          type="button"
          aria-label="Đóng thông báo"
          @click="notice = ''"
        >
          ×
        </button>
      </div>
      <AdminHomeView
        v-if="activePage === 'admin' && isAdmin"
        :auth="auth"
        @session-expired="handleSessionExpired"
      />
      <LandingView v-else-if="['home', 'customer-preview'].includes(activePage)" />
      <LoginView
        v-else-if="activePage === 'login' && !auth"
        @login-success="handleLoginSuccess"
      />
      <section
        v-else-if="activePage === 'login' && auth"
        class="container signed-in-section"
      >
        <h1>Bạn đã đăng nhập</h1>
        <p>
          Chào {{ auth.user.fullName || auth.user.phone }}!
        </p>
        <button
          class="small-button"
          type="button"
          @click="navigateTo('home')"
        >
          Về trang chủ
        </button>
      </section>
      <!--
        Các trang sân cầu, đặt sân và liên hệ
        sẽ được thêm bằng component riêng ở bước sau.
      -->
    </main>
    <footer class="site-footer">
      <div class="container footer-grid">
        <div class="footer-brand">
          <a
            class="brand"
            href="#home"
            aria-label="Carrot Badminton - Trang chủ"
            @click="closeHeaderPanels"
          >
            <img
              src="/images/logo.png"
              alt="Carrot Badminton"
            />
          </a>
          <p>
            Một điểm hẹn.<br />
            Cùng nhau giữ nhịp đam mê.
          </p>
        </div>
        <div class="footer-navigation">
          <h2>Khám phá</h2>
          <nav aria-label="Điều hướng cuối trang">
            <a
              v-for="item in navigation"
              :key="item.id"
              :href="`#${item.id}`"
              @click="selectNavigation(item)"
            >
              {{ item.label }}
            </a>
          </nav>
        </div>
        <div class="footer-support">
          <h2>Đồng hành cùng bạn</h2>
          <p>
            Tìm hiểu sân, chọn lịch chơi và kết nối cộng đồng.
          </p>
          <button
            class="footer-chat-link"
            type="button"
            @click="toggleChat"
          >
            Trò chuyện với Carrot AI →
          </button>
        </div>
      </div>
      <div class="container footer-bottom">
        <p>
          © {{ new Date().getFullYear() }} Carrot Badminton.
        </p>
        <span>PLAY · CONNECT · GROW</span>
      </div>
    </footer>
    <section
      v-if="chatOpen"
      id="chat-panel"
      class="chat-panel"
      aria-labelledby="chat-title"
    >
      <header class="chat-header">
        <div class="chat-identity">
          <span class="ai-avatar" aria-hidden="true">
            AI
          </span>
          <div>
            <h2 id="chat-title">Carrot AI</h2>
            <p>Trợ lý tra cứu cầu lông</p>
          </div>
        </div>
        <button
          class="chat-close"
          type="button"
          aria-label="Đóng chat"
          @click="toggleChat"
        >
          ×
        </button>
      </header>
      <div
        ref="chatBody"
        class="chat-body"
        role="log"
        aria-live="polite"
        aria-relevant="additions"
      >
        <p class="chat-status">
          Bản giao diện · Chưa kết nối AI
        </p>
        <div
          v-for="item in messages"
          :key="item.id"
          class="chat-message"
          :class="item.role"
        >
          <span class="sr-only">
            {{ item.role === 'user' ? 'Bạn:' : 'Carrot AI:' }}
          </span>
          {{ item.text }}
        </div>
        <div
          v-if="messages.length === 1"
          class="chat-suggestions"
        >
          <button
            v-for="suggestion in suggestions"
            :key="suggestion"
            type="button"
            @click="sendMessage(suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
      </div>
      <form
        class="chat-form"
        @submit.prevent="sendMessage()"
      >
        <input
          ref="chatInput"
          v-model="message"
          type="text"
          maxlength="1000"
          aria-label="Tin nhắn cho Carrot AI"
          placeholder="Bạn muốn tìm sân lúc nào?"
        />
        <button
          type="submit"
          aria-label="Gửi tin nhắn"
          :disabled="!message.trim()"
        >
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="m22 2-7 20-4-9-9-4 20-7Z" />
            <path d="M22 2 11 13" />
          </svg>
        </button>
      </form>
    </section>
    <button
      ref="chatButton"
      class="chat-launcher"
      type="button"
      :aria-expanded="chatOpen"
      aria-controls="chat-panel"
      :aria-label="chatOpen ? 'Đóng Carrot AI' : 'Mở Carrot AI'"
      @click="toggleChat"
    >
      <span class="launcher-icon" aria-hidden="true">
        {{ chatOpen ? '×' : 'AI' }}
      </span>
      <span>Carrot AI</span>
    </button>
  </div>
</template>
<style scoped>
.admin-label { color: #fff; font-size: 10px; letter-spacing: 1px; border: 1px solid #ffffff50; padding: 4px 8px; border-radius: 6px; }
@media(max-width: 480px) { .admin-label { display: none; } }
.account-area {
  position: relative;
  min-width: 0;
}
.account-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 190px;
}
.account-avatar {
  display: inline-flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #ffffff;
  color: #14532d;
  font-size: 13px;
  font-weight: 800;
}
.account-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.account-arrow {
  flex-shrink: 0;
  font-size: 12px;
}
.account-panel {
  position: absolute;
  top: calc(100% + 14px);
  right: 0;
  z-index: 100;
  width: 260px;
  max-width: calc(100vw - 32px);
  padding: 18px;
  border: 1px solid #e2e8e5;
  border-radius: 16px;
  background: #ffffff;
  color: #17251c;
  box-shadow: 0 16px 45px rgb(15 35 23 / 16%);
}
.account-details {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  padding-bottom: 16px;
  overflow-wrap: anywhere;
}
.account-details strong {
  font-size: 16px;
  line-height: 1.5;
}
.account-details > span {
  color: #647067;
  font-size: 13px;
}
.account-role {
  margin-top: 3px;
  padding: 5px 10px;
  border-radius: 999px;
  background: #edf7f0;
  color: #166534;
  font-size: 12px;
  font-weight: 700;
}
.logout-button {
  width: 100%;
  padding: 11px 14px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fff5f5;
  color: #b91c1c;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 160ms ease,
    border-color 160ms ease;
}
.logout-button:hover {
  border-color: #fca5a5;
  background: #fee2e2;
}
.logout-button:focus-visible {
  outline: 3px solid #fca5a5;
  outline-offset: 3px;
}
.signed-in-section {
  padding-top: 64px;
  padding-bottom: 80px;
  color: #17251c;
}
.signed-in-section h1 {
  margin-bottom: 12px;
}
.signed-in-section p {
  margin-bottom: 24px;
  color: #647067;
}
@media (max-width: 767px) {
  .account-button {
    max-width: 145px;
    gap: 6px;
  }
  .account-avatar {
    flex-basis: 26px;
    width: 26px;
    height: 26px;
  }
}
@media (max-width: 480px) {
  .account-button {
    max-width: none;
    padding: 7px 9px;
  }
  .account-name {
    display: none;
  }
  .account-panel {
    width: 240px;
  }
}
</style>
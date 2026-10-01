<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import LandingView from './views/LandingView.vue'



const navigation = [
  { id: 'home', label: 'Trang chủ' },
  { id: 'courts', label: 'Sân cầu' },
  { id: 'booking', label: 'Đặt sân' },
  { id: 'contact', label: 'Liên hệ' },
]

const activePage = ref('home')
const menuOpen = ref(false)
const notificationsOpen = ref(false)
const chatOpen = ref(false)
const notice = ref('')
const message = ref('')
const chatInput = ref(null)
const chatBody = ref(null)
const notificationArea = ref(null)
const notificationButton = ref(null)
const chatButton = ref(null)

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    text: 'Chào bạn! Mình là trợ lý Carrot. Bạn có thể hỏi về ngày, giờ, sân, giá, thời gian chơi, trình độ và cầu lông.',
  },
])

const suggestions = [
  'Tìm sân tối mai từ 18h đến 20h',
  'Có buổi chơi ghép cho người mới không?',
]

function closeHeaderPanels() {
  menuOpen.value = false
  notificationsOpen.value = false
}

function syncPage() {
  const page = window.location.hash.slice(1)
  activePage.value = navigation.some((item) => item.id === page)
    ? page
    : 'home'

  closeHeaderPanels()

  notice.value =
    activePage.value === 'home'
      ? ''
      : 'Trang này sẽ được bổ sung ở bước tiếp theo.'

  window.scrollTo({ top: 0, behavior: 'auto' })
}

function selectNavigation(item) {
  closeHeaderPanels()

  if (window.location.hash === `#${item.id}`) {
    syncPage()
  }
}

function showLogin() {
  closeHeaderPanels()
  notice.value = 'Chức năng đăng nhập sẽ được kết nối ở bước tiếp theo.'
}

function toggleMenu() {
  notificationsOpen.value = false
  menuOpen.value = !menuOpen.value
}

function toggleNotifications() {
  menuOpen.value = false
  notificationsOpen.value = !notificationsOpen.value
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

  // Phản hồi giao diện tạm, không giả lập kết quả AI hoặc dữ liệu sân.
  messages.value.push({
    id: messages.value.length + 1,
    role: 'assistant',
    text: 'Chat AI đang được hoàn thiện nên chưa thể tra cứu hoặc trả lời câu hỏi này.',
  })

  await nextTick()
  chatBody.value?.scrollTo({
    top: chatBody.value.scrollHeight,
    behavior: 'auto',
  })
  chatInput.value?.focus()
}

function handleOutsideClick(event) {
  if (
    notificationsOpen.value &&
    !notificationArea.value?.contains(event.target)
  ) {
    notificationsOpen.value = false
  }
}

function handleEscape(event) {
  if (event.key !== 'Escape') return

  if (chatOpen.value) {
    chatOpen.value = false
    nextTick(() => chatButton.value?.focus())
  } else if (notificationsOpen.value) {
    notificationsOpen.value = false
    nextTick(() => notificationButton.value?.focus())
  } else {
    menuOpen.value = false
  }
}

onMounted(() => {
  syncPage()
  window.addEventListener('hashchange', syncPage)
  document.addEventListener('click', handleOutsideClick)
  document.addEventListener('keydown', handleEscape)
})

onUnmounted(() => {
  window.removeEventListener('hashchange', syncPage)
  document.removeEventListener('click', handleOutsideClick)
  document.removeEventListener('keydown', handleEscape)
})
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#page-content">Đến nội dung chính</a>

    <!-- HEADER CHUNG -->
    <header class="site-header">
      <div class="container header-inner">
        <a
          class="brand"
          href="#home"
          aria-label="Carrot Badminton — Trang chủ"
          @click="closeHeaderPanels"
        >
          <img :src="'/images/logo.png'" alt="Carrot Badminton" />
        </a>

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
            :aria-current="activePage === item.id ? 'page' : undefined"
            @click="selectNavigation(item)"
          >
            {{ item.label }}
          </a>
        </nav>

        <div class="header-actions">
          <div ref="notificationArea" class="notification-area">
            <button
              ref="notificationButton"
              class="icon-button notification-button"
              type="button"
              aria-label="Thông báo"
              aria-controls="notification-panel"
              :aria-expanded="notificationsOpen"
              @click="toggleNotifications"
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
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
                >×</button>
              </div>

              <div class="notification-empty">
                <span class="empty-icon" aria-hidden="true">○</span>
                <strong>Thông báo của bạn</strong>
                <p>Đăng nhập để theo dõi các cập nhật về lịch chơi.</p>
                <button class="small-button" type="button" @click="showLogin">
                  Đăng nhập
                </button>
              </div>
            </section>
          </div>

          <button class="login-button" type="button" @click="showLogin">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="8" r="4" />
              <path d="M4 21v-2a8 8 0 0 1 16 0v2" />
            </svg>
            <span>Đăng nhập</span>
          </button>

          <button
            class="icon-button menu-toggle"
            type="button"
            aria-controls="main-navigation"
            :aria-expanded="menuOpen"
            :aria-label="menuOpen ? 'Đóng menu' : 'Mở menu'"
            @click="toggleMenu"
          >
            <svg v-if="!menuOpen" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M4 6h16M4 12h16M4 18h16" />
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="m6 6 12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
      </div>
    </header>

    <!-- KHU VỰC HIỂN THỊ CÁC TRANG SAU NÀY -->
    <main id="page-content" class="page-content" tabindex="-1">
      <div v-if="notice" class="container notice" role="status">
        <span>{{ notice }}</span>
        <button
          type="button"
          aria-label="Đóng thông báo"
          @click="notice = ''"
        >×</button>
      </div>
      <LandingView v-if="activePage === 'home'" />

      <!-- Sau này đặt RouterView hoặc component trang tại đây. -->
    </main>

    <!-- FOOTER CHUNG -->
    <footer class="site-footer">
      <div class="container footer-grid">
        <div class="footer-brand">
          <a class="brand" href="#home" aria-label="Carrot Badminton — Trang chủ">
            <img :src="'/images/logo.png'" alt="Carrot Badminton" />
          </a>
          <p>Một điểm hẹn.<br />Cùng nhau giữ nhịp đam mê.</p>
        </div>

        <div class="footer-navigation">
          <h2>Khám phá</h2>
          <nav aria-label="Liên kết cuối trang">
            <a
              v-for="item in navigation"
              :key="item.id"
              :href="`#${item.id}`"
              @click="selectNavigation(item)"
            >{{ item.label }}</a>
          </nav>
        </div>

        <div class="footer-support">
          <h2>Đồng hành cùng bạn</h2>
          <p>Tìm hiểu sân, chọn lịch chơi và kết nối cộng đồng cầu lông.</p>
          <button class="footer-chat-link" type="button" @click="toggleChat">
            Mở trợ lý Carrot AI ↗
          </button>
        </div>
      </div>

      <div class="container footer-bottom">
        <span>© {{ new Date().getFullYear() }} Carrot Badminton.</span>
        <span class="footer-motto">PLAY · CONNECT · GROW</span>
      </div>
    </footer>

    <!-- BOX CHAT CHUNG -->
    <section
      v-if="chatOpen"
      id="chat-panel"
      class="chat-panel"
      aria-labelledby="chat-title"
    >
      <header class="chat-header">
        <div class="chat-identity">
          <span class="ai-avatar" aria-hidden="true">AI</span>
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
        >×</button>
      </header>

      <div ref="chatBody" class="chat-body" role="log" aria-live="polite">
        <p class="chat-status">Bản giao diện · Chưa kết nối AI</p>

        <div
          v-for="item in messages"
          :key="item.id"
          class="chat-message"
          :class="item.role"
        >
          <span class="sr-only">
            {{ item.role === 'user' ? 'Bạn: ' : 'Trợ lý: ' }}
          </span>
          {{ item.text }}
        </div>

        <div v-if="messages.length === 1" class="chat-suggestions">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion"
            type="button"
            @click="sendMessage(suggestion)"
          >{{ suggestion }}</button>
        </div>
      </div>

      <form class="chat-form" @submit.prevent="sendMessage()">
        <label class="sr-only" for="chat-input">Câu hỏi của bạn</label>
        <input
          id="chat-input"
          ref="chatInput"
          v-model="message"
          type="text"
          maxlength="1000"
          placeholder="Hỏi về sân hoặc cầu lông..."
          autocomplete="off"
        />
        <button
          type="submit"
          aria-label="Gửi câu hỏi"
          :disabled="!message.trim()"
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m5 12 14-7-5 14-3-6-6-1Z" />
            <path d="m11 13 8-8" />
          </svg>
        </button>
      </form>
    </section>

    <button
      ref="chatButton"
      class="chat-launcher"
      type="button"
      aria-controls="chat-panel"
      :aria-expanded="chatOpen"
      :aria-label="chatOpen ? 'Đóng Carrot AI' : 'Mở Carrot AI'"
      @click="toggleChat"
    >
      <span class="launcher-icon" aria-hidden="true">{{ chatOpen ? '×' : 'AI' }}</span>
      <span>Carrot AI</span>
    </button>
  </div>
</template>
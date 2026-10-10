<script setup>
import { nextTick, onMounted, onUnmounted, ref, computed, watch } from 'vue';
import LandingView from './views/LandingView.vue';
import LoginView from './views/LoginView.vue';
import AdminHomeView from './views/AdminHomeView.vue';
import StaffHomeView from './views/StaffHomeView.vue';
import CustomerHomeView from './views/CustomerHomeView.vue';
import ProductView from './views/ProductView.vue';
import PricingView from './views/PricingView.vue';
import NewsView from './views/NewsView.vue';
import ContactView from './views/ContactView.vue';
import SearchView from './views/SearchView.vue';
import BookingView from './views/BookingView.vue';
import { publicRequest, todayVN } from './services/customerPortalUtils.js';
import {
  getCustomerData,
  customerImageUrl,
} from './services/customerService.js';
const AUTH_STORAGE_KEY = 'carrot.auth';
const publicNavigation = [
  { id: 'home', label: 'Trang chủ' },
  { id: 'products', label: 'Sản phẩm' },
  { id: 'booking', label: 'Lịch trống / Đặt sân' },
  { id: 'pricing', label: 'Bảng giá' },
  { id: 'news', label: 'Tin tức' },
  { id: 'contact', label: 'Liên hệ' },
];
const routeParams = ref({});
const globalQuery = ref('');
const globalScope = ref('all');
const globalDate = ref(todayVN());
const notificationItems = ref([]);
const notificationLoading = ref(false);
const notificationError = ref('');
function submitGlobalSearch() {
  const params = new URLSearchParams({
    q: globalQuery.value.trim(),
    scope: globalScope.value,
    date: globalDate.value,
  });
  navigateTo(`search?${params}`);
}
const readNoticeIds = ref([]);
const notificationBusy = ref(false);
const selectedOffer = ref(null);
const noticeClock = ref(Date.now());
let noticeServerOffset = 0;
let notificationTimer;
let notificationController;
let notificationSequence = 0;
const unreadCount = computed(
  () =>
    notificationItems.value.filter((n) => !readNoticeIds.value.includes(n.id))
      .length,
);
function readStorageKey() {
  return `carrot.notifications.read.${auth.value?.user?.id}`;
}
function restoreReadNotices() {
  try {
    const ids = JSON.parse(localStorage.getItem(readStorageKey()) || '[]');
    readNoticeIds.value = Array.isArray(ids)
      ? ids.filter((x) => typeof x === 'string')
      : [];
  } catch {
    readNoticeIds.value = [];
  }
}
function markRead(ids) {
  readNoticeIds.value = [...new Set([...readNoticeIds.value, ...ids])].slice(
    -1000,
  );
  try {
    localStorage.setItem(readStorageKey(), JSON.stringify(readNoticeIds.value));
  } catch {
    /* Vẫn hoạt động trong phiên hiện tại. */
  }
}
function offerAvailable(n) {
  return (
    n.actionable &&
    n.offerExpiresAt &&
    new Date(`${n.offerExpiresAt}+07:00`).getTime() > noticeClock.value
  );
}
function noticeTimestamp(value) {
  if (!value) return null;
  const raw = String(value);
  const timestamp = Date.parse(
    /(?:Z|[+-]\d{2}:?\d{2})$/i.test(raw) ? raw : raw + '+07:00',
  );
  return Number.isFinite(timestamp) ? timestamp : null;
}
const sortedNotifications = computed(() =>
  notificationItems.value
    .map((item, index) => ({ item, index }))
    .sort(
      (a, b) =>
        (noticeTimestamp(b.item.occurredAt) ?? -Infinity) -
          (noticeTimestamp(a.item.occurredAt) ?? -Infinity) ||
        a.index - b.index,
    )
    .map(({ item }) => item),
);
function noticeTime(n) {
  const timestamp = noticeTimestamp(n.occurredAt);
  return timestamp === null
    ? ''
    : new Date(timestamp).toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
        hour: '2-digit',
        minute: '2-digit',
        day: '2-digit',
        month: '2-digit',
      });
}
function noticeIcon(n) {
  if (offerAvailable(n)) return '↗';
  if (/hủy/i.test(n.title)) return '×';
  if (/chờ|waiting/i.test(n.title)) return '◷';
  return '✓';
}
function openNotification(n) {
  markRead([n.id]);
  if (offerAvailable(n)) {
    selectedOffer.value = n;
    notificationError.value = '';
  } else {
    notificationsOpen.value = false;
    navigateTo(n.target || 'history');
  }
}
async function confirmNotificationOffer() {
  const n = selectedOffer.value;
  if (!n || notificationBusy.value) return;
  if (!offerAvailable(n)) {
    notificationError.value =
      'Lời mời đã hết hạn. Vui lòng kiểm tra danh sách chờ.';
    return;
  }
  notificationBusy.value = true;
  notificationError.value = '';
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 15000);
  const token = auth.value?.accessToken;
  try {
    await getCustomerData(
      `/api/daily-visitor-waitlists/session/${n.sessionId}/confirm`,
      token,
      controller.signal,
      { method: 'POST' },
    );
    if (auth.value?.accessToken !== token) return;
    selectedOffer.value = null;
    notificationsOpen.value = false;
    navigateTo('my-bookings');
    await nextTick();
    notice.value = 'Đã xác nhận slot Daily. Hẹn bạn trên sân!';
    await loadNotifications();
  } catch (e) {
    if (e.status === 401) handleSessionExpired();
    else
      notificationError.value =
        e.name === 'AbortError'
          ? 'Xác nhận quá lâu. Kiểm tra lịch đặt trước khi thử lại.'
          : e.message;
  } finally {
    window.clearTimeout(timeout);
    notificationBusy.value = false;
  }
}
async function loadNotifications() {
  if (!isCustomer.value) return;
  const sequence = ++notificationSequence;
  notificationController?.abort();
  const controller = new AbortController();
  notificationController = controller;
  const token = auth.value.accessToken;
  notificationLoading.value = true;
  const timeout = window.setTimeout(() => controller.abort(), 15000);
  try {
    const data = await getCustomerData(
      '/api/customer/notifications',
      token,
      controller.signal,
    );
    if (sequence !== notificationSequence || auth.value?.accessToken !== token)
      return;
    const serverTime = new Date(`${data.serverTime}+07:00`).getTime();
    if (Number.isFinite(serverTime))
      noticeServerOffset = serverTime - Date.now();
    noticeClock.value = Date.now() + noticeServerOffset;
    notificationItems.value = data.items || [];
    if (notificationsOpen.value)
      markRead(notificationItems.value.map((n) => n.id));
    if (!selectedOffer.value) notificationError.value = '';
  } catch (e) {
    if (sequence !== notificationSequence || auth.value?.accessToken !== token)
      return;
    if (e.status === 401) handleSessionExpired();
    else if (e.name !== 'AbortError') notificationError.value = e.message;
  } finally {
    window.clearTimeout(timeout);
    if (sequence === notificationSequence) notificationLoading.value = false;
  }
}
function refreshCustomerNotifications() {
  if (isCustomer.value) loadNotifications();
}
const activePage = ref('home');
const menuOpen = ref(false);
const notificationsOpen = ref(false);
const accountOpen = ref(false);
const chatOpen = ref(false);
const notice = ref('');
const auth = ref(null);
const isCustomer = computed(() => auth.value?.user?.role === 'CUSTOMER');
const isStaff = computed(() => auth.value?.user?.role === 'STAFF');
const isAdmin = computed(() => auth.value?.user?.role === 'ADMIN');
const navigation = computed(() => publicNavigation);
const notificationArea = ref(null);
const notificationButton = ref(null);
const accountArea = ref(null);
const accountButton = ref(null);
const chatButton = ref(null);
const chatInput = ref(null);
const chatBody = ref(null);
const message = ref('');
const messages = ref([
  {
    id: 1,
    role: 'assistant',
    text: 'Chào bạn! Mình là trợ lý Carrot. Bạn có thể hỏi về ngày, giờ, sân, giá, thời gian chơi, trình độ và cầu lông.',
  },
]);
const suggestions = [
  'Tìm sân tối mai từ 18h đến 20h',
  'Có buổi chơi ghép cho người mới không?',
];
const roleLabels = {
  CUSTOMER: 'Khách hàng',
  STAFF: 'Nhân viên',
  ADMIN: 'Quản trị viên',
};
let expirationTimer = null;
function getTokenExpiration(token) {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, '+').replace(/\_/g, '/');
    const paddedBase64 = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    const decoded = JSON.parse(window.atob(paddedBase64));
    return typeof decoded.exp === 'number' ? decoded.exp * 1000 : null;
  } catch {
    return null;
  }
}
function clearExpirationTimer() {
  if (expirationTimer !== null) {
    window.clearTimeout(expirationTimer);
    expirationTimer = null;
  }
}
function removeStoredAuth() {
  try {
    window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
  } catch {
    // Vẫn có thể đăng xuất nếu trình duyệt chặn bộ nhớ phiên.
  }
}
function clearAuth() {
  clearExpirationTimer();
  auth.value = null;
  accountOpen.value = false;
  removeStoredAuth();
}
function checkSession() {
  if (!auth.value) return;
  const expiresAt = getTokenExpiration(auth.value.accessToken);
  if (expiresAt !== null && expiresAt <= Date.now()) {
    clearAuth();
    notice.value = 'Phiên đăng nhập đã hết hạn. Bạn đăng nhập lại nhé.';
  }
}
function scheduleExpiration() {
  clearExpirationTimer();
  if (!auth.value) return;
  const expiresAt = getTokenExpiration(auth.value.accessToken);
  if (expiresAt === null) return;
  const remaining = expiresAt - Date.now();
  if (remaining <= 0) {
    checkSession();
    return;
  }
  expirationTimer = window.setTimeout(
    () => {
      checkSession();
      if (auth.value) {
        scheduleExpiration();
      }
    },
    Math.min(remaining, 2147483647),
  );
}
function restoreAuth() {
  try {
    const stored = window.sessionStorage.getItem(AUTH_STORAGE_KEY);
    if (!stored) return;
    const data = JSON.parse(stored);
    const valid =
      typeof data.accessToken === 'string' &&
      data.accessToken.length > 0 &&
      data.user?.id != null &&
      ['CUSTOMER', 'STAFF', 'ADMIN'].includes(data.user?.role);
    if (!valid) {
      removeStoredAuth();
      return;
    }
    const expiresAt = getTokenExpiration(data.accessToken);
    if (expiresAt !== null && expiresAt <= Date.now()) {
      removeStoredAuth();
      return;
    }
    auth.value = data;
    scheduleExpiration();
  } catch {
    removeStoredAuth();
  }
}
function closeHeaderPanels() {
  menuOpen.value = false;
  notificationsOpen.value = false;
  accountOpen.value = false;
}
function syncPage() {
  const raw = window.location.hash.slice(1);
  const [page, queryString] = raw.split('?');
  routeParams.value = Object.fromEntries(
    new URLSearchParams(queryString || ''),
  );
  // Cho liên kết hỗ trợ bàn phím giữ nguyên trang đang xem.
  if (page === 'page-content') return;
  checkSession();
  const validPage =
    ['login', 'register', 'search'].includes(page) ||
    page === 'home' ||
    [
      'courts',
      'booking',
      'my-bookings',
      'history',
      'profile',
      'contact',
    ].includes(page) ||
    (page === 'customer-preview' && isAdmin.value) ||
    (page === 'admin' && isAdmin.value) ||
    (page === 'staff' && isStaff.value) ||
    navigation.value.some((item) => item.id === page);
  activePage.value = validPage ? page : 'home';
  if (page === 'admin' && !isAdmin.value) {
    activePage.value = auth.value ? 'home' : 'login';
  } else if (page === 'staff' && !isStaff.value) {
    activePage.value = auth.value ? 'home' : 'login';
  }
  closeHeaderPanels();
  notice.value = '';
  window.scrollTo({
    top: 0,
    behavior: 'auto',
  });
}
function navigateTo(page) {
  closeHeaderPanels();
  if (window.location.hash === `#${page}`) {
    syncPage();
  } else {
    window.location.hash = page;
  }
}
function selectNavigation(item) {
  closeHeaderPanels();
  if (window.location.hash === `#${item.id}`) {
    syncPage();
  }
}
async function handleSessionExpired() {
  clearAuth();
  navigateTo('login');
  await nextTick();
  notice.value = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
}
function handleProfileUpdated(profile) {
  if (!isCustomer.value) return;
  auth.value = {
    ...auth.value,
    user: {
      ...auth.value.user,
      fullName: profile.fullName,
      avatarUrl: profile.avatarUrl,
    },
  };
  try {
    window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth.value));
  } catch {
    /* Hồ sơ vẫn được lưu ở máy chủ. */
  }
}
function showLogin() {
  navigateTo('login');
}
async function handleLoginSuccess(result) {
  auth.value = result;
  let stored = true;
  try {
    window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(result));
  } catch {
    stored = false;
  }
  scheduleExpiration();
  navigateTo(
    result.user.role === 'ADMIN'
      ? 'admin'
      : result.user.role === 'STAFF'
        ? 'staff'
        : 'home',
  );
  // Đợi điều hướng xong để thông báo không bị syncPage xóa.
  await nextTick();
  if (needsPhoneVerification.value && result.user.phone)
    openPhoneVerification();
  const name = result.user.fullName || result.user.phone || 'bạn';
  notice.value = stored
    ? `Đăng nhập thành công. Chào ${name}!`
    : `Chào ${name}! Đã đăng nhập, nhưng trình duyệt không lưu được phiên khi tải lại trang.`;
}
async function logout() {
  clearAuth();
  closeHeaderPanels();
  navigateTo('home');
  await nextTick();
  notice.value = 'Bạn đã đăng xuất.';
}
function toggleMenu() {
  notificationsOpen.value = false;
  accountOpen.value = false;
  menuOpen.value = !menuOpen.value;
}
function toggleNotifications() {
  menuOpen.value = false;
  accountOpen.value = false;
  notificationsOpen.value = !notificationsOpen.value;
  if (notificationsOpen.value) {
    markRead(notificationItems.value.map((n) => n.id));
    loadNotifications();
  }
}
function toggleAccount() {
  checkSession();
  if (!auth.value) {
    showLogin();
    return;
  }
  menuOpen.value = false;
  notificationsOpen.value = false;
  accountOpen.value = !accountOpen.value;
}
async function toggleChat() {
  closeHeaderPanels();
  chatOpen.value = !chatOpen.value;
  if (chatOpen.value) {
    await nextTick();
    chatInput.value?.focus();
  }
}
async function sendMessage(text = message.value) {
  const content = text.trim();
  if (!content || content.length > 1000) return;
  messages.value.push({
    id: messages.value.length + 1,
    role: 'user',
    text: content,
  });
  message.value = '';
  messages.value.push({
    id: messages.value.length + 1,
    role: 'assistant',
    text: 'Chat AI đang được hoàn thiện nên chưa thể tra cứu hoặc trả lời câu hỏi này.',
  });
  await nextTick();
  if (chatBody.value) {
    chatBody.value.scrollTop = chatBody.value.scrollHeight;
  }
  chatInput.value?.focus();
}
function handleOutsideClick(event) {
  if (
    notificationsOpen.value &&
    !notificationArea.value?.contains(event.target)
  ) {
    notificationsOpen.value = false;
  }
  if (accountOpen.value && !accountArea.value?.contains(event.target)) {
    accountOpen.value = false;
  }
}
function handleEscape(event) {
  if (event.key !== 'Escape') return;
  if (chatOpen.value) {
    chatOpen.value = false;
    chatButton.value?.focus();
    return;
  }
  if (accountOpen.value) {
    accountOpen.value = false;
    accountButton.value?.focus();
    return;
  }
  if (notificationsOpen.value) {
    notificationsOpen.value = false;
    notificationButton.value?.focus();
    return;
  }
  menuOpen.value = false;
}
watch(
  () => auth.value?.accessToken,
  () => {
    notificationController?.abort();
    ++notificationSequence;
    notificationItems.value = [];
    selectedOffer.value = null;
    notificationError.value = '';
    notificationLoading.value = false;
    restoreReadNotices();
    refreshCustomerNotifications();
  },
);
onMounted(() => {
  restoreAuth();
  verificationTimer = window.setInterval(() => {
    verificationClock.value = Date.now();
  }, 1000);
  notificationTimer = window.setInterval(() => {
    noticeClock.value = Date.now() + noticeServerOffset;
    if (!document.hidden) refreshCustomerNotifications();
  }, 15000);
  window.addEventListener(
    'carrot:customer-updated',
    refreshCustomerNotifications,
  );
  window.addEventListener('focus', refreshCustomerNotifications);
  syncPage();
  window.addEventListener('hashchange', syncPage);
  window.addEventListener('focus', checkSession);
  document.addEventListener('click', handleOutsideClick);
  document.addEventListener('keydown', handleEscape);
});
onUnmounted(() => {
  window.clearInterval(verificationTimer);
  window.clearInterval(notificationTimer);
  notificationController?.abort();
  window.removeEventListener(
    'carrot:customer-updated',
    refreshCustomerNotifications,
  );
  window.removeEventListener('focus', refreshCustomerNotifications);
  clearExpirationTimer();
  window.removeEventListener('hashchange', syncPage);
  window.removeEventListener('focus', checkSession);
  document.removeEventListener('click', handleOutsideClick);
  document.removeEventListener('keydown', handleEscape);
});

const phoneVerificationOpen = ref(false);
const verificationPhone = ref('');
const verificationOtp = ref('');
const verificationSentPhone = ref('');
const verificationBusy = ref(false);
const verificationError = ref('');
const verificationMessage = ref('');
const verificationCooldownUntil = ref(0);
const verificationClock = ref(Date.now());
let verificationTimer;
const needsPhoneVerification = computed(
  () =>
    auth.value?.user?.role === 'CUSTOMER' &&
    (!auth.value.user.phone || auth.value.user.phoneVerified !== true),
);
const verificationCooldown = computed(() =>
  Math.max(
    0,
    Math.ceil(
      (verificationCooldownUntil.value - verificationClock.value) / 1000,
    ),
  ),
);
function normalizedVerificationPhone(value) {
  let phone = String(value || '')
    .trim()
    .replace(/[\s.-]/g, '');
  if (phone.startsWith('+84')) phone = `0${phone.slice(3)}`;
  return phone;
}
function openPhoneVerification() {
  if (!needsPhoneVerification.value) return;
  verificationPhone.value = auth.value.user.phone || '';
  verificationOtp.value = '';
  verificationSentPhone.value = '';
  verificationError.value = '';
  verificationMessage.value = '';
  phoneVerificationOpen.value = true;
}
async function verificationRequest(path, body, token) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), 15000);
  try {
    return await getCustomerData(path, token, controller.signal, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  } catch (error) {
    if (error.name === 'AbortError')
      throw new Error('Máy chủ phản hồi quá lâu. Vui lòng thử lại.');
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}
async function sendVerificationOtp() {
  if (verificationBusy.value || verificationCooldown.value > 0) return;
  const phone = normalizedVerificationPhone(verificationPhone.value);
  if (!/^0[35789]\d{8}$/.test(phone)) {
    verificationError.value = 'Vui lòng nhập số điện thoại Việt Nam hợp lệ.';
    return;
  }
  const token = auth.value?.accessToken;
  if (!token) return;
  verificationBusy.value = true;
  verificationError.value = '';
  verificationMessage.value = '';
  try {
    await verificationRequest(
      '/api/auth/phone/request-verification',
      { phone },
      token,
    );
    if (auth.value?.accessToken !== token) return;
    verificationSentPhone.value = phone;
    verificationOtp.value = '';
    verificationClock.value = Date.now();
    verificationCooldownUntil.value = Date.now() + 60000;
    verificationMessage.value =
      'Đã tạo OTP. Với bản demo, xem mã trong terminal backend. Mã có hiệu lực 5 phút.';
  } catch (error) {
    if (auth.value?.accessToken === token)
      verificationError.value = error.message || 'Không thể gửi OTP.';
  } finally {
    verificationBusy.value = false;
  }
}
async function confirmVerificationOtp() {
  if (verificationBusy.value) return;
  const phone = normalizedVerificationPhone(verificationPhone.value);
  if (!verificationSentPhone.value || phone !== verificationSentPhone.value) {
    verificationError.value =
      'Vui lòng gửi OTP cho số điện thoại đang nhập trước.';
    return;
  }
  const otp = verificationOtp.value.trim();
  if (!/^\d{6}$/.test(otp)) {
    verificationError.value = 'OTP phải gồm đúng 6 chữ số.';
    return;
  }
  const token = auth.value?.accessToken;
  if (!token) return;
  verificationBusy.value = true;
  verificationError.value = '';
  try {
    await verificationRequest('/api/auth/phone/verify', { otp }, token);
    if (auth.value?.accessToken !== token) return;
    auth.value = {
      ...auth.value,
      user: {
        ...auth.value.user,
        phone,
        phoneVerified: true,
      },
    };
    try {
      window.sessionStorage.setItem(
        AUTH_STORAGE_KEY,
        JSON.stringify(auth.value),
      );
    } catch {
      /* Backend đã lưu; phiên hiện tại vẫn được cập nhật. */
    }
    phoneVerificationOpen.value = false;
    verificationOtp.value = '';
    notice.value =
      'Xác minh số điện thoại thành công. Bạn có thể đặt sân và đăng ký Daily Visitor.';
  } catch (error) {
    if (auth.value?.accessToken === token)
      verificationError.value = error.message || 'Không thể xác minh OTP.';
  } finally {
    verificationBusy.value = false;
  }
}
watch(
  () => auth.value?.accessToken,
  () => {
    phoneVerificationOpen.value = false;
    verificationOtp.value = '';
    verificationSentPhone.value = '';
    verificationCooldownUntil.value = 0;
  },
);
watch(
  () => [activePage.value, needsPhoneVerification.value],
  ([page, needed]) => {
    if (needed && ['booking', 'courts'].includes(page)) openPhoneVerification();
  },
);
</script>
<template>
  <div class="app-shell">
    <a class="skip-link" href="#page-content"> Chuyển đến nội dung </a>
    <header class="site-header">
      <div class="container header-inner">
        <a
          href="#home"
          class="brand"
          aria-label="Carrot Badminton - Trang chủ"
          @click="closeHeaderPanels"
        >
          <img src="/images/logo.png" alt="Carrot Badminton" />
          <span class="header-brand-copy"
            ><strong>CARROT</strong><small>BADMINTON CLUB</small></span
          >
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
              :class="{ active: notificationsOpen }"
              type="button"
              :aria-label="`Thông báo, ${unreadCount} chưa đọc`"
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
                <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
                <path d="M10 21h4" />
              </svg>
              <span v-if="unreadCount" class="notification-count">{{
                unreadCount > 99 ? '99+' : unreadCount
              }}</span>
            </button>
            <section
              v-if="notificationsOpen"
              id="notification-panel"
              class="notification-panel"
              aria-labelledby="notification-title"
            >
              <div class="panel-heading">
                <div>
                  <span class="notification-eyebrow"
                    >CARROT · LIVE UPDATES</span
                  >
                  <h2 id="notification-title">
                    Nhịp sân của bạn<span
                      class="notification-live"
                      aria-hidden="true"
                    ></span>
                  </h2>
                  <p class="notification-subtitle">
                    Mọi cập nhật. Ngay trong tầm tay.
                  </p>
                </div>
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
                <template v-if="!auth">
                  <p>Đăng nhập để theo dõi các cập nhật về lịch chơi.</p>
                  <button class="small-button" type="button" @click="showLogin">
                    Đăng nhập
                  </button>
                </template>
                <template v-else-if="isCustomer">
                  <div class="notification-tools">
                    <span
                      >{{ notificationItems.length }} cập nhật · Mới nhất
                      trước</span
                    >
                    <button
                      type="button"
                      @click="markRead(notificationItems.map((n) => n.id))"
                    >
                      Đọc tất cả
                    </button>
                  </div>
                  <p
                    v-if="notificationError"
                    class="notification-error"
                    role="alert"
                  >
                    {{ notificationError }}
                  </p>
                  <div v-if="selectedOffer" class="notification-offer">
                    <strong>🏸 Xác nhận vào chơi</strong>
                    <p>{{ selectedOffer.message }}</p>
                    <p>
                      Hạn xác nhận:
                      {{ selectedOffer.offerExpiresAt?.slice(11, 16) }}
                    </p>
                    <button
                      type="button"
                      :disabled="
                        notificationBusy || !offerAvailable(selectedOffer)
                      "
                      @click="confirmNotificationOffer"
                    >
                      {{
                        notificationBusy
                          ? 'Đang xác nhận…'
                          : offerAvailable(selectedOffer)
                            ? 'Xác nhận nhận slot'
                            : 'Lời mời đã hết hạn'
                      }}
                    </button>
                    <button
                      type="button"
                      :disabled="notificationBusy"
                      @click="selectedOffer = null"
                    >
                      Quay lại
                    </button>
                  </div>
                  <div
                    v-else-if="notificationItems.length"
                    class="booking-notices"
                  >
                    <button
                      v-for="n in sortedNotifications"
                      :key="n.id"
                      type="button"
                      :class="{
                        unread: !readNoticeIds.includes(n.id),
                        invitation: offerAvailable(n),
                      }"
                      @click="openNotification(n)"
                    >
                      <span class="notice-card-icon" aria-hidden="true">{{
                        noticeIcon(n)
                      }}</span>
                      <div class="notice-card-body">
                        <strong>{{ n.title }}</strong>
                        <span>{{ n.message }}</span>
                        <small v-if="noticeTime(n)">{{ noticeTime(n) }}</small>
                        <small v-else>Chưa có thời gian ghi nhận</small>
                      </div>
                      <b v-if="offerAvailable(n)"
                        >Nhận slot → · trước
                        {{ n.offerExpiresAt?.slice(11, 16) }}</b
                      >
                    </button>
                  </div>
                  <p v-else>
                    {{
                      notificationLoading
                        ? 'Đang tải thông báo…'
                        : 'Bạn chưa có thông báo.'
                    }}
                  </p>
                </template>
                <p v-else>Xem cập nhật trong bảng điều hành của bạn.</p>
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
          <button
            v-if="!auth"
            class="global-register"
            type="button"
            @click="navigateTo('register')"
          >
            Đăng ký
          </button>
          <div v-if="auth" ref="accountArea" class="account-area">
            <button
              ref="accountButton"
              class="login-button account-button"
              type="button"
              :aria-expanded="accountOpen"
              aria-controls="account-panel"
              :aria-label="`Tài khoản ${auth.user.fullName || auth.user.phone || ''}`"
              @click="toggleAccount"
            >
              <span class="account-avatar" aria-hidden="true">
                <img
                  v-if="auth.user.avatarUrl"
                  :src="customerImageUrl(auth.user.avatarUrl)"
                  alt=""
                />
                <template v-else>{{
                  (auth.user.fullName || auth.user.phone || 'U')
                    .trim()
                    .charAt(0)
                    .toUpperCase()
                }}</template>
              </span>
              <span class="account-name">
                {{ auth.user.fullName || auth.user.phone }}
              </span>
              <span class="account-arrow" aria-hidden="true"> ▾ </span>
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
              <div class="account-shortcuts">
                <button v-if="isAdmin" @click="navigateTo('admin')">
                  Trang quản trị ↗
                </button>
                <button v-if="isStaff" @click="navigateTo('staff')">
                  Bảng điều hành staff ↗
                </button>
                <template v-if="isCustomer"
                  ><button @click="navigateTo('my-bookings')">
                    Lịch đặt của tôi</button
                  ><button @click="navigateTo('history')">
                    Lịch sử đặt sân</button
                  ><button @click="navigateTo('profile')">
                    Hồ sơ cá nhân
                  </button></template
                >
              </div>
              <button class="logout-button" type="button" @click="logout">
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
              <path v-if="menuOpen" d="M6 6l12 12M18 6L6 18" />
              <path v-else d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        </div>
      </div>
      <form
        class="global-search"
        role="search"
        @submit.prevent="submitGlobalSearch"
      >
        <div class="container global-search-inner">
          <label class="search-scope"
            ><span class="sr-only">Loại tìm kiếm</span
            ><select v-model="globalScope">
              <option value="all">Tất cả</option>
              <option value="booking">Lịch trống</option>
              <option value="products">Sản phẩm</option>
              <option value="pricing">Bảng giá</option>
            </select></label
          ><label class="search-query"
            ><span class="sr-only">Từ khóa</span
            ><input
              v-model="globalQuery"
              placeholder="Tìm sân Premium, cầu lông, bảng giá…"
              maxlength="150" /></label
          ><label class="search-day"
            ><span class="sr-only">Ngày cần tìm sân</span
            ><input
              v-model="globalDate"
              type="date"
              :min="todayVN()"
              required /></label
          ><button type="submit">Tìm kiếm ↗</button>
        </div>
      </form>
    </header>
    <main id="page-content" class="page-content" tabindex="-1">
      <div v-if="notice" class="container notice" role="status">
        <span>{{ notice }}</span>
        <button type="button" aria-label="Đóng thông báo" @click="notice = ''">
          ×
        </button>
      </div>
      <AdminHomeView
        v-if="activePage === 'admin' && isAdmin"
        :auth="auth"
        @session-expired="handleSessionExpired"
      />
      <StaffHomeView
        v-else-if="activePage === 'staff' && isStaff"
        :auth="auth"
        @session-expired="handleSessionExpired"
      />
      <section
        v-else-if="
          ['booking', 'courts'].includes(activePage) && needsPhoneVerification
        "
        class="phone-gate"
      >
        <span class="phone-verify-label">CARROT · XÁC MINH TÀI KHOẢN</span>
        <h1>Một bước nữa, sẵn sàng ra sân.</h1>
        <p>
          Xác minh số điện thoại một lần để đặt sân và đăng ký Daily Visitor.
          Sau khi xác minh, bạn không cần nhập OTP cho mỗi booking.
        </p>
        <button class="phone-verify-primary" @click="openPhoneVerification">
          Xác minh số điện thoại →
        </button>
      </section>
      <component
        :is="
          ['booking', 'courts'].includes(activePage)
            ? BookingView
            : CustomerHomeView
        "
        v-else-if="
          ['booking', 'courts', 'my-bookings', 'history', 'profile'].includes(
            activePage,
          )
        "
        :auth="auth"
        :page="activePage"
        :group-filter="routeParams.type || ''"
        :room-filter="routeParams.room || ''"
        :initial-product="routeParams.product || ''"
        :initial-date="routeParams.date || ''"
        :initial-start="routeParams.start || ''"
        :initial-end="routeParams.end || ''"
        :initial-court="routeParams.court || ''"
        @navigate="navigateTo"
        @session-expired="handleSessionExpired"
        @profile-updated="handleProfileUpdated"
      />
      <ProductView
        v-else-if="activePage === 'products'"
        @navigate="navigateTo"
      />
      <PricingView
        v-else-if="activePage === 'pricing'"
        @navigate="navigateTo"
      />
      <NewsView
        v-else-if="activePage === 'news'"
        :article-id="routeParams.article || ''"
        @navigate="navigateTo"
      />
      <ContactView v-else-if="activePage === 'contact'" />
      <SearchView
        v-else-if="activePage === 'search'"
        :query="routeParams.q || ''"
        :scope="routeParams.scope || 'all'"
        :search-date="routeParams.date || ''"
        @navigate="navigateTo"
      />
      <LandingView
        v-else-if="['home', 'customer-preview'].includes(activePage)"
      />
      <LoginView
        v-else-if="['login', 'register'].includes(activePage) && !auth"
        :initial-mode="activePage === 'register' ? 'register' : 'login'"
        @login-success="handleLoginSuccess"
      />
      <section
        v-else-if="['login', 'register'].includes(activePage) && auth"
        class="container signed-in-section"
      >
        <h1>Bạn đã đăng nhập</h1>
        <p>Chào {{ auth.user.fullName || auth.user.phone }}!</p>
        <button class="small-button" type="button" @click="navigateTo('home')">
          Về trang chủ
        </button>
      </section>
    </main>
    <footer class="site-footer">
      <div class="container footer-invitation">
        <div>
          <span class="footer-kicker">YOUR NEXT GAME STARTS HERE</span>
          <h2>RA SÂN.<br /><em>BẬT ĐAM MÊ.</em></h2>
          <p>Một cú giao cầu. Một cuộc gặp mới. Một ngày chơi hết mình.</p>
        </div>
        <a href="#booking" class="footer-book-now" @click="closeHeaderPanels"
          ><span>CHỌN LỊCH CHƠI</span><strong>Đặt sân ngay ↗</strong></a
        >
      </div>
      <div class="container footer-grid">
        <div class="footer-brand">
          <a
            class="brand"
            href="#home"
            aria-label="Carrot Badminton - Trang chủ"
            @click="closeHeaderPanels"
          >
            <img src="/images/logo.png" alt="Carrot Badminton" />
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
          <h2>Kết nối cùng Carrot</h2>
          <a class="footer-hotline" href="tel:0397446844">0397 446 844 ↗</a>
          <a class="footer-email" href="mailto:carrot686868@gmail.com"
            >carrot686868@gmail.com</a
          >
          <p>Ninh Kiều, TP. Cần Thơ</p>
          <button class="footer-chat-link" type="button" @click="toggleChat">
            Trò chuyện với Carrot AI →
          </button>
        </div>
      </div>
      <div class="container footer-bottom">
        <p>© {{ new Date().getFullYear() }} Carrot Badminton.</p>
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
          <span class="ai-avatar" aria-hidden="true"> AI </span>
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
        <p class="chat-status">Bản giao diện · Chưa kết nối AI</p>
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
        <div v-if="messages.length === 1" class="chat-suggestions">
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
      <form class="chat-form" @submit.prevent="sendMessage()">
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
  <Teleport to="body">
    <div
      v-if="phoneVerificationOpen && needsPhoneVerification"
      class="phone-verify-overlay"
    >
      <section
        class="phone-verify-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="phone-verify-title"
      >
        <button
          type="button"
          class="phone-verify-close"
          aria-label="Đóng xác minh"
          :disabled="verificationBusy"
          @click="phoneVerificationOpen = false"
        >
          ×
        </button>
        <span class="phone-verify-label">CARROT · SẴN SÀNG RA SÂN</span>
        <h2 id="phone-verify-title">Xác minh số điện thoại</h2>
        <p>
          Chỉ cần xác minh một lần. Bạn có thể tiếp tục xem website và quay lại
          xác minh trước khi đặt sân.
        </p>
        <form
          @submit.prevent="confirmVerificationOtp"
          :aria-busy="verificationBusy"
        >
          <label for="account-verification-phone">Số điện thoại</label>
          <div class="phone-verify-send-row">
            <input
              id="account-verification-phone"
              v-model="verificationPhone"
              type="tel"
              autocomplete="tel"
              placeholder="Ví dụ: 0901234567"
              :disabled="verificationBusy"
              required
            />
            <button
              type="button"
              class="phone-verify-send"
              :disabled="verificationBusy || verificationCooldown > 0"
              @click="sendVerificationOtp"
            >
              {{
                verificationCooldown > 0
                  ? `Gửi lại (${verificationCooldown}s)`
                  : verificationSentPhone
                    ? 'Gửi lại OTP'
                    : 'Gửi OTP'
              }}
            </button>
          </div>
          <label for="account-verification-otp">Mã xác nhận gồm 6 chữ số</label>
          <input
            id="account-verification-otp"
            v-model="verificationOtp"
            class="phone-verify-code"
            type="text"
            inputmode="numeric"
            autocomplete="one-time-code"
            maxlength="6"
            placeholder="000000"
            :disabled="verificationBusy || !verificationSentPhone"
            required
          />
          <p
            v-if="verificationMessage"
            class="phone-verify-success"
            role="status"
          >
            {{ verificationMessage }}
          </p>
          <p v-if="verificationError" class="phone-verify-error" role="alert">
            {{ verificationError }}
          </p>
          <button
            class="phone-verify-primary"
            type="submit"
            :disabled="verificationBusy || !verificationSentPhone"
          >
            {{ verificationBusy ? 'Đang xử lý…' : 'Xác nhận số điện thoại' }}
          </button>
        </form>
      </section>
    </div>
  </Teleport>
</template>
<style scoped>
.admin-label {
  color: #fff;
  font-size: 10px;
  letter-spacing: 1px;
  border: 1px solid #ffffff50;
  padding: 4px 8px;
  border-radius: 6px;
}
@media (max-width: 480px) {
  .admin-label {
    display: none;
  }
}
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
    * background * 160ms ease,
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
.site-header {
  top: 0;
  position: sticky;
  z-index: 100;
  background: #005b35;
  box-shadow: 0 4px 18px #122b1b15;
}
.header-inner {
  max-width: 1440px;
  min-height: 82px;
  gap: 20px;
}
.main-navigation {
  flex: 1;
  justify-content: center;
  gap: 20px;
}
.main-navigation a {
  font-size: 12px;
  white-space: nowrap;
}
.global-register {
  border: 1px solid #b7ceb666;
  border-radius: 7px;
  background: transparent;
  color: white;
  padding: 11px 14px;
  font: inherit;
  font-size: 12px;
  font-weight: 750;
  cursor: pointer;
}
.global-search {
  background: #005b35;
  border-top: 1px solid rgb(255 255 255 / 12%);
  border-bottom: 1px solid rgb(0 0 0 / 10%);
  padding: 6px 0 8px;
}
.global-search-inner {
  max-width: 1440px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.global-search label {
  display: block;
}
.global-search input,
.global-search select {
  min-height: 32px;
  border: 1px solid rgb(255 255 255 / 20%);
  border-radius: 4px;
  background: #064e32;
  padding: 5px 10px;
  color: #f5fff8;
  font: inherit;
  font-size: 12px;
  width: 100%;
  box-sizing: border-box;
}
.search-query {
  flex: 1;
  min-width: 0;
}
.search-scope {
  width: 140px;
}
.search-day {
  width: 150px;
}
.global-search button {
  min-height: 32px;
  background: #ff8500;
  color: #17351d;
  border: 0;
  border-radius: 4px;
  padding: 6px 16px;
  font: inherit;
  font-size: 12px;
  font-weight: 850;
  cursor: pointer;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}
.account-shortcuts {
  display: grid;
  border-top: 1px solid #e5eadf;
  padding: 10px 0;
}
.account-shortcuts button {
  text-align: left;
  background: transparent;
  border: 0;
  padding: 10px 15px;
  color: #49663b;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.account-shortcuts button:hover {
  background: #f0f5e9;
}
.booking-notices {
  display: grid;
  gap: 7px;
}
.booking-notices button {
  text-align: left;
  border: 1px solid #dfe8d6;
  background: #f5f8ef;
  padding: 12px;
  border-radius: 5px;
  cursor: pointer;
}
.booking-notices strong,
.booking-notices span {
  display: block;
  font-size: 11px;
  color: #637c50;
}
.booking-notices span {
  font-size: 10px;
  margin-top: 5px;
}
@media (max-width: 1200px) {
  .main-navigation {
    gap: 12px;
  }
  .main-navigation a {
    font-size: 11px;
  }
  .header-inner {
    gap: 12px;
  }
  .account-name {
    max-width: 90px;
  }
}
@media (max-width: 1050px) {
  .menu-toggle {
    display: flex;
  }
  .header-inner {
    flex-wrap: wrap;
  }
  .main-navigation {
    display: none;
    flex-basis: 100%;
    order: 3;
  }
  .main-navigation.open {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 10px;
    padding: 10px 0;
  }
  .header-actions {
    margin-left: auto;
  }
  .main-navigation a {
    text-align: center;
    padding: 10px;
  }
  .account-name {
    display: none;
  }
}
@media (max-width: 650px) {
  .global-search-inner {
    gap: 6px;
    flex-wrap: wrap;
  }
  .search-query {
    order: 1;
    flex-basis: 70%;
  }
  .global-search button {
    order: 2;
    padding: 9px 13px;
  }
  .search-scope {
    order: 3;
    flex: 1;
  }
  .search-day {
    order: 4;
    flex: 1;
  }
  .global-register {
    padding: 9px;
    font-size: 11px;
  }
  .header-actions {
    gap: 8px;
  }
  .main-navigation.open {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .header-inner {
    min-height: 70px;
  }
  .global-search input,
  .global-search select {
    min-height: 33px;
    font-size: 11px;
  }
  .global-search {
    padding: 7px 0;
  }
}
.global-search-inner {
  max-width: 1060px;
  gap: 8px;
}
.global-search input::placeholder {
  color: #c1d8c8;
}
.global-search input[type='date'] {
  color-scheme: dark;
}
.global-search select option {
  background: #064e32;
  color: #fff;
}
.global-search input:focus-visible,
.global-search select:focus-visible {
  outline: 2px solid #ffad48;
  outline-offset: 2px;
}
.notification-button {
  position: relative;
}
.notification-count {
  position: absolute;
  top: -3px;
  right: -5px;
  min-width: 19px;
  height: 19px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  background: #ff8500;
  color: #1e251f;
  font-size: 10px;
  font-weight: 900;
  border: 2px solid #005b35;
}
.notification-panel {
  width: min(410px, calc(100vw - 32px));
  max-height: min(620px, 80vh);
  overflow-y: auto;
}
.notification-tools {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  width: 100%;
}
.notification-tools button {
  background: none;
  border: 0;
  color: #005b35;
  cursor: pointer;
  font-weight: 800;
  padding: 8px;
}
.booking-notices {
  width: 100%;
  display: grid;
  gap: 8px;
  text-align: left;
}
.booking-notices button {
  position: relative;
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 14px 16px;
  background: #f7f7f2;
  border: 1px solid #e6e9e0;
  border-radius: 12px;
  text-align: left;
  cursor: pointer;
  transition: * background * 0.2s;
}
.booking-notices button.unread {
  background: #fff4e4;
  border-left: 3px solid #ff8500;
}
.booking-notices button.invitation {
  background: #f1eafb;
  border-color: #b89fda;
}
.booking-notices button:hover {
  background: #eaf2e9;
}
.booking-notices strong {
  font-size: 13px;
  color: #213025;
}
.booking-notices span,
.booking-notices small {
  color: #627167;
  font-size: 12px;
  line-height: 1.6;
}
.booking-notices b {
  color: #6d359b;
  font-size: 12px;
}
.notification-offer {
  padding: 18px;
  border-radius: 14px;
  background: #f4edff;
  color: #3b2553;
  text-align: left;
}
.notification-offer button {
  border: 0;
  border-radius: 8px;
  padding: 11px;
  margin: 4px;
  color: #fff;
  background: #6f4398;
  cursor: pointer;
}
.notification-offer button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.notification-error {
  color: #a32d2d;
}
.account-avatar {
  overflow: hidden;
}
.account-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
/* Một nền dùng chung cho toàn bộ header và thanh tìm kiếm. */
.site-header {
  --header-surface: #005b35;
  background: var(--header-surface) !important;
}
.site-header > .global-search {
  background: var(--header-surface) !important;
  background-image: none !important;
  border-top: 0;
  border-bottom: 0;
  box-shadow: none !important;
  margin: 0;
}
.site-header .global-search-inner,
.site-header .global-search label {
  background: transparent !important;
  box-shadow: none !important;
}
.site-header .global-search input,
.site-header .global-search select {
  background: transparent !important;
  background-image: none !important;
  color: #fff !important;
  border: 1px solid rgb(255 255 255 / 25%);
  box-shadow: none !important;
}
.site-header .global-search select option {
  background: #005b35;
  color: #fff;
}

/* Cinematic notification centre */
.notification-button {
  transition:
    transform 0.2s,
    background 0.2s;
}
.notification-button:hover,
.notification-button.active {
  background: rgba(255, 255, 255, 0.18);
  transform: translateY(-2px);
}
.notification-panel {
  width: min(440px, calc(100vw - 32px));
  background:
    radial-gradient(ellipse at top right, #254d40 0, transparent 60%), #111e19;
  color: #f3f5ef;
  border: 1px solid rgba(220, 239, 222, 0.18);
  border-radius: 22px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.38);
  padding: 0;
  animation: notice-reveal 0.24s ease-out;
  scrollbar-width: thin;
  scrollbar-color: #526d5c #111e19;
}
.notification-panel .panel-heading {
  padding: 24px 22px 18px;
  align-items: flex-start;
  border-bottom: 1px solid rgba(255, 255, 255, 0.09);
}
.notification-eyebrow {
  color: #efb871;
  font-size: 9px;
  letter-spacing: 2px;
  font-weight: 800;
}
.notification-panel h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-size: 23px;
  margin: 10px 0 6px;
  letter-spacing: -0.6px;
}
.notification-live {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a8e8b9;
  box-shadow: 0 0 12px #a8e8b9;
}
.notification-subtitle {
  margin: 0;
  color: #aebdb2;
  font-size: 12px;
}
.notification-panel .panel-close {
  background: rgba(255, 255, 255, 0.08);
  color: #eee;
  border-radius: 50%;
}
.notification-panel .notification-empty {
  padding: 16px;
  gap: 14px;
  color: #b5c2b9;
}
.notification-panel .notification-tools {
  color: #aebeb3;
  font-size: 11px;
}
.notification-panel .notification-tools button {
  color: #f2c180;
}
.notification-panel .booking-notices {
  gap: 10px;
}
.notification-panel .booking-notices > button {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.09);
  padding: 16px;
  border-radius: 15px;
  transition:
    background 0.2s,
    border-color 0.2s,
    transform 0.2s;
}
.notification-panel .booking-notices > button:hover {
  background: rgba(255, 255, 255, 0.09);
  border-color: rgba(242, 193, 128, 0.4);
  transform: translateY(-2px);
}
.notification-panel .booking-notices > button.unread {
  border-left: 3px solid #f2b96f;
  background: rgba(242, 185, 111, 0.08);
}
.notification-panel .booking-notices > button.invitation {
  border-color: #82b99b;
  background: rgba(130, 185, 155, 0.1);
}
.notification-panel .booking-notices .notice-card-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(242, 193, 128, 0.12);
  color: #f2c180;
  font-size: 21px;
}
.notice-card-body {
  display: grid;
  gap: 6px;
  min-width: 0;
}
.notification-panel .booking-notices strong {
  color: #f3f5ef;
  font-size: 13px;
  line-height: 1.5;
}
.notification-panel .booking-notices .notice-card-body > span {
  color: #b9c7be;
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.notification-panel .booking-notices small {
  color: #859b8e;
  font-size: 10px;
}
.notification-panel .booking-notices b {
  grid-column: 2;
  color: #b6e5c9;
  font-size: 11px;
}
.notification-panel .notification-offer {
  background: rgba(130, 185, 155, 0.1);
  border: 1px solid #648b74;
  color: #e2eee5;
  border-radius: 16px;
  padding: 18px;
}
.notification-panel .notification-offer strong {
  color: #fff;
}
.notification-panel .notification-offer button {
  background: #edbd7c;
  color: #17251d;
  min-height: 40px;
}
.notification-panel .notification-error {
  background: #3b2526;
  color: #ffc5bd;
}
.notification-panel button:focus-visible {
  outline: 2px solid #edbd7c;
  outline-offset: 3px;
}
@keyframes notice-reveal {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .notification-panel {
    animation: none;
  }
  .notification-button,
  .notification-panel .booking-notices > button {
    transition: none;
  }
  .notification-button:hover,
  .notification-panel .booking-notices > button:hover {
    transform: none;
  }
}

/* Sport / cinema global identity */
.site-header {
  --header-surface: #111b18;
  background: var(--header-surface) !important;
  border-top: 3px solid #f5953b;
  border-bottom: 1px solid #ffffff15;
  box-shadow: 0 10px 35px #00000025;
}
.site-header .header-inner {
  min-height: 78px;
  gap: 18px;
}
.site-header .brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}
.site-header .brand img {
  width: 46px;
  height: 46px;
  object-fit: contain;
  border-radius: 12px;
  background: #fff;
  padding: 4px;
}
.header-brand-copy {
  display: grid;
  gap: 3px;
}
.header-brand-copy strong {
  font-size: 23px;
  font-weight: 950;
  font-style: italic;
  letter-spacing: -1px;
  color: #fff;
  line-height: 1;
}
.header-brand-copy small {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 2px;
  color: #d5a96f;
}
.site-header .main-navigation {
  gap: 6px;
}
.site-header .main-navigation a {
  color: #bac4bd;
  font-size: 12px;
  font-weight: 700;
  padding: 12px 10px;
  border-radius: 7px;
  transition:
    color 0.2s,
    background 0.2s;
}
.site-header .main-navigation a:hover {
  color: #fff;
  background: #ffffff08;
}
.site-header .main-navigation a.active {
  color: #ffc281;
  background: #f5953b12;
  box-shadow: inset 0 -2px #f5953b;
}
.site-header .global-register {
  color: #ffba75;
  border-color: #f5953b66;
  border-radius: 8px;
}
.site-header .header-actions .login-button {
  background: #f5953b;
  color: #151d18;
  border-color: #f5953b;
}
.site-header > .global-search {
  background: var(--header-surface) !important;
  border-top: 1px solid #ffffff0d;
  border-bottom: 0;
  padding: 7px 0 10px;
}
.site-header .global-search-inner {
  gap: 8px;
}
.site-header .global-search input,
.site-header .global-search select {
  background: #ffffff07 !important;
  border: 1px solid #ffffff17 !important;
  color: #dbe3dd;
  border-radius: 7px;
  height: 34px;
  font-size: 11px;
  padding-block: 6px;
}
.site-header .global-search input::placeholder {
  color: #89968c;
}
.site-header .global-search select option {
  background: #17221c !important;
  color: #fff;
}
.site-header .global-search input[type='date'] {
  color-scheme: dark;
}
.site-header .global-search button {
  background: #e9b277;
  color: #17231a;
  border: 0;
  height: 34px;
  padding: 0 18px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 800;
}
.site-header .global-search input:focus-visible,
.site-header .global-search select:focus-visible {
  outline: 2px solid #f5953b;
  outline-offset: 2px;
}
.site-footer {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at top right, #34402b55, transparent 55%), #111b18;
  color: #bec8bf;
  padding-top: 0;
  border-top: 1px solid #5c675322;
}
.site-footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 35%;
  height: 3px;
  background: #f5953b;
  pointer-events: none;
}
.footer-invitation {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
  padding-top: 64px;
  padding-bottom: 54px;
  border-bottom: 1px solid #ffffff15;
}
.footer-kicker {
  color: #d8a56c;
  font-size: 10px;
  letter-spacing: 3px;
  font-weight: 800;
}
.site-footer .footer-invitation h2 {
  color: #fff;
  font-size: clamp(40px, 6vw, 78px);
  line-height: 1;
  font-weight: 950;
  font-style: italic;
  letter-spacing: -3px;
  margin: 20px 0;
}
.footer-invitation h2 em {
  color: #f5ad63;
}
.footer-invitation p {
  color: #9cae9e;
  font-size: 13px;
  line-height: 1.8;
}
.footer-book-now {
  display: grid;
  gap: 14px;
  padding: 24px 30px;
  border: 1px solid #d3a16b55;
  border-radius: 14px;
  background: #eab57608;
  text-decoration: none;
  transition:
    background 0.25s,
    transform 0.25s;
}
.footer-book-now span {
  font-size: 9px;
  letter-spacing: 2px;
  color: #b4beae;
}
.footer-book-now strong {
  color: #f7c68c;
  font-size: 22px;
}
.footer-book-now:hover {
  transform: translateY(-4px);
  background: #eab57615;
}
.site-footer .footer-grid {
  padding-top: 40px;
  padding-bottom: 40px;
  gap: 40px;
}
.site-footer .footer-brand img {
  max-width: 150px;
  background: #fff;
  border-radius: 12px;
  padding: 8px;
}
.site-footer .footer-brand p {
  color: #93a495;
  font-size: 13px;
  line-height: 1.9;
}
.site-footer .footer-grid h2 {
  color: #f2e8d6;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  margin-bottom: 18px;
}
.site-footer .footer-navigation a {
  color: #acbcad;
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s;
}
.site-footer .footer-navigation a:hover {
  color: #ffbb76;
}
.site-footer .footer-support a {
  display: block;
  text-decoration: none;
}
.site-footer .footer-hotline {
  color: #edc38e;
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
.site-footer .footer-email {
  color: #a9b7a7;
  font-size: 12px;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.site-footer .footer-support p {
  color: #91a18e;
  font-size: 12px;
}
.site-footer .footer-chat-link {
  color: #f3bd7c;
  background: #ffffff07;
  border: 1px solid #ffffff18;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 12px;
}
.site-footer .footer-bottom {
  border-top: 1px solid #ffffff12;
  padding-top: 20px;
  padding-bottom: 24px;
  color: #7f907e;
  font-size: 10px;
}
.site-footer .footer-bottom > span {
  color: #a49c80;
  letter-spacing: 2px;
}
@media (max-width: 1100px) {
  .header-brand-copy {
    display: none;
  }
  .site-header .main-navigation a {
    padding-inline: 7px;
    font-size: 11px;
  }
}
@media (max-width: 900px) {
  .site-header .main-navigation.open {
    background: #111b18;
    border-color: #ffffff1a;
  }
  .site-header .main-navigation.open a {
    padding: 12px 16px;
  }
}
@media (max-width: 640px) {
  .site-header .header-inner {
    min-height: 64px;
    gap: 8px;
  }
  .site-header .brand img {
    width: 36px;
    height: 36px;
  }
  .footer-invitation {
    flex-direction: column;
    align-items: flex-start;
    padding-top: 40px;
    padding-bottom: 32px;
    gap: 18px;
  }
  .footer-book-now {
    width: 100%;
    padding: 20px;
  }
  .site-footer .footer-grid {
    gap: 28px;
  }
  .site-header .global-search-inner {
    flex-wrap: wrap;
  }
}
@media (prefers-reduced-motion: reduce) {
  .footer-book-now,
  .site-header .main-navigation a {
    transition: none;
  }
  .footer-book-now:hover {
    transform: none;
  }
}
</style>

<style scoped>
.phone-gate {
  max-width: 760px;
  margin: 60px auto;
  padding: 40px;
  border: 1px solid #dce6df;
  border-radius: 24px;
  background: #f8faf5;
  color: #183c2b;
}
.phone-gate h1 {
  font-size: clamp(26px, 4vw, 38px);
}
.phone-gate p {
  line-height: 1.8;
  color: #627267;
}
.phone-verify-overlay {
  position: fixed;
  inset: 0;
  z-index: 5000;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(8 24 17 / 65%);
  backdrop-filter: blur(8px);
}
.phone-verify-dialog {
  position: relative;
  width: min(100%, 470px);
  box-sizing: border-box;
  max-height: 90dvh;
  overflow-y: auto;
  padding: 36px;
  border: 1px solid #dae7dc;
  border-radius: 24px;
  background: #fffdf6;
  color: #193526;
  box-shadow: 0 24px 80px rgb(0 0 0 / 25%);
}
.phone-verify-label {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 2px;
  color: #916326;
}
.phone-verify-dialog h2 {
  margin: 16px 0 12px;
  font-size: 28px;
  letter-spacing: -1px;
}
.phone-verify-dialog p {
  font-size: 13px;
  line-height: 1.8;
  color: #65756a;
}
.phone-verify-dialog label {
  display: block;
  margin: 20px 0 8px;
  font-size: 13px;
  font-weight: 700;
}
.phone-verify-dialog input {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
  height: 48px;
  padding: 12px;
  border: 1px solid #d9e1d8;
  border-radius: 10px;
  background: white;
  color: #183c2b;
  font: inherit;
}
.phone-verify-send-row {
  display: flex;
  gap: 8px;
}
.phone-verify-send {
  flex-shrink: 0;
  border: 1px solid #d4dfd4;
  border-radius: 10px;
  padding: 0 12px;
  color: #235b3c;
  background: #edf4e9;
  font-weight: 700;
  cursor: pointer;
}
.phone-verify-code {
  letter-spacing: 8px;
  text-align: center;
  font-size: 22px !important;
}
.phone-verify-primary {
  width: 100%;
  min-height: 48px;
  margin-top: 20px;
  border: none;
  border-radius: 12px;
  background: #005b35;
  color: white;
  padding: 12px 18px;
  font-weight: 700;
  cursor: pointer;
}
.phone-verify-close {
  position: absolute;
  top: 12px;
  right: 16px;
  border: 0;
  background: transparent;
  color: #506454;
  font-size: 28px;
  cursor: pointer;
}
.phone-verify-dialog button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.phone-verify-dialog :focus-visible {
  outline: 3px solid #ed9b34;
  outline-offset: 3px;
}
.phone-verify-dialog .phone-verify-error {
  padding: 10px;
  border-radius: 8px;
  color: #a03030;
  background: #fff0ef;
}
.phone-verify-dialog .phone-verify-success {
  padding: 10px;
  border-radius: 8px;
  color: #326542;
  background: #edf6eb;
}
@media (max-width: 480px) {
  .phone-verify-dialog {
    padding: 28px 20px;
  }
  .phone-gate {
    margin: 24px 16px;
    padding: 24px;
  }
}

/* Cinematic notification centre */
.notification-button {
  transition:
    transform 0.2s,
    background 0.2s;
}
.notification-button:hover,
.notification-button.active {
  background: rgba(255, 255, 255, 0.18);
  transform: translateY(-2px);
}
.notification-panel {
  width: min(440px, calc(100vw - 32px));
  background:
    radial-gradient(ellipse at top right, #254d40 0, transparent 60%), #111e19;
  color: #f3f5ef;
  border: 1px solid rgba(220, 239, 222, 0.18);
  border-radius: 22px;
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.38);
  padding: 0;
  animation: notice-reveal 0.24s ease-out;
  scrollbar-width: thin;
  scrollbar-color: #526d5c #111e19;
}
.notification-panel .panel-heading {
  padding: 24px 22px 18px;
  align-items: flex-start;
  border-bottom: 1px solid rgba(255, 255, 255, 0.09);
}
.notification-eyebrow {
  color: #efb871;
  font-size: 9px;
  letter-spacing: 2px;
  font-weight: 800;
}
.notification-panel h2 {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-size: 23px;
  margin: 10px 0 6px;
  letter-spacing: -0.6px;
}
.notification-live {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a8e8b9;
  box-shadow: 0 0 12px #a8e8b9;
}
.notification-subtitle {
  margin: 0;
  color: #aebdb2;
  font-size: 12px;
}
.notification-panel .panel-close {
  background: rgba(255, 255, 255, 0.08);
  color: #eee;
  border-radius: 50%;
}
.notification-panel .notification-empty {
  padding: 16px;
  gap: 14px;
  color: #b5c2b9;
}
.notification-panel .notification-tools {
  color: #aebeb3;
  font-size: 11px;
}
.notification-panel .notification-tools button {
  color: #f2c180;
}
.notification-panel .booking-notices {
  gap: 10px;
}
.notification-panel .booking-notices > button {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.09);
  padding: 16px;
  border-radius: 15px;
  transition:
    background 0.2s,
    border-color 0.2s,
    transform 0.2s;
}
.notification-panel .booking-notices > button:hover {
  background: rgba(255, 255, 255, 0.09);
  border-color: rgba(242, 193, 128, 0.4);
  transform: translateY(-2px);
}
.notification-panel .booking-notices > button.unread {
  border-left: 3px solid #f2b96f;
  background: rgba(242, 185, 111, 0.08);
}
.notification-panel .booking-notices > button.invitation {
  border-color: #82b99b;
  background: rgba(130, 185, 155, 0.1);
}
.notification-panel .booking-notices .notice-card-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: rgba(242, 193, 128, 0.12);
  color: #f2c180;
  font-size: 21px;
}
.notice-card-body {
  display: grid;
  gap: 6px;
  min-width: 0;
}
.notification-panel .booking-notices strong {
  color: #f3f5ef;
  font-size: 13px;
  line-height: 1.5;
}
.notification-panel .booking-notices .notice-card-body > span {
  color: #b9c7be;
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.notification-panel .booking-notices small {
  color: #859b8e;
  font-size: 10px;
}
.notification-panel .booking-notices b {
  grid-column: 2;
  color: #b6e5c9;
  font-size: 11px;
}
.notification-panel .notification-offer {
  background: rgba(130, 185, 155, 0.1);
  border: 1px solid #648b74;
  color: #e2eee5;
  border-radius: 16px;
  padding: 18px;
}
.notification-panel .notification-offer strong {
  color: #fff;
}
.notification-panel .notification-offer button {
  background: #edbd7c;
  color: #17251d;
  min-height: 40px;
}
.notification-panel .notification-error {
  background: #3b2526;
  color: #ffc5bd;
}
.notification-panel button:focus-visible {
  outline: 2px solid #edbd7c;
  outline-offset: 3px;
}
@keyframes notice-reveal {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .notification-panel {
    animation: none;
  }
  .notification-button,
  .notification-panel .booking-notices > button {
    transition: none;
  }
  .notification-button:hover,
  .notification-panel .booking-notices > button:hover {
    transform: none;
  }
}

/* Sport / cinema global identity */
.site-header {
  --header-surface: #111b18;
  background: var(--header-surface) !important;
  border-top: 3px solid #f5953b;
  border-bottom: 1px solid #ffffff15;
  box-shadow: 0 10px 35px #00000025;
}
.site-header .header-inner {
  min-height: 78px;
  gap: 18px;
}
.site-header .brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  flex-shrink: 0;
}
.site-header .brand img {
  width: 46px;
  height: 46px;
  object-fit: contain;
  border-radius: 12px;
  background: #fff;
  padding: 4px;
}
.header-brand-copy {
  display: grid;
  gap: 3px;
}
.header-brand-copy strong {
  font-size: 23px;
  font-weight: 950;
  font-style: italic;
  letter-spacing: -1px;
  color: #fff;
  line-height: 1;
}
.header-brand-copy small {
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 2px;
  color: #d5a96f;
}
.site-header .main-navigation {
  gap: 6px;
}
.site-header .main-navigation a {
  color: #bac4bd;
  font-size: 12px;
  font-weight: 700;
  padding: 12px 10px;
  border-radius: 7px;
  transition:
    color 0.2s,
    background 0.2s;
}
.site-header .main-navigation a:hover {
  color: #fff;
  background: #ffffff08;
}
.site-header .main-navigation a.active {
  color: #ffc281;
  background: #f5953b12;
  box-shadow: inset 0 -2px #f5953b;
}
.site-header .global-register {
  color: #ffba75;
  border-color: #f5953b66;
  border-radius: 8px;
}
.site-header .header-actions .login-button {
  background: #f5953b;
  color: #151d18;
  border-color: #f5953b;
}
.site-header > .global-search {
  background: var(--header-surface) !important;
  border-top: 1px solid #ffffff0d;
  border-bottom: 0;
  padding: 7px 0 10px;
}
.site-header .global-search-inner {
  gap: 8px;
}
.site-header .global-search input,
.site-header .global-search select {
  background: #ffffff07 !important;
  border: 1px solid #ffffff17 !important;
  color: #dbe3dd;
  border-radius: 7px;
  height: 34px;
  font-size: 11px;
  padding-block: 6px;
}
.site-header .global-search input::placeholder {
  color: #89968c;
}
.site-header .global-search select option {
  background: #17221c !important;
  color: #fff;
}
.site-header .global-search input[type='date'] {
  color-scheme: dark;
}
.site-header .global-search button {
  background: #e9b277;
  color: #17231a;
  border: 0;
  height: 34px;
  padding: 0 18px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 800;
}
.site-header .global-search input:focus-visible,
.site-header .global-search select:focus-visible {
  outline: 2px solid #f5953b;
  outline-offset: 2px;
}
.site-footer {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at top right, #34402b55, transparent 55%), #111b18;
  color: #bec8bf;
  padding-top: 0;
  border-top: 1px solid #5c675322;
}
.site-footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 35%;
  height: 3px;
  background: #f5953b;
  pointer-events: none;
}
.footer-invitation {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
  padding-top: 64px;
  padding-bottom: 54px;
  border-bottom: 1px solid #ffffff15;
}
.footer-kicker {
  color: #d8a56c;
  font-size: 10px;
  letter-spacing: 3px;
  font-weight: 800;
}
.site-footer .footer-invitation h2 {
  color: #fff;
  font-size: clamp(40px, 6vw, 78px);
  line-height: 1;
  font-weight: 950;
  font-style: italic;
  letter-spacing: -3px;
  margin: 20px 0;
}
.footer-invitation h2 em {
  color: #f5ad63;
}
.footer-invitation p {
  color: #9cae9e;
  font-size: 13px;
  line-height: 1.8;
}
.footer-book-now {
  display: grid;
  gap: 14px;
  padding: 24px 30px;
  border: 1px solid #d3a16b55;
  border-radius: 14px;
  background: #eab57608;
  text-decoration: none;
  transition:
    background 0.25s,
    transform 0.25s;
}
.footer-book-now span {
  font-size: 9px;
  letter-spacing: 2px;
  color: #b4beae;
}
.footer-book-now strong {
  color: #f7c68c;
  font-size: 22px;
}
.footer-book-now:hover {
  transform: translateY(-4px);
  background: #eab57615;
}
.site-footer .footer-grid {
  padding-top: 40px;
  padding-bottom: 40px;
  gap: 40px;
}
.site-footer .footer-brand img {
  max-width: 150px;
  background: #fff;
  border-radius: 12px;
  padding: 8px;
}
.site-footer .footer-brand p {
  color: #93a495;
  font-size: 13px;
  line-height: 1.9;
}
.site-footer .footer-grid h2 {
  color: #f2e8d6;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 1.5px;
  margin-bottom: 18px;
}
.site-footer .footer-navigation a {
  color: #acbcad;
  font-size: 13px;
  text-decoration: none;
  transition: color 0.2s;
}
.site-footer .footer-navigation a:hover {
  color: #ffbb76;
}
.site-footer .footer-support a {
  display: block;
  text-decoration: none;
}
.site-footer .footer-hotline {
  color: #edc38e;
  font-size: 25px;
  font-weight: 800;
  letter-spacing: -0.5px;
}
.site-footer .footer-email {
  color: #a9b7a7;
  font-size: 12px;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.site-footer .footer-support p {
  color: #91a18e;
  font-size: 12px;
}
.site-footer .footer-chat-link {
  color: #f3bd7c;
  background: #ffffff07;
  border: 1px solid #ffffff18;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 12px;
}
.site-footer .footer-bottom {
  border-top: 1px solid #ffffff12;
  padding-top: 20px;
  padding-bottom: 24px;
  color: #7f907e;
  font-size: 10px;
}
.site-footer .footer-bottom > span {
  color: #a49c80;
  letter-spacing: 2px;
}
@media (max-width: 1100px) {
  .header-brand-copy {
    display: none;
  }
  .site-header .main-navigation a {
    padding-inline: 7px;
    font-size: 11px;
  }
}
@media (max-width: 900px) {
  .site-header .main-navigation.open {
    background: #111b18;
    border-color: #ffffff1a;
  }
  .site-header .main-navigation.open a {
    padding: 12px 16px;
  }
}
@media (max-width: 640px) {
  .site-header .header-inner {
    min-height: 64px;
    gap: 8px;
  }
  .site-header .brand img {
    width: 36px;
    height: 36px;
  }
  .footer-invitation {
    flex-direction: column;
    align-items: flex-start;
    padding-top: 40px;
    padding-bottom: 32px;
    gap: 18px;
  }
  .footer-book-now {
    width: 100%;
    padding: 20px;
  }
  .site-footer .footer-grid {
    gap: 28px;
  }
  .site-header .global-search-inner {
    flex-wrap: wrap;
  }
}
@media (prefers-reduced-motion: reduce) {
  .footer-book-now,
  .site-header .main-navigation a {
    transition: none;
  }
  .footer-book-now:hover {
    transform: none;
  }
}
</style>

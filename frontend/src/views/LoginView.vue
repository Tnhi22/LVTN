<script setup>
import {
  computed,
  nextTick,
  reactive,
  ref,
  watch,
  onMounted,
  onUnmounted,
} from 'vue';
import {
  loginWithIdentifier,
  loginByGoogle,
  normalizePhone,
  registerCustomer,
} from '../services/authService.js';

import { mountGoogleButton } from '../services/googleIdentity.js';

const emit = defineEmits(['login-success']);
const props = defineProps({ initialMode: { type: String, default: 'login' } });
const mode = ref(props.initialMode);
watch(
  () => props.initialMode,
  (value) => switchMode(value),
);
const identifier = ref('');
const password = ref('');
const showPassword = ref(false);
const showRegisterPassword = ref(false);
const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');
const identifierInput = ref(null);
const nameInput = ref(null);
const registration = reactive({
  fullName: '',
  phone: '',
  email: '',
  password: '',
  confirmPassword: '',
});
const registering = computed(() => mode.value === 'register');
const validEmail = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

async function switchMode(nextMode) {
  if (loading.value) return;
  mode.value = nextMode;
  errorMessage.value = '';
  successMessage.value = '';
  password.value = '';
  registration.password = '';
  registration.confirmPassword = '';
  showPassword.value = false;
  showRegisterPassword.value = false;
  await nextTick();
  if (nextMode === 'login') identifierInput.value?.focus();
  else nameInput.value?.focus();
}

async function submitLogin() {
  if (loading.value) return;
  errorMessage.value = '';
  successMessage.value = '';
  const value = identifier.value.trim();
  if (value.includes('@')) {
    if (!validEmail(value)) {
      errorMessage.value = 'Vui lòng nhập email hợp lệ.';
      return;
    }
  } else if (!/^0[35789]\d{8}$/.test(normalizePhone(value))) {
    errorMessage.value =
      'Vui lòng nhập email hoặc số điện thoại Việt Nam hợp lệ.';
    return;
  }
  if (!password.value.trim()) {
    errorMessage.value = 'Vui lòng nhập mật khẩu.';
    return;
  }
  if (new TextEncoder().encode(password.value).length > 72) {
    errorMessage.value = 'Mật khẩu không được vượt quá 72 byte.';
    return;
  }
  loading.value = true;
  try {
    const result = await loginWithIdentifier({
      identifier: value,
      password: password.value,
    });
    password.value = '';
    showPassword.value = false;
    emit('login-success', result);
  } catch (error) {
    errorMessage.value = error.message || 'Không thể đăng nhập.';
  } finally {
    loading.value = false;
  }
}

async function submitRegister() {
  if (loading.value) return;
  errorMessage.value = '';
  successMessage.value = '';
  if (!registration.fullName.trim()) {
    errorMessage.value = 'Vui lòng nhập họ và tên.';
    return;
  }
  if (!/^0[35789]\d{8}$/.test(normalizePhone(registration.phone))) {
    errorMessage.value = 'Vui lòng nhập số điện thoại Việt Nam hợp lệ.';
    return;
  }
  if (!validEmail(registration.email.trim())) {
    errorMessage.value = 'Vui lòng nhập email hợp lệ.';
    return;
  }
  if (
    registration.password.trim().length === 0 ||
    registration.password.length < 8
  ) {
    errorMessage.value = 'Mật khẩu phải có ít nhất 8 ký tự.';
    return;
  }
  if (new TextEncoder().encode(registration.password).length > 72) {
    errorMessage.value = 'Mật khẩu không được vượt quá 72 byte.';
    return;
  }
  if (registration.password !== registration.confirmPassword) {
    errorMessage.value = 'Mật khẩu xác nhận không khớp.';
    return;
  }
  loading.value = true;
  try {
    const result = await registerCustomer({ ...registration });
    identifier.value = registration.email.trim().toLowerCase();
    Object.assign(registration, {
      fullName: '',
      phone: '',
      email: '',
      password: '',
      confirmPassword: '',
    });
    password.value = '';
    showRegisterPassword.value = false;
    mode.value = 'login';
    successMessage.value = result.message;
    loading.value = false;
    await nextTick();
    identifierInput.value?.focus();
  } catch (error) {
    errorMessage.value = error.message || 'Không thể đăng ký.';
  } finally {
    loading.value = false;
  }
}

const googleButton = ref(null);
const googleLoading = ref(true);
const googleError = ref('');
const googleClientId =
  import.meta.env.VITE_GOOGLE_CLIENT_ID ||
  '959847691255-af0urgshhkrnconcpplar0g5c3g4qcvc.apps.googleusercontent.com';
let googleDisposed = false;
let googleCleanup;
async function setupGoogle() {
  googleLoading.value = true;
  googleError.value = '';
  try {
    googleCleanup?.();
    googleCleanup = await mountGoogleButton({
      element: googleButton.value,
      clientId: googleClientId,
      onCredential: submitGoogle,
      isActive: () => !googleDisposed && !!googleButton.value,
    });
  } catch (error) {
    if (!googleDisposed)
      googleError.value =
        error.message || 'Không khởi tạo được đăng nhập Google.';
  } finally {
    if (!googleDisposed) googleLoading.value = false;
  }
}
async function submitGoogle(response) {
  if (loading.value || googleDisposed) return;
  loading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    const session = await loginByGoogle({ idToken: response?.credential });
    if (googleDisposed) return;
    password.value = '';
    registration.password = '';
    registration.confirmPassword = '';
    emit('login-success', session);
  } catch (error) {
    if (!googleDisposed)
      errorMessage.value = error.message || 'Không thể đăng nhập Google.';
  } finally {
    if (!googleDisposed) loading.value = false;
  }
}
onMounted(setupGoogle);
onUnmounted(() => {
  googleDisposed = true;
  googleCleanup?.();
});
</script>

<template>
  <section class="login-page">
    <div class="login-layout">
      <aside class="login-intro">
        <p class="intro-label">CARROT BADMINTON</p>
        <h1 v-if="registering">Bắt đầu hành trình.<br />Hẹn nhau trên sân.</h1>
        <h1 v-else>Trở lại sân.<br />Tiếp nối đam mê.</h1>
        <p class="intro-description">
          {{
            registering
              ? 'Tạo tài khoản để chọn giờ chơi, đặt sân và theo dõi những buổi chơi của riêng bạn.'
              : 'Đăng nhập bằng email hoặc số điện thoại để chọn lịch chơi và quản lý đặt sân.'
          }}
        </p>
        <div class="intro-features">
          <div><span>01</span> Chủ động lịch chơi</div>
          <div><span>02</span> Theo dõi booking của bạn</div>
          <div><span>03</span> Quản lý thông tin cá nhân</div>
        </div>
        <a href="#home" class="back-link">← Về trang chủ</a>
        <div class="court-decoration" aria-hidden="true"><span></span></div>
      </aside>
      <div class="login-card">
        <p class="form-label">
          {{
            registering ? 'CHÀO MỪNG THÀNH VIÊN MỚI' : 'CHÀO MỪNG BẠN TRỞ LẠI'
          }}
        </p>
        <div
          class="auth-switch"
          role="group"
          aria-label="Chọn đăng nhập hoặc đăng ký"
        >
          <button
            type="button"
            :class="{ active: !registering }"
            :aria-pressed="!registering"
            :disabled="loading"
            @click="switchMode('login')"
          >
            Đăng nhập
          </button>
          <button
            type="button"
            :class="{ active: registering }"
            :aria-pressed="registering"
            :disabled="loading"
            @click="switchMode('register')"
          >
            Đăng ký
          </button>
        </div>
        <h2>{{ registering ? 'Tạo tài khoản' : 'Đăng nhập' }}</h2>
        <p class="form-description">
          {{
            registering
              ? 'Điền thông tin để tạo tài khoản khách hàng.'
              : 'Sử dụng email hoặc số điện thoại và mật khẩu của bạn.'
          }}
        </p>
        <div class="google-auth-section" :aria-busy="googleLoading || loading">
          <div :class="['google-button-wrap', { 'google-busy': loading }]">
            <div ref="googleButton" class="google-button-host"></div>
          </div>
          <p v-if="googleLoading" class="google-note" role="status">
            Đang tải đăng nhập Google…
          </p>
          <p v-if="googleError" class="login-error" role="alert">
            {{ googleError }}
            <button type="button" :disabled="loading" @click="setupGoogle">
              Thử lại
            </button>
          </p>
          <p class="google-note">
            {{
              registering
                ? 'Google sẽ tạo tài khoản nếu bạn chưa có.'
                : 'Tiếp tục bằng tài khoản Google của bạn.'
            }}
            Bổ sung và xác minh SĐT trước khi đặt sân.
          </p>
          <div class="auth-divider">
            <span></span><small>HOẶC DÙNG EMAIL / SỐ ĐIỆN THOẠI</small
            ><span></span>
          </div>
        </div>
        <p v-if="successMessage" class="auth-success" role="status">
          {{ successMessage }}
        </p>
        <form
          v-if="!registering"
          :aria-busy="loading"
          @submit.prevent="submitLogin"
        >
          <div class="field">
            <label for="login-identifier">Email hoặc số điện thoại</label>
            <input
              id="login-identifier"
              ref="identifierInput"
              v-model="identifier"
              name="username"
              type="text"
              autocomplete="username"
              autocapitalize="none"
              :spellcheck="false"
              placeholder="Email hoặc số điện thoại"
              maxlength="255"
              :disabled="loading"
              required
            />
          </div>
          <div class="field">
            <label for="login-password">Mật khẩu</label>
            <div class="password-field">
              <input
                id="login-password"
                v-model="password"
                name="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="Nhập mật khẩu"
                :disabled="loading"
                required
              />
              <button
                type="button"
                :aria-label="showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'"
                :aria-pressed="showPassword"
                aria-controls="login-password"
                :disabled="loading"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? 'Ẩn' : 'Hiện' }}
              </button>
            </div>
          </div>
          <p v-if="errorMessage" class="login-error" role="alert">
            {{ errorMessage }}
          </p>
          <button class="submit-button" type="submit" :disabled="loading">
            {{ loading ? 'Đang đăng nhập…' : 'Đăng nhập'
            }}<span v-if="!loading" aria-hidden="true">→</span>
          </button>
          <p class="auth-footer">
            Chưa có tài khoản?
            <button
              type="button"
              :disabled="loading"
              @click="switchMode('register')"
            >
              Đăng ký ngay
            </button>
          </p>
        </form>
        <form v-else :aria-busy="loading" @submit.prevent="submitRegister">
          <div class="field">
            <label for="register-name">Họ và tên</label
            ><input
              id="register-name"
              ref="nameInput"
              v-model="registration.fullName"
              name="fullName"
              autocomplete="name"
              placeholder="Nhập họ và tên"
              maxlength="255"
              :disabled="loading"
              required
            />
          </div>
          <div class="field">
            <label for="register-phone">Số điện thoại</label
            ><input
              id="register-phone"
              v-model="registration.phone"
              name="phone"
              type="tel"
              autocomplete="tel"
              placeholder="Ví dụ: 0901234567"
              maxlength="30"
              :disabled="loading"
              required
            />
          </div>
          <div class="field">
            <label for="register-email">Email</label
            ><input
              id="register-email"
              v-model="registration.email"
              name="email"
              type="email"
              autocomplete="email"
              autocapitalize="none"
              :spellcheck="false"
              placeholder="ban@example.com"
              maxlength="255"
              :disabled="loading"
              required
            />
          </div>
          <div class="field">
            <label for="register-password">Mật khẩu</label>
            <div class="password-field">
              <input
                id="register-password"
                v-model="registration.password"
                name="newPassword"
                :type="showRegisterPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="Ít nhất 8 ký tự"
                minlength="8"
                maxlength="72"
                :disabled="loading"
                required
              /><button
                type="button"
                :aria-label="
                  showRegisterPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'
                "
                :aria-pressed="showRegisterPassword"
                aria-controls="register-password register-confirm"
                :disabled="loading"
                @click="showRegisterPassword = !showRegisterPassword"
              >
                {{ showRegisterPassword ? 'Ẩn' : 'Hiện' }}
              </button>
            </div>
            <small class="field-help">Ít nhất 8 ký tự, tối đa 72 byte.</small>
          </div>
          <div class="field">
            <label for="register-confirm">Xác nhận mật khẩu</label
            ><input
              id="register-confirm"
              v-model="registration.confirmPassword"
              name="confirmPassword"
              :type="showRegisterPassword ? 'text' : 'password'"
              autocomplete="new-password"
              placeholder="Nhập lại mật khẩu"
              minlength="8"
              maxlength="72"
              :disabled="loading"
              required
            />
          </div>
          <p v-if="errorMessage" class="login-error" role="alert">
            {{ errorMessage }}
          </p>
          <button class="submit-button" type="submit" :disabled="loading">
            {{ loading ? 'Đang tạo tài khoản…' : 'Tạo tài khoản'
            }}<span v-if="!loading" aria-hidden="true">→</span>
          </button>
          <p class="auth-footer">
            Đã có tài khoản?
            <button
              type="button"
              :disabled="loading"
              @click="switchMode('login')"
            >
              Đăng nhập
            </button>
          </p>
        </form>
        <p class="form-note">
          {{
            registering
              ? 'Sau khi đăng ký, bạn có thể đăng nhập bằng email hoặc số điện thoại đã cung cấp.'
              : 'Thông tin đăng nhập được kiểm tra bởi hệ thống Carrot.'
          }}
        </p>
      </div>
    </div>
  </section>
</template>
<style scoped>
.login-page {
  padding: clamp(24px, 5vw, 64px) 16px;
  background: #ffffff;
  color: #19281f;
}

.login-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  width: min(100%, 1040px);
  margin-inline: auto;
  overflow: hidden;
  border: 1px solid #e2e9e4;
  border-radius: 24px;
  box-shadow: 0 16px 50px rgb(20 50 30 / 6%);
}

.login-intro {
  position: relative;
  overflow: hidden;
  padding: clamp(28px, 4vw, 48px);
  background: #edf5ef;
}

.intro-label,
.form-label {
  margin: 0 0 20px;
  color: #005b35;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.5px;
}

.login-intro h1 {
  position: relative;
  z-index: 1;
  margin: 0;
  font-size: clamp(30px, 3.5vw, 44px);
  line-height: 1.2;
  letter-spacing: -1.5px;
}

.intro-description {
  position: relative;
  z-index: 1;
  margin: 22px 0;
  color: #5b6d61;
  font-size: 14px;
  line-height: 1.8;
}

.intro-features {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 16px;
  margin: 30px 0;
}

.intro-features > div {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
}

.intro-features span {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #ffffff;
  color: #005b35;
  font-size: 10px;
  font-weight: 800;
}

.back-link {
  position: relative;
  z-index: 1;
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  color: #005b35;
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
}

.court-decoration {
  position: absolute;
  right: -45px;
  bottom: -85px;
  width: 210px;
  height: 300px;
  border: 3px solid #005b35;
  opacity: 0.08;
  transform: rotate(-25deg);
  pointer-events: none;
}

.court-decoration::before {
  position: absolute;
  inset: 0 24px;
  border-inline: 2px solid #005b35;
  content: '';
}

.court-decoration::after {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  border-top: 3px solid #005b35;
  content: '';
}

.court-decoration span {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  border-left: 2px solid #005b35;
}

.login-card {
  min-width: 0;
  padding: clamp(28px, 4vw, 48px);
  background: #ffffff;
}

.login-card h2 {
  margin: 0;
  font-size: 30px;
  line-height: 1.3;
  letter-spacing: -0.8px;
}

.form-description {
  margin: 12px 0 28px;
  color: #6b776f;
  font-size: 13px;
  line-height: 1.7;
}

.field {
  margin-bottom: 20px;
}

.field label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 700;
}

.field input {
  width: 100%;
  min-width: 0;
  height: 48px;
  padding: 12px 14px;
  border: 1px solid #dce5de;
  border-radius: 10px;
  background: #ffffff;
  color: #19281f;
  font: inherit;
  font-size: 16px;
}

.field input::placeholder {
  color: #89958d;
  font-size: 13px;
}

.field input:focus-visible {
  outline: 2px solid #005b35;
  outline-offset: 2px;
}

.password-field {
  position: relative;
}

.password-field input {
  padding-right: 68px;
}

.password-field button {
  position: absolute;
  top: 2px;
  right: 3px;
  height: 44px;
  min-width: 56px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #005b35;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.login-error {
  padding: 12px;
  border: 1px solid #f0c6c6;
  border-radius: 10px;
  background: #fff3f3;
  color: #a32d2d;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.submit-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  width: 100%;
  min-height: 48px;
  margin-top: 8px;
  padding: 12px 16px;
  border: 0;
  border-radius: 10px;
  background: #005b35;
  color: #ffffff;
  font-size: 14px;
  font-weight: 750;
  cursor: pointer;
}

.submit-button:hover:not(:disabled) {
  background: #004329;
}

button:disabled,
input:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

button:focus-visible,
a:focus-visible {
  outline: 3px solid #ff8500;
  outline-offset: 3px;
}

.form-note {
  margin: 22px 0 0;
  color: #849087;
  font-size: 11px;
  line-height: 1.7;
  text-align: center;
}

@media (max-width: 720px) {
  .login-layout {
    grid-template-columns: minmax(0, 1fr);
    max-width: 520px;
    border-radius: 18px;
  }

  .login-intro {
    padding: 26px;
  }

  .login-intro h1 {
    font-size: 32px;
  }

  .intro-description {
    margin-bottom: 12px;
  }

  .intro-features {
    display: none;
  }

  .login-card {
    padding: 28px 26px;
  }
}

@media (max-width: 380px) {
  .login-page {
    padding-inline: 12px;
  }

  .login-intro,
  .login-card {
    padding: 22px 18px;
  }

  .login-intro h1 {
    font-size: 28px;
  }
}

.auth-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 5px;
  margin-bottom: 26px;
  padding: 5px;
  border: 1px solid #e0e9e1;
  border-radius: 12px;
  background: #f1f5ef;
}
.auth-switch button {
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #7a887b;
  font: inherit;
  font-size: 13px;
  font-weight: 750;
  cursor: pointer;
}
.auth-switch button.active {
  background: #ffffff;
  color: #005b35;
  box-shadow: 0 2px 8px rgb(20 50 30 / 8%);
}
.auth-success {
  padding: 13px;
  border: 1px solid #cbe0cb;
  border-radius: 10px;
  background: #edf7ec;
  color: #366a37;
  font-size: 13px;
  line-height: 1.7;
}
.auth-footer {
  margin: 21px 0 0;
  text-align: center;
  color: #7b877e;
  font-size: 12px;
}
.auth-footer button {
  border: 0;
  background: none;
  color: #005b35;
  font: inherit;
  font-weight: 750;
  cursor: pointer;
  padding: 6px;
}
.field-help {
  display: block;
  margin-top: 8px;
  font-size: 11px;
  color: #849087;
}
.field input {
  box-sizing: border-box;
}
</style>

<style scoped>
.google-auth-section {
  margin: 22px 0;
}
.google-button-host {
  display: flex;
  justify-content: center;
  min-height: 44px;
  width: 100%;
}
.google-busy {
  pointer-events: none;
  opacity: 0.6;
}
.google-note {
  margin: 12px 0;
  color: #758072;
  font-size: 11px;
  line-height: 1.7;
  text-align: center;
}
.auth-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 22px 0;
}
.auth-divider span {
  height: 1px;
  flex: 1;
  background: #e3e8de;
}
.auth-divider small {
  color: #86907f;
  font-size: 9px;
  letter-spacing: 0.8px;
  text-align: center;
}
.google-auth-section .login-error button {
  border: 0;
  background: transparent;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
</style>

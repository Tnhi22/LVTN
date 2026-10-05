<script setup>
import { ref } from 'vue'
import { loginByPhone } from '../services/authService'

const emit = defineEmits(['login-success'])

const phone = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')

async function submitLogin() {
  if (loading.value) return

  errorMessage.value = ''

  let normalizedPhone = phone.value.trim().replace(/[\s.-]/g, '')

  if (normalizedPhone.startsWith('+84')) {
    normalizedPhone = `0${normalizedPhone.slice(3)}`
  }

  if (!/^0[35789]\d{8}$/.test(normalizedPhone)) {
    errorMessage.value = 'Vui lòng nhập số điện thoại Việt Nam hợp lệ.'
    return
  }

  if (!password.value.trim()) {
    errorMessage.value = 'Vui lòng nhập mật khẩu.'
    return
  }

  loading.value = true

  try {
    const result = await loginByPhone({
      phone: normalizedPhone,
      password: password.value,
    })

    password.value = ''
    emit('login-success', result)
  } catch (error) {
    errorMessage.value = error.message || 'Không thể đăng nhập.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-layout">
      <aside class="login-intro">
        <p class="intro-label">CARROT BADMINTON</p>

        <h1>
          Trở lại sân.<br />
          Tiếp nối đam mê.
        </h1>

        <p class="intro-description">
          Đăng nhập để chọn lịch chơi, quản lý đặt sân và kết nối
          những người cùng đam mê cầu lông.
        </p>

        <div class="intro-features">
          <div><span>01</span> Chủ động lịch chơi</div>
          <div><span>02</span> Theo dõi đăng ký của bạn</div>
          <div><span>03</span> Khám phá các buổi chơi chung</div>
        </div>

        <a href="#home" class="back-link">← Về trang chủ</a>

        <div class="court-decoration" aria-hidden="true">
          <span></span>
        </div>
      </aside>

      <div class="login-card">
        <p class="form-label">CHÀO MỪNG BẠN TRỞ LẠI</p>
        <h2>Đăng nhập</h2>
        <p class="form-description">
          Sử dụng số điện thoại và mật khẩu của bạn.
        </p>

        <form :aria-busy="loading" @submit.prevent="submitLogin">
          <div class="field">
            <label for="login-phone">Số điện thoại</label>
            <input
              id="login-phone"
              v-model="phone"
              name="phone"
              type="tel"
              autocomplete="username"
              placeholder="Nhập số điện thoại"
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

          <button
            class="submit-button"
            type="submit"
            :disabled="loading"
          >
            {{ loading ? 'Đang đăng nhập…' : 'Đăng nhập' }}
            <span v-if="!loading" aria-hidden="true">→</span>
          </button>
        </form>

        <p class="form-note">
          Thông tin đăng nhập được kiểm tra bởi hệ thống Carrot.
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
</style>
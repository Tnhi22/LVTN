<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

const slides = [
  {
    image: '/images/shida.webp',
    title: 'Đặt sân nhanh chóng.',
    subtitle: 'Chơi hết mình.',
    description:
      'Khám phá không gian chơi cầu lông và chọn lịch phù hợp với bạn.',
    position: 'center 35%',
  },
  {
    image: '/images/lindan.jpg',
    title: 'Giữ nhịp đam mê.',
    subtitle: 'Tiến bộ mỗi ngày.',
    description:
      'Dành thời gian cho những buổi tập và những trận cầu cùng bạn bè.',
    position: 'center 30%',
  },
  {
    image: '/images/tienminh.webp',
    title: 'Cùng nhau ra sân.',
    subtitle: 'Kết nối bạn chơi.',
    description:
      'Tận hưởng cầu lông cùng những người có chung đam mê.',
    position: 'center 30%',
  },
]

const currentIndex = ref(0)
const currentSlide = computed(() => slides[currentIndex.value])
const paused = ref(false)
const hovered = ref(false)
const focused = ref(false)
const reducedMotion = ref(false)

let slideTimer
let motionQuery

const courtTypes = [
  {
    number: '01',
    name: 'Premium',
    label: 'TRẢI NGHIỆM CAO CẤP',
    theme: 'premium',
    image: '/images/thuylin.jpg',
    imagePosition: 'center 30%',
    description:
      'Khám phá thông tin sân Premium và chọn khung giờ phù hợp.',
    tags: ['Thông tin sân', 'Lịch trống'],
    link: '#courts',
    action: 'Khám phá sân',
  },
  {
    number: '02',
    name: 'Gold',
    label: 'GIỮ NHỊP TẬP LUYỆN',
    theme: 'gold',
    image: '/images/leechongw.jpg',
    imagePosition: 'center 30%',
    description:
      'Tra cứu sân Gold cho những buổi tập và trận đấu cùng nhóm bạn.',
    tags: ['Tra cứu giá', 'Chọn giờ chơi'],
    link: '#courts',
    action: 'Khám phá sân',
  },
  {
    number: '03',
    name: 'Basic',
    label: 'BẮT ĐẦU ĐAM MÊ',
    theme: 'basic',
    image: '/images/anse.jpg',
    imagePosition: 'center 30%',
    description:
      'Tìm sân Basic theo thời gian và ngân sách của bạn.',
    tags: ['Theo ngày', 'Theo ngân sách'],
    link: '#courts',
    action: 'Khám phá sân',
  },
  {
    number: '04',
    name: 'Đánh vãng lai',
    label: 'KẾT NỐI BẠN CHƠI',
    theme: 'visitor',
    image: '/images/doi.jpg',
    imagePosition: 'center 30%',
    description:
      'Tìm hiểu các buổi chơi chung và lựa chọn theo trình độ.',
    tags: ['Trình độ', 'Buổi chơi chung'],
    link: '#contact',
    action: 'Tìm hiểu thêm',
  },
]

const benefits = [
  {
    icon: 'AI',
    title: 'Tìm sân bằng AI',
    description:
      'Hướng đến tra cứu theo ngày, giờ, sân, giá và thời lượng chơi.',
    state: 'Đang hoàn thiện',
  },
  {
    icon: '01',
    title: 'Đặt sân dễ dàng',
    description:
      'Chọn lịch trống, xem giá và đặt sân theo nhu cầu của bạn.',
    state: '',
  },
  {
    icon: '02',
    title: 'Đăng ký đánh vãng lai',
    description:
      'Khám phá buổi chơi phù hợp với thời gian và trình độ.',
    state: '',
  },
  {
    icon: '03',
    title: 'Thanh toán an toàn',
    description:
      'Xem rõ số tiền và thông tin giao dịch trước khi xác nhận.',
    state: 'Sẽ kết nối',
  },
]

const steps = [
  {
    number: '01',
    title: 'Chọn lịch chơi',
    description: 'Chọn ngày và thời gian bạn muốn ra sân.',
  },
  {
    number: '02',
    title: 'Tìm sân phù hợp',
    description: 'Xem sân trống và giá theo khung giờ.',
  },
  {
    number: '03',
    title: 'Xác nhận đặt sân',
    description: 'Đăng nhập và kiểm tra thông tin trước khi đặt.',
  },
]

function changeSlide(index) {
  currentIndex.value = (index + slides.length) % slides.length
}

function handleMotionChange(event) {
  reducedMotion.value = event.matches
}

function handleFocusOut(event) {
  focused.value = event.currentTarget.contains(event.relatedTarget)
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.value = motionQuery.matches
  motionQuery.addEventListener('change', handleMotionChange)

  slideTimer = window.setInterval(() => {
    if (
      !paused.value &&
      !hovered.value &&
      !focused.value &&
      !reducedMotion.value &&
      !document.hidden
    ) {
      changeSlide(currentIndex.value + 1)
    }
  }, 5500)
})

onUnmounted(() => {
  window.clearInterval(slideTimer)
  motionQuery?.removeEventListener('change', handleMotionChange)
})
</script>

<template>
  <div class="landing-view">
    <!-- Banner -->
    <section class="landing-hero">
      <div class="container hero-layout">
        <div class="hero-copy">
          <p class="landing-eyebrow">
            <span></span>
            CARROT BADMINTON
          </p>

          <h1>
            {{ currentSlide.title }}
            <span>{{ currentSlide.subtitle }}</span>
          </h1>

          <p class="hero-description">
            {{ currentSlide.description }}
          </p>

          <div class="hero-actions">
            <a href="#booking" class="landing-button button-dark">
              Đặt sân ngay
              <span aria-hidden="true">↗</span>
            </a>

            <a href="#courts" class="landing-button button-outline">
              Khám phá sân
            </a>
          </div>

          <div class="hero-note">
            <span class="note-icon" aria-hidden="true">✓</span>
            <p>Chọn lịch phù hợp. Sẵn sàng cho trận cầu tiếp theo.</p>
          </div>
        </div>

        <div
          class="hero-carousel"
          role="region"
          aria-roledescription="băng ảnh"
          aria-label="Hình ảnh cầu lông"
          @mouseenter="hovered = true"
          @mouseleave="hovered = false"
          @focusin="focused = true"
          @focusout="handleFocusOut"
        >
          <div class="hero-image-frame">
            <img
              v-for="(slide, index) in slides"
              :key="slide.image"
              :src="slide.image"
              :alt="`${slide.title} ${slide.subtitle}`"
              :class="{ visible: currentIndex === index }"
              :style="{ objectPosition: slide.position }"
              :aria-hidden="currentIndex !== index"
              :loading="index === 0 ? 'eager' : 'lazy'"
              decoding="async"
            />

            <div class="image-overlay"></div>

            <span class="image-label">ON COURT. IN YOUR ELEMENT.</span>

            <div class="image-caption">
              <span>CẦU LÔNG & KẾT NỐI</span>
              <strong>Mỗi trận cầu, một trải nghiệm.</strong>
            </div>
          </div>

          <div class="carousel-toolbar">
            <span class="slide-counter">
              {{ String(currentIndex + 1).padStart(2, '0') }}
              <span>/ {{ String(slides.length).padStart(2, '0') }}</span>
            </span>

            <div class="slide-dots" aria-label="Chọn ảnh">
              <button
                v-for="(slide, index) in slides"
                :key="slide.image"
                type="button"
                :class="{ active: currentIndex === index }"
                :aria-label="`Xem ảnh ${index + 1}`"
                :aria-pressed="currentIndex === index"
                @click="changeSlide(index)"
              >
                <span></span>
              </button>
            </div>

            <div class="slide-controls">
              <button
                type="button"
                aria-label="Ảnh trước"
                @click="changeSlide(currentIndex - 1)"
              >
                ←
              </button>

              <button
                type="button"
                :aria-label="paused ? 'Bật tự chuyển ảnh' : 'Dừng tự chuyển ảnh'"
                :aria-pressed="paused"
                @click="paused = !paused"
              >
                {{ paused ? '▶' : 'Ⅱ' }}
              </button>

              <button
                type="button"
                aria-label="Ảnh tiếp theo"
                @click="changeSlide(currentIndex + 1)"
              >
                →
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Dải nhận diện -->
    <div class="sport-strip">
      <div class="container sport-strip-inner">
        <span>PLAY WITH PURPOSE</span>
        <span aria-hidden="true">✦</span>
        <span>CONNECT THROUGH SPORT</span>
        <span aria-hidden="true">✦</span>
        <span>GROW EVERY DAY</span>
      </div>
    </div>

    <!-- Tiện ích -->
    <section class="container landing-section">
      <div class="section-heading">
        <div>
          <p class="landing-eyebrow">TRẢI NGHIỆM CỦA BẠN</p>
          <h2>Tập trung vào trận cầu.<br />Phần còn lại, để Carrot hỗ trợ.</h2>
        </div>

        <p class="section-intro">
          Từ tìm lịch chơi đến kết nối bạn chơi, mọi lựa chọn bắt đầu
          từ nhu cầu của bạn.
        </p>
      </div>

      <div class="benefit-grid">
        <article
          v-for="benefit in benefits"
          :key="benefit.title"
          class="benefit-card"
        >
          <div class="benefit-top">
            <span class="benefit-symbol" aria-hidden="true">
              {{ benefit.icon }}
            </span>
            <span v-if="benefit.state" class="feature-state">
              {{ benefit.state }}
            </span>
          </div>

          <h3>{{ benefit.title }}</h3>
          <p>{{ benefit.description }}</p>
        </article>
      </div>
    </section>

    <!-- Các lựa chọn sân -->
    <section class="court-section">
      <div class="container landing-section">
        <div class="section-heading">
          <div>
            <p class="landing-eyebrow">CHỌN CÁCH BẠN RA SÂN</p>
            <h2>Một đam mê. Nhiều lựa chọn.</h2>
          </div>

          <a href="#courts" class="text-link">
            Xem sân cầu lông <span aria-hidden="true">↗</span>
          </a>
        </div>

        <div class="landing-court-grid">
          <article
            v-for="court in courtTypes"
            :key="court.name"
            class="landing-court-card"
            :class="court.theme"
          >
        <div class="court-art">
        <img
            :src="court.image"
            :alt="`Ảnh cầu lông minh họa cho ${court.name}`"
            :style="{ objectPosition: court.imagePosition }"
            loading="lazy"
            decoding="async"
        />

        <div class="court-photo-shade" aria-hidden="true"></div>
        <span class="court-number">{{ court.number }}</span>
        <span class="court-art-name">{{ court.name }}</span>
        </div>

            <div class="court-info">
              <p class="court-category">{{ court.label }}</p>
              <h3>{{ court.name }}</h3>
              <p class="court-description">{{ court.description }}</p>

              <div class="court-tags">
                <span v-for="tag in court.tags" :key="tag">{{ tag }}</span>
              </div>

              <a :href="court.link" class="court-link">
                {{ court.action }}
                <span aria-hidden="true">↗</span>
              </a>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- Hướng dẫn -->
    <section class="container landing-section journey-section">
      <div class="journey-copy">
        <p class="landing-eyebrow">TỪ LỊCH TRỐNG ĐẾN TRẬN CẦU</p>
        <h2>Lên lịch chơi.<br />Giữ nhịp đam mê.</h2>
        <p>
          Chủ động chọn ngày, giờ và sân phù hợp để mỗi buổi chơi
          bắt đầu thật thoải mái.
        </p>

        <a href="#booking" class="landing-button button-dark">
          Chọn lịch của bạn <span aria-hidden="true">↗</span>
        </a>
      </div>

      <div class="journey-steps">
        <article v-for="step in steps" :key="step.number">
          <span class="step-number">{{ step.number }}</span>
          <div>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </div>
        </article>
      </div>
    </section>

    <!-- Lời mời cuối trang -->
    <section class="container landing-cta">
      <div>
        <p class="landing-eyebrow">SEE YOU ON COURT</p>
        <h2>Trận cầu tiếp theo<br />đang chờ bạn.</h2>
        <p>Ra sân, vận động và kết nối những người cùng đam mê.</p>
      </div>

      <a href="#booking" class="landing-button button-dark">
        Đặt sân ngay <span aria-hidden="true">↗</span>
      </a>
    </section>
  </div>
</template>

<style scoped>
.landing-view {
  --ink: #18251e;
  --muted: #68746d;
  --green: #005b35;
  --orange: #ff8500;
  color: var(--ink);
  background: #ffffff;
}

.landing-view a {
  text-decoration: none;
}

.landing-view button,
.landing-view a {
  -webkit-tap-highlight-color: transparent;
}

.landing-view a:focus-visible,
.landing-view button:focus-visible {
  outline: 3px solid var(--orange);
  outline-offset: 4px;
}

.landing-hero {
  padding: 48px 0 30px;
  background: #ffffff;
}

.hero-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  align-items: center;
  gap: clamp(28px, 4vw, 64px);
}

.hero-copy,
.hero-carousel {
  min-width: 0;
}

.landing-eyebrow {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0 0 16px;
  color: var(--green);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 1.6px;
}

.landing-eyebrow > span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--orange);
}

.hero-copy h1 {
  margin: 0;
  font-size: clamp(36px, 4.2vw, 58px);
  line-height: 1.14;
  font-weight: 850;
  letter-spacing: -2px;
}

.hero-copy h1 > span {
  display: block;
  margin-top: 8px;
  color: var(--green);
}

.hero-description {
  max-width: 430px;
  min-height: 52px;
  margin: 22px 0 0;
  color: var(--muted);
  font-size: 15px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 26px;
}

.landing-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  min-height: 48px;
  padding: 12px 20px;
  border: 1px solid transparent;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 750;
  transition: background 160ms ease, transform 160ms ease;
}

.landing-button:hover {
  transform: translateY(-2px);
}

.button-dark {
  color: #ffffff;
  background: var(--ink);
}

.button-dark:hover {
  background: var(--green);
}

.button-outline {
  color: var(--ink);
  border-color: #dce4de;
  background: #ffffff;
}

.button-outline:hover {
  background: #f3f7f4;
}

.hero-note {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 26px;
}

.note-icon {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 50%;
  color: var(--green);
  background: #edf5ef;
  font-size: 12px;
}

.hero-note p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.hero-image-frame {
  position: relative;
  width: 100%;
  height: clamp(300px, 32vw, 410px);
  overflow: hidden;
  border-radius: 22px;
  background: #e9efeb;
}

.hero-image-frame img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  max-width: none;
  object-fit: cover;
  opacity: 0;
  transition: opacity 650ms ease;
}

.hero-image-frame img.visible {
  opacity: 1;
}

.image-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgb(0 0 0 / 12%) 0%,
    transparent 45%,
    rgb(0 0 0 / 65%) 100%
  );
}

.image-label {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 7px 10px;
  border: 1px solid rgb(255 255 255 / 35%);
  border-radius: 6px;
  color: #ffffff;
  background: rgb(0 0 0 / 20%);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.2px;
}

.image-caption {
  position: absolute;
  right: 24px;
  bottom: 24px;
  left: 24px;
  color: #ffffff;
}

.image-caption span {
  display: block;
  margin-bottom: 7px;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.image-caption strong {
  display: block;
  font-size: clamp(18px, 2vw, 25px);
  line-height: 1.3;
}

.carousel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.slide-counter {
  font-size: 12px;
  font-weight: 800;
}

.slide-counter > span {
  margin-left: 4px;
  color: #859087;
  font-weight: 500;
}

.slide-dots,
.slide-controls {
  display: flex;
  align-items: center;
}

.slide-dots button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 36px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.slide-dots button span {
  width: 7px;
  height: 7px;
  border-radius: 20px;
  background: #d8e1db;
  transition: width 160ms ease, background 160ms ease;
}

.slide-dots button.active span {
  width: 23px;
  background: var(--green);
}

.slide-controls {
  gap: 6px;
}

.slide-controls button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid #e0e7e2;
  border-radius: 9px;
  color: var(--ink);
  background: #ffffff;
  cursor: pointer;
}

.slide-controls button:hover {
  background: #edf5ef;
}

.sport-strip {
  border-block: 1px solid #e8eee9;
  background: #f8faf8;
}

.sport-strip-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px 20px;
  padding-block: 18px;
  color: #56635b;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 1.4px;
}

.sport-strip-inner span[aria-hidden] {
  color: var(--orange);
  font-size: 18px;
}

.landing-section {
  padding-block: 64px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 28px;
  margin-bottom: 30px;
}

.section-heading > div {
  min-width: 0;
}

.landing-view h2 {
  margin: 0;
  font-size: clamp(25px, 2.8vw, 36px);
  line-height: 1.25;
  letter-spacing: -1px;
}

.section-intro {
  max-width: 330px;
  margin: 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.8;
}

.benefit-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.benefit-card {
  min-width: 0;
  padding: 22px 18px;
  border: 1px solid #e5ebe6;
  border-radius: 14px;
  background: #ffffff;
}

.benefit-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 22px;
}

.benefit-symbol {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 12px;
  color: var(--green);
  background: #edf5ef;
  font-size: 14px;
  font-weight: 850;
}

.feature-state {
  color: #7e6a43;
  font-size: 9px;
  line-height: 1.4;
  text-align: right;
}

.benefit-card h3 {
  margin: 0 0 10px;
  font-size: 16px;
  line-height: 1.4;
}

.benefit-card p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.court-section {
  background: #f5f7f5;
}

.text-link {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  color: var(--green);
  font-size: 13px;
  font-weight: 750;
}

.landing-court-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.landing-court-card {
  --card-color: #005b35;
  --art-background: #e2efe7;
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e0e7e1;
  border-radius: 16px;
  background: #ffffff;
}

.landing-court-card.gold {
  --card-color: #795719;
  --art-background: #f4ebd8;
}

.landing-court-card.basic {
  --card-color: #285d75;
  --art-background: #e3eef3;
}

.landing-court-card.visitor {
  --card-color: #a4512a;
  --art-background: #f8e8df;
}

.court-art {
  position: relative;
  display: grid;
  place-items: center;
  height: 170px;
  overflow: hidden;
  color: var(--card-color);
  background: var(--art-background);
}

.court-number {
  position: absolute;
  top: 14px;
  left: 16px;
  font-size: 11px;
  font-weight: 800;
}

.court-line-art {
  position: relative;
  width: 110px;
  height: 140px;
  border: 2px solid currentColor;
  opacity: 0.35;
  transform: rotate(-24deg) skewY(8deg);
}

.court-line-art::before {
  position: absolute;
  inset: 0 12px;
  border-inline: 1px solid currentColor;
  content: '';
}

.court-line-art::after {
  position: absolute;
  top: 50%;
  right: -8px;
  left: -8px;
  border-top: 2px solid currentColor;
  content: '';
}

.court-center-line {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  border-left: 1px solid currentColor;
}

.court-service-line {
  position: absolute;
  right: 0;
  left: 0;
  border-top: 1px solid currentColor;
}

.court-service-line.first {
  top: 30%;
}

.court-service-line.second {
  bottom: 30%;
}

.court-art-name {
  position: absolute;
  right: 14px;
  bottom: 12px;
  max-width: calc(100% - 28px);
  font-size: 22px;
  font-weight: 850;
  letter-spacing: -0.8px;
}

.court-info {
  display: flex;
  flex-direction: column;
  padding: 20px 18px;
}

.court-category {
  margin: 0 0 7px;
  color: var(--card-color);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.7px;
}

.court-info h3 {
  margin: 0 0 10px;
  font-size: 22px;
  letter-spacing: -0.5px;
}

.court-description {
  min-height: 66px;
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.8;
}

.court-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 16px;
}

.court-tags span {
  padding: 4px 7px;
  border-radius: 5px;
  color: #637067;
  background: #f3f6f3;
  font-size: 10px;
}

.court-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #e9eee9;
  color: var(--card-color);
  font-size: 12px;
  font-weight: 800;
}

.journey-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 64px;
}

.journey-copy > p:not(.landing-eyebrow) {
  max-width: 400px;
  margin: 18px 0 24px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.8;
}

.journey-steps {
  display: grid;
  gap: 12px;
}

.journey-steps article {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  padding: 20px;
  border: 1px solid #e5ebe6;
  border-radius: 12px;
}

.step-number {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 50%;
  color: var(--green);
  background: #edf5ef;
  font-size: 12px;
  font-weight: 800;
}

.journey-steps h3 {
  margin: 0 0 5px;
  font-size: 15px;
}

.journey-steps p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.7;
}

.landing-cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  margin-bottom: 64px;
  padding: 36px;
  border: 1px solid #efdcc6;
  border-radius: 20px;
  background: #fff4e6;
}

.landing-cta .landing-eyebrow {
  color: #98612a;
}

.landing-cta p:not(.landing-eyebrow) {
  margin: 14px 0 0;
  color: #766b5d;
  font-size: 13px;
  line-height: 1.8;
}

.landing-cta > a {
  flex-shrink: 0;
}

@media (max-width: 1100px) {
  .hero-copy h1 {
    font-size: 43px;
  }

  .hero-layout {
    gap: 28px;
  }

  .benefit-grid,
  .landing-court-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .court-description {
    min-height: 44px;
  }
}

@media (max-width: 800px) {
  .landing-hero {
    padding-top: 32px;
  }

  .hero-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 28px;
  }

  .hero-copy h1 {
    font-size: clamp(36px, 6vw, 48px);
  }

  .hero-description {
    max-width: 540px;
    min-height: 0;
  }

  .hero-image-frame {
    height: clamp(280px, 48vw, 390px);
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .section-intro {
    max-width: 540px;
  }

  .journey-section {
    grid-template-columns: minmax(0, 1fr);
    gap: 28px;
  }

  .sport-strip-inner {
    justify-content: center;
    font-size: 9px;
  }

  .sport-strip-inner span[aria-hidden] {
    display: none;
  }
}

@media (max-width: 540px) {
  .landing-hero {
    padding-top: 28px;
    padding-bottom: 24px;
  }

  .landing-eyebrow {
    margin-bottom: 12px;
    font-size: 9px;
    letter-spacing: 1.2px;
  }

  .hero-copy h1 {
    font-size: clamp(31px, 8vw, 41px);
    letter-spacing: -1.3px;
  }

  .hero-description {
    margin-top: 18px;
    font-size: 13px;
  }

  .hero-actions {
    gap: 8px;
    margin-top: 20px;
  }

  .landing-button {
    min-height: 46px;
    padding: 11px 15px;
    gap: 12px;
    font-size: 12px;
  }

  .hero-note {
    margin-top: 18px;
  }

  .hero-note p {
    font-size: 11px;
  }

  .hero-image-frame {
    height: clamp(240px, 65vw, 320px);
    border-radius: 16px;
  }

  .image-label {
    top: 14px;
    left: 14px;
    font-size: 8px;
  }

  .image-caption {
    right: 18px;
    bottom: 18px;
    left: 18px;
  }

  .carousel-toolbar {
    gap: 6px;
  }

  .sport-strip-inner {
    gap: 8px 16px;
    padding-block: 14px;
    font-size: 8px;
    letter-spacing: 0.8px;
  }

  .landing-section {
    padding-block: 40px;
  }

  .landing-view h2 {
    font-size: 27px;
  }

  .section-heading {
    margin-bottom: 22px;
  }

  .benefit-grid,
  .landing-court-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 12px;
  }

  .benefit-card {
    padding: 20px;
  }

  .benefit-top {
    margin-bottom: 14px;
  }

  .court-art {
    height: 180px;
  }

  .court-description {
    min-height: 0;
  }

  .journey-steps article {
    padding: 16px;
    gap: 12px;
  }

  .landing-cta {
    align-items: flex-start;
    flex-direction: column;
    gap: 22px;
    margin-bottom: 40px;
    padding: 24px;
    border-radius: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-image-frame img,
  .slide-dots button span,
  .landing-button {
    transition: none;
  }

  .landing-button:hover {
    transform: none;
  }
}
</style>
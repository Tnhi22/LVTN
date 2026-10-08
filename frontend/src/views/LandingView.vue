<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { publicRequest, money, time, todayVN } from '../services/customerPortalUtils.js'
import {
  homeSupport,
  homeReviews,
  featuredTubeId,
} from '../services/customerHomeConfig.js'
const quickNeeds = [
  { key: 'Premium', icon: '❄️', title: 'Thích mát lạnh?', detail: 'Khám phá Premium' },
  { key: 'Gold', icon: '💰', title: 'Tiết kiệm & mát vừa?', detail: 'Khám phá Gold' },
  { key: 'Basic', icon: '⚡', title: 'Tiết kiệm tối đa?', detail: 'Khám phá Basic' },
  {
    key: 'DailyVisitor',
    icon: '🏸',
    title: 'Đi một mình tìm cạ?',
    detail: 'Daily Visitor · Giao lưu',
  },
]
const levels = ['TBY', 'TB', 'TB+']
const sessions = ref([])
const sessionLoading = ref(true)
const sessionError = ref('')
const refreshedAt = ref('')
const selectedLevel = ref('TB')
const tube = ref(null)
const selectedType = ref('')
const addTube = ref(false)
const comboOpen = ref(false)
let statusTimer
let statusLoading = false
const visibleSessions = computed(() =>
  sessions.value.filter(
    (s) => String(s.skillLevel).trim().toUpperCase() === selectedLevel.value,
  ),
)
const supportPhone = computed(() => homeSupport.hotline.replace(/[^+\d]/g, ''))
async function refreshStatus() {
  if (statusLoading) return
  statusLoading = true
  try {
    const data = await publicRequest('/api/courts/home-status')
    sessions.value = data.sessions || []
    refreshedAt.value = data.updatedAt?.slice(11, 19) || ''
    sessionError.value = ''
  } catch (e) {
    sessionError.value = 'Chưa tải được số chỗ trống. Bạn có thể xem lại ở trang đặt sân.'
  } finally {
    sessionLoading.value = false
    statusLoading = false
  }
}
async function loadTube() {
  try {
    const data = await publicRequest('/api/courts/catalogue')
    const available = (data.products || []).filter(
      (p) => p.availableQuantityTubes > 0 && p.tubePrice > 0,
    )
    tube.value =
      featuredTubeId == null
        ? available[0] || null
        : available.find((p) => p.id === featuredTubeId) || null
  } catch {
    tube.value = null
  }
}
function chooseCourt(type) {
  selectedType.value = type
  addTube.value = false
  if (type === 'DailyVisitor' || !tube.value) {
    window.location.hash = `booking?type=${type}&date=${todayVN()}`
    return
  }
  comboOpen.value = true
}
function continueBooking() {
  window.location.hash = `booking?type=${selectedType.value}&date=${todayVN()}${addTube.value && tube.value ? `&product=${tube.value.id}` : ''}`
  comboOpen.value = false
}
function closeCombo(event) {
  if (event.key === 'Escape') comboOpen.value = false
}
const slides = [
  {
    image: '/images/shida.webp',
    title: 'Đặt sân nhanh chóng.',
    subtitle: 'Chơi hết mình.',
    description: 'Khám phá không gian chơi cầu lông và chọn lịch phù hợp với bạn.',
    position: 'center 35%',
  },
  {
    image: '/images/lindan.jpg',
    title: 'Giữ nhịp đam mê.',
    subtitle: 'Tiến bộ mỗi ngày.',
    description: 'Dành thời gian cho những buổi tập và những trận cầu cùng bạn bè.',
    position: 'center 30%',
  },
  {
    image: '/images/tienminh.webp',
    title: 'Cùng nhau ra sân.',
    subtitle: 'Kết nối bạn chơi.',
    description: 'Tận hưởng cầu lông cùng những người có chung đam mê.',
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
    description: 'Khám phá thông tin sân Premium và chọn khung giờ phù hợp.',
    tags: ['Thông tin sân', 'Lịch trống'],
    link: '#booking?type=Premium',
    action: 'Chọn lịch sân',
  },
  {
    number: '02',
    name: 'Gold',
    label: 'GIỮ NHỊP TẬP LUYỆN',
    theme: 'gold',
    image: '/images/leechongw.jpg',
    imagePosition: 'center 30%',
    description: 'Tra cứu sân Gold cho những buổi tập và trận đấu cùng nhóm bạn.',
    tags: ['Tra cứu giá', 'Chọn giờ chơi'],
    link: '#booking?type=Gold',
    action: 'Chọn lịch sân',
  },
  {
    number: '03',
    name: 'Basic',
    label: 'BẮT ĐẦU ĐAM MÊ',
    theme: 'basic',
    image: '/images/anse.jpg',
    imagePosition: 'center 30%',
    description: 'Tìm sân Basic theo thời gian và ngân sách của bạn.',
    tags: ['Theo ngày', 'Theo ngân sách'],
    link: '#booking?type=Basic',
    action: 'Chọn lịch sân',
  },
  {
    number: '04',
    name: 'Đánh vãng lai',
    label: 'KẾT NỐI BẠN CHƠI',
    theme: 'visitor',
    image: '/images/doi.jpg',
    imagePosition: 'center 30%',
    description: 'Tìm hiểu các buổi chơi chung và lựa chọn theo trình độ.',
    tags: ['Trình độ', 'Buổi chơi chung'],
    link: '#booking?type=DailyVisitor',
    action: 'Xem buổi chơi',
  },
]
const benefits = [
  {
    icon: '↗',
    title: 'Tra cứu lịch trống',
    description: 'Xem sân phù hợp với ngày, giờ và thời lượng chơi của bạn.',
    state: '',
  },
  {
    icon: '01',
    title: 'Đặt sân dễ dàng',
    description: 'Chọn lịch trống, xem giá và đặt sân theo nhu cầu của bạn.',
    state: '',
  },
  {
    icon: '02',
    title: 'Đăng ký đánh vãng lai',
    description: 'Khám phá buổi chơi phù hợp với thời gian và trình độ.',
    state: '',
  },
  {
    icon: '03',
    title: 'Cầu mua kèm',
    description: 'Chọn cầu ống hoặc cầu lẻ trong bước đặt sân, nhận khi check-in.',
    state: '',
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
function scrollToCourts() {
  document
    .getElementById('court-collections')
    ?.scrollIntoView({
      behavior: reducedMotion.value ? 'auto' : 'smooth',
      block: 'start',
    })
}
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
  refreshStatus()
  loadTube()
  statusTimer = window.setInterval(() => {
    if (!document.hidden) refreshStatus()
  }, 30000)
  window.addEventListener('keydown', closeCombo)
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
  window.clearInterval(statusTimer)
  window.removeEventListener('keydown', closeCombo)
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
            <a
              href="#home"
              @click.prevent="scrollToCourts"
              class="landing-button button-dark"
            >
              Đặt sân ngay
              <span aria-hidden="true">↗</span>
            </a>
            <a
              href="#home"
              @click.prevent="scrollToCourts"
              class="landing-button button-outline"
            >
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
    <section class="container quick-section" aria-label="Chọn sân theo nhu cầu">
      <div class="quick-title">
        <span class="landing-eyebrow">HÔM NAY BẠN MUỐN CHƠI THẾ NÀO?</span>
        <p>Chọn nhu cầu. Tìm lịch phù hợp.</p>
      </div>
      <div class="quick-grid">
        <button
          v-for="need in quickNeeds"
          :key="need.key"
          type="button"
          @click="chooseCourt(need.key)"
        >
          <span class="need-icon" aria-hidden="true">{{ need.icon }}</span
          ><strong>{{ need.title }}</strong
          ><small>{{ need.detail }} ↗</small>
        </button>
      </div>
      <div v-if="tube" class="tube-banner">
        <div>
          <span class="tube-badge">MUA KÈM BUỔI CHƠI</span>
          <h3>{{ tube.name }} · {{ money(tube.tubePrice) }}/ống</h3>
          <p>Chọn thêm khi đặt sân. Nhận cầu lúc check-in.</p>
        </div>
        <button type="button" class="landing-button button-dark" @click="scrollToCourts">
          Chọn sân trước ↗
        </button>
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
          Từ tìm lịch chơi đến kết nối bạn chơi, mọi lựa chọn bắt đầu từ nhu cầu của bạn.
        </p>
      </div>
      <div class="benefit-grid">
        <article v-for="benefit in benefits" :key="benefit.title" class="benefit-card">
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
    <section id="court-collections" class="court-section">
      <div class="container landing-section">
        <div class="section-heading">
          <div>
            <p class="landing-eyebrow">CHỌN CÁCH BẠN RA SÂN</p>
            <h2>Một đam mê. Nhiều lựa chọn.</h2>
          </div>
          <a href="#home" @click.prevent="scrollToCourts" class="text-link">
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
            <a
              :href="court.link"
              @click.prevent="
                chooseCourt(court.name === 'Đánh vãng lai' ? 'DailyVisitor' : court.name)
              "
              class="court-art"
              :aria-label="`Chọn lịch ${court.name}`"
            >
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
            </a>
            <div class="court-info">
              <p class="court-category">{{ court.label }}</p>
              <h3>{{ court.name }}</h3>
              <p class="court-description">{{ court.description }}</p>
              <div class="court-tags">
                <span v-for="tag in court.tags" :key="tag">{{ tag }}</span>
              </div>
              <a
                :href="court.link"
                @click.prevent="
                  chooseCourt(
                    court.name === 'Đánh vãng lai' ? 'DailyVisitor' : court.name,
                  )
                "
                class="court-link"
              >
                {{ court.action }}
                <span aria-hidden="true">↗</span>
              </a>
            </div>
          </article>
        </div>
      </div>
    </section>
    <section class="container landing-section visitor-section">
      <div class="section-heading">
        <div>
          <p class="landing-eyebrow">ĐI MỘT MÌNH. CHƠI CÙNG ĐỒNG ĐỘI.</p>
          <h2>Tìm cạ đúng trình độ.</h2>
        </div>
      </div>
      <div class="level-grid" role="group" aria-label="Chọn trình độ">
        <button
          v-for="(level, i) in levels"
          :key="level"
          type="button"
          :class="[
            'level-card',
            'level-' + i,
            { selected: selectedLevel === level },
          ]"
          :aria-pressed="selectedLevel === level"
          @click="selectedLevel = level"
        >
          <strong>{{ level }}</strong>
        </button>
      </div>
      <p v-if="sessionLoading" role="status">Đang tải các ca giao lưu…</p>
      <p v-else-if="sessionError" role="alert">{{ sessionError }}</p>
      <p v-else-if="!visibleSessions.length" class="visitor-empty">
        Chưa có ca {{ selectedLevel }} sắp diễn ra hôm nay. Xem thêm lịch giao lưu tại
        trang đặt sân.
      </p>
      <a href="#booking?type=DailyVisitor" class="text-link"
        >Xem toàn bộ lịch giao lưu ↗</a
      >
    </section>
    <section v-if="homeReviews.length" class="container landing-section">
      <p class="landing-eyebrow">TRẢI NGHIỆM TỪ KHÁCH HÀNG</p>
      <h2>Những câu chuyện sau trận cầu.</h2>
      <div class="review-grid">
        <blockquote v-for="review in homeReviews" :key="review.name + review.text">
          <p>“{{ review.text }}”</p>
          <cite>{{ review.name }}</cite>
        </blockquote>
      </div>
    </section>
    <aside v-if="supportPhone || homeSupport.zaloUrl" class="container urgent-support">
      <div>
        <strong>Cần ra sân trong 30 phút tới?</strong>
        <p>Liên hệ để nhân viên kiểm tra lịch. Sân được giữ khi có xác nhận.</p>
      </div>
      <a v-if="supportPhone" :href="`tel:${supportPhone}`"
        >Gọi {{ homeSupport.hotline }} ↗</a
      ><a
        v-if="homeSupport.zaloUrl"
        :href="homeSupport.zaloUrl"
        target="_blank"
        rel="noopener noreferrer"
        >Nhắn Zalo ↗</a
      >
    </aside>
    <!-- Hướng dẫn -->
    <section class="container landing-section journey-section">
      <div class="journey-copy">
        <p class="landing-eyebrow">TỪ LỊCH TRỐNG ĐẾN TRẬN CẦU</p>
        <h2>Lên lịch chơi.<br />Giữ nhịp đam mê.</h2>
        <p>
          Chủ động chọn ngày, giờ và sân phù hợp để mỗi buổi chơi bắt đầu thật thoải mái.
        </p>
        <a
          href="#home"
          @click.prevent="scrollToCourts"
          class="landing-button button-dark"
        >
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
      <a href="#home" @click.prevent="scrollToCourts" class="landing-button button-dark">
        Đặt sân ngay <span aria-hidden="true">↗</span>
      </a>
    </section>
    <div v-if="comboOpen" class="combo-overlay" @click.self="comboOpen = false">
      <section
        class="combo-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="combo-title"
      >
        <button
          type="button"
          class="combo-close"
          aria-label="Đóng"
          @click="comboOpen = false"
        >
          ×
        </button>
        <p class="landing-eyebrow">CHUẨN BỊ CHO BUỔI CHƠI</p>
        <h2 id="combo-title">Bạn chọn {{ selectedType }}.</h2>
        <p>Tiếp theo, chọn ngày và khung giờ phù hợp.</p>
        <label class="combo-choice"
          ><input v-model="addTube" type="checkbox" /><span
            ><strong>Thêm 1 ống {{ tube?.name }}</strong
            ><small
              >+ {{ money(tube?.tubePrice || 0) }} ·
              {{ tube?.piecesPerTube }} quả/ống</small
            ></span
          ></label
        >
        <p class="combo-note">
          Đây là lựa chọn gợi ý. Bạn có thể đổi hoặc bỏ sản phẩm ở bước đặt sân; chưa tạo
          đơn hay giữ hàng.
        </p>
        <button type="button" class="landing-button button-dark" @click="continueBooking">
          Tiếp tục đặt sân ↗
        </button>
      </section>
    </div>
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
  transition:
    background 160ms ease,
    transform 160ms ease;
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
  transition:
    width 160ms ease,
    background 160ms ease;
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

/* Diện mạo thể thao, giữ nguyên ảnh của bạn. */
.landing-view .container {
  width: min(1200px, calc(100% - 40px));
  margin-inline: auto;
}
.landing-hero {
  background: radial-gradient(ellipse at 10% 0%, #fff1df, transparent 55%), #fffdfa;
  padding-block: 60px 42px;
}
.hero-copy h1 {
  font-weight: 900;
  letter-spacing: -2.5px;
}
.hero-copy h1 > span {
  color: #dc6810;
}
.hero-image-frame {
  height: clamp(350px, 39vw, 490px);
  border-radius: 28px 28px 70px 28px;
  box-shadow: 0 24px 60px rgb(24 37 30 / 16%);
}
.image-label {
  background: #17251ed9;
  border-color: #ffffff50;
}
.button-dark {
  background: #17251e;
  box-shadow: 0 8px 20px rgb(24 37 30 / 12%);
}
.sport-strip {
  background: #17251e;
  border: 0;
}
.sport-strip-inner {
  color: #fff;
  padding-block: 22px;
}
.court-section {
  background: #f3f3ef;
  scroll-margin-top: 180px;
}
.landing-court-card {
  border: 0;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgb(24 37 30 / 6%);
  transition:
    transform 200ms ease,
    box-shadow 200ms ease;
}
.landing-court-card:hover {
  transform: translateY(-7px);
  box-shadow: 0 18px 36px rgb(24 37 30 / 14%);
}
.court-art {
  display: block;
  height: 260px;
  color: white;
}
.court-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 450ms ease;
}
.landing-court-card:hover .court-art img {
  transform: scale(1.05);
}
.court-photo-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, #00000020 0%, transparent 35%, #08140dcc 100%);
  pointer-events: none;
}
.court-number {
  z-index: 1;
  background: #ffffff24;
  border: 1px solid #ffffff50;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
}
.court-art-name {
  z-index: 1;
  left: 20px;
  bottom: 20px;
  font-size: 29px;
}
.court-info {
  padding: 24px 20px;
}
.court-link {
  min-height: 46px;
}
.benefit-card {
  border-radius: 18px;
  padding: 26px 20px;
}
.landing-cta {
  background: #17251e;
  color: #fff;
  border: 0;
  padding: 44px;
}
.landing-cta .landing-eyebrow {
  color: #ffab53;
}
.landing-cta p:not(.landing-eyebrow) {
  color: #c7d2cb;
}
.landing-cta .button-dark {
  background: #ff8500;
  color: #17251e;
}
@media (max-width: 800px) {
  .hero-image-frame {
    height: 400px;
  }
  .landing-hero {
    padding-top: 32px;
  }
}
@media (max-width: 540px) {
  .landing-view .container {
    width: calc(100% - 32px);
  }
  .hero-image-frame {
    height: 330px;
    border-radius: 20px 20px 45px 20px;
  }
  .court-art {
    height: 280px;
  }
  .landing-cta {
    padding: 26px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .landing-court-card,
  .court-art img {
    transition: none;
  }
  .landing-court-card:hover,
  .landing-court-card:hover .court-art img {
    transform: none;
  }
}

.quick-section {
  padding-block: 32px 40px;
}
.quick-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.quick-title p {
  color: var(--muted);
  font-size: 13px;
}
.quick-grid,
.level-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.quick-grid button {
  padding: 22px;
  text-align: left;
  border: 1px solid #e4e5dd;
  border-radius: 18px;
  background: #fffdfa;
  cursor: pointer;
  transition: transform 160ms ease;
}
.quick-grid button:hover {
  transform: translateY(-4px);
  border-color: #ff8500;
}
.need-icon {
  display: block;
  font-size: 25px;
  margin-bottom: 14px;
}
.quick-grid strong,
.quick-grid small {
  display: block;
}
.quick-grid strong {
  font-size: 14px;
}
.quick-grid small {
  color: #68746d;
  margin-top: 9px;
}
.tube-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 26px;
  margin-top: 20px;
  border-radius: 18px;
  background: #fff0da;
  border: 1px solid #f4d5aa;
}
.tube-badge {
  font-size: 10px;
  font-weight: 800;
  color: #9b4d0c;
  letter-spacing: 1px;
}
.tube-banner h3 {
  margin: 10px 0;
}
.tube-banner p {
  margin: 0;
  color: #766b5d;
  font-size: 13px;
}
.level-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.level-card {
  padding: 25px;
  text-align: left;
  border: 2px solid transparent;
  border-radius: 20px;
  cursor: pointer;
}
.level-0 {
  background: #e8f3ee;
  color: #236149;
}
.level-1 {
  background: #eaf0fb;
  color: #284c86;
}
.level-2 {
  background: #fff0df;
  color: #934a0b;
}
.level-card.selected {
  border-color: currentColor;
}
.level-card small,
.level-card strong,
.level-card span {
  display: block;
}
.level-card small {
  font-size: 10px;
  letter-spacing: 1.4px;
}
.level-card strong {
  font-size: 42px;
  margin: 8px 0;
}
.level-card span {
  font-size: 13px;
}
.visitor-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0;
  color: #68746d;
  font-size: 12px;
}
.visitor-status button {
  border: 1px solid #dfe5df;
  background: #fff;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
}
.session-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.session-grid article {
  padding: 24px;
  border: 1px solid #e2e7e2;
  border-radius: 16px;
}
.session-grid h3 {
  margin: 10px 0;
}
.session-grid p {
  color: #68746d;
}
.session-grid strong {
  display: block;
  color: #005b35;
}
.session-grid strong.full {
  color: #876c55;
}
.session-grid a {
  display: inline-block;
  margin-top: 18px;
  color: #18251e;
  font-weight: 700;
}
.session-level {
  font-size: 11px;
  padding: 5px 10px;
  border-radius: 6px;
  background: #f0f3ef;
}
.visitor-empty {
  padding: 24px;
  background: #f6f7f3;
  border-radius: 14px;
  line-height: 1.8;
}
.urgent-support {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 24px;
  background: #fff0df;
  border-radius: 16px;
  margin-bottom: 24px;
}
.urgent-support div {
  flex: 1;
}
.urgent-support p {
  font-size: 13px;
  color: #766b5d;
}
.urgent-support a {
  color: #18251e;
  font-weight: 700;
}
.review-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}
.review-grid blockquote {
  margin: 24px 0 0;
  padding: 28px;
  background: #f5f7f3;
  border-radius: 18px;
  line-height: 1.8;
}
.combo-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  padding: 24px;
  background: #09110bc9;
  display: grid;
  place-items: center;
  overflow-y: auto;
}
.combo-dialog {
  position: relative;
  width: min(100%, 500px);
  box-sizing: border-box;
  border-radius: 24px;
  padding: 36px;
  background: #fffdfa;
}
.combo-close {
  position: absolute;
  right: 14px;
  top: 10px;
  width: 40px;
  height: 40px;
  border: 0;
  background: transparent;
  font-size: 28px;
  cursor: pointer;
}
.combo-dialog > p {
  color: #68746d;
  line-height: 1.7;
}
.combo-choice {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: #fff0df;
  border-radius: 14px;
  cursor: pointer;
}
.combo-choice input {
  width: 20px;
  height: 20px;
  accent-color: #005b35;
}
.combo-choice small {
  display: block;
  margin-top: 8px;
}
.combo-note {
  font-size: 12px;
}
.combo-dialog .landing-button {
  width: 100%;
}
@media (max-width: 900px) {
  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .session-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 540px) {
  .quick-title,
  .tube-banner,
  .urgent-support {
    align-items: flex-start;
    flex-direction: column;
  }
  .quick-grid {
    gap: 10px;
  }
  .quick-grid button {
    padding: 16px;
  }
  .quick-grid strong {
    font-size: 12px;
  }
  .level-grid {
    gap: 8px;
  }
  .level-card {
    padding: 14px 10px;
  }
  .level-card strong {
    font-size: 30px;
  }
  .level-card span {
    font-size: 11px;
  }
  .session-grid,
  .review-grid {
    grid-template-columns: 1fr;
  }
  .combo-dialog {
    padding: 28px 20px;
  }
}
</style>

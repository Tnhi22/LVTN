<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import CourtImage from '../components/CourtImage.vue'
import {
  buildGroups,
  publicRequest,
  money,
  time,
  todayVN,
} from '../services/customerPortalUtils.js'
import { roomImages } from '../services/customerSiteConfig.js'

const emit = defineEmits(['navigate'])
const loading = ref(false),
  error = ref(''),
  courts = ref([]),
  daily = ref([]),
  sessions = ref([])
let disposed = false
const order = ['PREMIUM', 'GOLD', 'BASIC', 'DAILY_VISITOR']
const stories = {
  PREMIUM: {
    number: '01',
    symbol: '✦',
    tag: 'NÂNG TẦM TRẢI NGHIỆM',
    title: 'Premium',
    subtitle: 'Chơi hết mình. Tận hưởng từng khoảnh khắc.',
    description:
      'Không gian mát lạnh cho những buổi chơi trọn vẹn. Chọn phòng Private để tập trung cùng nhóm bạn, hoặc sân Public để hòa vào nhịp cầu sôi động.',
    benefits: [
      ['❄', 'Không gian máy lạnh', 'Thoải mái duy trì nhịp chơi.'],
      ['◇', 'Private & Public', 'Chọn không gian hợp với nhóm.'],
      ['✧', 'Trải nghiệm cao cấp', 'Cho buổi chơi đáng mong đợi.'],
    ],
  },
  GOLD: {
    number: '02',
    symbol: '◈',
    tag: 'CÂN BẰNG ĐỂ BỨT PHÁ',
    title: 'Gold',
    subtitle: 'Mát vừa đủ. Phong độ vừa tầm.',
    description:
      'Lựa chọn cân bằng giữa sự thoải mái và chi phí. Không gian quạt phun sương phù hợp cho buổi tập đều đặn, rủ đồng đội ra sân và giữ lửa đam mê mỗi tuần.',
    benefits: [
      ['≋', 'Quạt phun sương', 'Không gian chơi mát vừa.'],
      ['◎', 'Cân bằng chi phí', 'Dễ duy trì lịch tập thường xuyên.'],
      ['↗', 'Hợp nhóm đồng đội', 'Cùng nhau giữ nhịp phong độ.'],
    ],
  },
  BASIC: {
    number: '03',
    symbol: 'ϟ',
    tag: 'NĂNG LƯỢNG KHÔNG GIỚI HẠN',
    title: 'Basic',
    subtitle: 'Nhẹ chi phí. Nặng đam mê.',
    description:
      'Ưu tiên trải nghiệm đánh cầu với mức chi phí tiết kiệm. Một lựa chọn thực tế cho người mới, nhóm bạn chơi phong trào và những buổi tập đều đặn sau giờ học, giờ làm.',
    benefits: [
      ['ϟ', 'Chi phí tiết kiệm', 'Dành ngân sách cho nhiều buổi chơi.'],
      ['⌁', 'Tập luyện đều đặn', 'Từ làm quen đến nâng kỹ năng.'],
      ['♧', 'Dễ rủ bạn chơi', 'Lên lịch cùng nhóm của bạn.'],
    ],
  },
  DAILY_VISITOR: {
    number: '04',
    symbol: '✺',
    tag: 'MỘT MÌNH ĐẾN. CÙNG NHAU CHƠI.',
    title: 'Daily Visitor',
    subtitle: 'Tìm đúng trình độ. Gặp đúng đồng đội.',
    description:
      'Không cần chờ đủ nhóm mới được ra sân. Chọn TBY, TB hoặc TB+ để tham gia ca giao lưu phù hợp, kết nối bạn chơi và thử sức qua từng trận đấu.',
    benefits: [
      ['♙', 'Đi một mình vẫn vui', 'Tham gia nhóm giao lưu theo ca.'],
      ['◉', 'Đúng trình độ', 'Ba nhóm TBY · TB · TB+.'],
      ['◷', 'Phí cố định / người', 'Không chia giá theo giờ cao điểm.'],
    ],
  },
}
const groups = computed(() => {
  const rows = buildGroups(courts.value)
  return order.map((key) => ({
    ...(rows.find((g) => g.key === key) || { key, rooms: [], courts: [], image: '' }),
    ...stories[key],
  }))
})
function book(key) {
  emit('navigate', `booking?type=${key}`)
}
function privateRoom(room) {
  return `${room.group || ''} ${room.name || ''}`.toUpperCase().includes('PRIVATE')
}
function roomRows(group) {
  return [...group.rooms].sort(
    (a, b) =>
      Number(privateRoom(b)) - Number(privateRoom(a)) ||
      a.name.localeCompare(b.name, 'vi'),
  )
}
function priceRows(group) {
  return [
    ...new Map(
      group.courts.filter((c) => c.price).map((c) => [c.typeId || c.typeName, c]),
    ).values(),
  ]
}
function priceTitle(name) {
  const value = String(name || '').toUpperCase()
  return value.includes('PRIVATE')
    ? 'Premium Private'
    : value.includes('SHARED') || value.includes('PUBLIC')
      ? 'Premium Public'
      : value.includes('MIST')
        ? 'Gold · Quạt phun sương'
        : value.includes('BASIC')
          ? 'Basic'
          : name
}
const levels = computed(() =>
  ['TBY', 'TB', 'TB+'].map((level, i) => {
    const sessionFees = sessions.value
      .filter(
        (s) =>
          String(s.schedule?.skillLevel || '')
            .trim()
            .toUpperCase() === level,
      )
      .map((s) => s.fixedFee)
    const scheduleFees = daily.value
      .filter(
        (s) =>
          String(s.skillLevel || '')
            .trim()
            .toUpperCase() === level,
      )
      .map((s) => s.fixedFee)
    const fees = (sessionFees.length ? sessionFees : scheduleFees).filter(
      (v) => Number.isSafeInteger(v) && v > 0,
    )
    return {
      level,
      icon: ['◇', '◎', '✦'][i],
      description: [
        'Làm quen nhịp giao lưu',
        'Giữ nhịp, phối hợp đồng đội',
        'Thử sức, nâng phong độ',
      ][i],
      fee: fees.length ? Math.min(...fees) : null,
      max: fees.length ? Math.max(...fees) : null,
    }
  }),
)
function lowest(group) {
  const prices =
    group.key === 'DAILY_VISITOR'
      ? levels.value.map((l) => l.fee)
      : priceRows(group).flatMap((c) => [
          c.price.normalPricePerHour,
          c.price.peakPricePerHour,
        ])
  const values = prices.filter((v) => Number.isSafeInteger(v) && v > 0)
  return values.length ? Math.min(...values) : null
}
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    publicRequest(`/api/courts/schedule?date=${todayVN()}`),
    publicRequest('/api/courts/catalogue'),
    publicRequest(`/api/courts/daily-options?date=${todayVN()}`),
  ])
  if (disposed) return
  if (results[0].status === 'fulfilled') courts.value = results[0].value.courts || []
  if (results[1].status === 'fulfilled')
    daily.value = results[1].value.dailySchedules || []
  if (results[2].status === 'fulfilled') sessions.value = results[2].value.sessions || []
  const warnings = []
  if (results[0].status === 'rejected')
    warnings.push('Chưa tải được giá sân: ' + results[0].reason.message)
  if (results[1].status === 'rejected' && results[2].status === 'rejected')
    warnings.push('Chưa tải được phí Daily Visitor. Vui lòng thử lại.')
  error.value = warnings.join(' ')
  loading.value = false
}
onMounted(load)
onUnmounted(() => {
  disposed = true
})
</script>

<template>
  <main class="pricing-page">
    <header class="pricing-hero">
      <div class="hero-court" aria-hidden="true"><i></i></div>
      <div class="hero-copy">
        <p class="eyebrow"><span></span> CARROT BADMINTON / COURT COLLECTION</p>
        <h1>Bốn chất sân.<br /><em>Một đam mê.</em></h1>
        <p class="hero-description">
          Từ buổi chơi riêng tư đến trận giao lưu đầy năng lượng.<br />Chọn không gian hợp
          bạn, lên sân theo cách của bạn.
        </p>
        <div class="hero-tags">
          <span>✦ Giá theo cấu hình sân</span><span>◷ Giờ thường & cao điểm</span
          ><span>♙ Daily tính theo người</span>
        </div>
      </div>
      <div class="hero-index" aria-hidden="true">
        <strong>04</strong><span>COURT<br />EXPERIENCES</span>
      </div>
    </header>
    <div class="collection-intro">
      <div>
        <span class="eyebrow">FIND YOUR COURT</span>
        <h2>Chọn sân. Chọn cảm hứng.</h2>
      </div>
      <p>
        Mỗi phân khúc một trải nghiệm.<br />Một nút đặt sân, mọi lựa chọn ở bước tiếp
        theo.
      </p>
    </div>
    <div v-if="error" class="message error" role="alert">
      {{ error }} <button :disabled="loading" @click="load">Thử lại</button>
    </div>
    <p v-if="loading" class="message" role="status">
      Đang cập nhật bảng giá từ hệ thống…
    </p>
    <div class="collection-grid" :aria-busy="loading">
      <section
        v-for="(g, index) in groups"
        :key="g.key"
        class="collection-card"
        :class="g.key.toLowerCase()"
        :style="{ '--entry-delay': index * 90 + 'ms' }"
      >
        <div class="card-top">
          <div class="collection-label">
            <span class="collection-symbol" aria-hidden="true">{{ g.symbol }}</span
            ><span>{{ g.tag }}</span>
          </div>
          <button class="book-button" @click="book(g.key)">
            {{ g.key === 'DAILY_VISITOR' ? 'Đặt ca chơi' : 'Đặt sân' }}
            <span aria-hidden="true">↗</span>
          </button>
        </div>
        <div class="title-row">
          <div>
            <span class="collection-number">COLLECTION / {{ g.number }}</span>
            <h2>{{ g.title }}</h2>
          </div>
          <div class="starting-price" v-if="lowest(g)">
            <small>Giá từ</small><strong>{{ money(lowest(g)) }}</strong
            ><span>{{ g.key === 'DAILY_VISITOR' ? '/ người / ca' : '/ sân / giờ' }}</span>
          </div>
        </div>
        <div class="collection-visual">
          <CourtImage
            :src="g.image || (g.key === 'PREMIUM' ? roomImages[g.rooms[0]?.id] : '')"
            :name="g.title"
          />
          <div class="visual-caption">
            <span>{{ g.subtitle }}</span
            ><small>{{
              g.key === 'DAILY_VISITOR'
                ? 'TBY / TB / TB+'
                : `${g.rooms.length} phòng · ${g.courts.length} sân hoạt động`
            }}</small>
          </div>
          <div class="photo-shine" aria-hidden="true"></div>
        </div>
        <p class="collection-description">{{ g.description }}</p>
        <div class="benefits">
          <div v-for="benefit in g.benefits" :key="benefit[1]">
            <span aria-hidden="true">{{ benefit[0] }}</span
            ><strong>{{ benefit[1] }}</strong
            ><small>{{ benefit[2] }}</small>
          </div>
        </div>
        <div class="section-label">
          <span>{{
            g.key === 'DAILY_VISITOR' ? '03 TRÌNH ĐỘ GIAO LƯU' : 'KHÔNG GIAN & BẢNG GIÁ'
          }}</span
          ><i></i>
        </div>
        <template v-if="g.key !== 'DAILY_VISITOR'">
          <div v-if="g.key === 'PREMIUM' && g.rooms.length" class="room-prices">
            <article v-for="r in roomRows(g)" :key="r.id" class="price-row">
              <CourtImage :src="roomImages[r.id]" :name="r.name" />
              <div class="room-info">
                <small>{{
                  privateRoom(r) ? 'PRIVATE / RIÊNG TƯ' : 'PUBLIC / CHƠI CHUNG NHÓM'
                }}</small>
                <h3>{{ r.name }}</h3>
                <p>
                  {{ r.courts.length }} sân · {{ r.courts.map((c) => c.name).join(', ') }}
                </p>
                <div v-if="r.price" class="price-pair">
                  <div>
                    <small
                      >Thường {{ time(r.price.openingTime) }}–{{
                        time(r.price.peakStartTime)
                      }}</small
                    ><strong
                      >{{ money(r.price.normalPricePerHour) }}<em>/ giờ</em></strong
                    >
                  </div>
                  <div>
                    <small
                      >Cao điểm {{ time(r.price.peakStartTime) }}–{{
                        time(r.price.closingTime)
                      }}</small
                    ><strong>{{ money(r.price.peakPricePerHour) }}<em>/ giờ</em></strong>
                  </div>
                </div>
                <p v-else class="unavailable">Chưa có giá đang hoạt động.</p>
              </div>
            </article>
          </div>
          <div v-else class="type-prices">
            <article v-for="c in priceRows(g)" :key="c.typeId || c.typeName">
              <div class="price-type">
                <span aria-hidden="true">{{ g.symbol }}</span>
                <h3>{{ priceTitle(c.typeName) }}</h3>
              </div>
              <div class="price-pair">
                <div>
                  <small
                    >Giờ thường · {{ time(c.price.openingTime) }}–{{
                      time(c.price.peakStartTime)
                    }}</small
                  ><strong>{{ money(c.price.normalPricePerHour) }}<em>/ giờ</em></strong>
                </div>
                <div>
                  <small
                    >Cao điểm · {{ time(c.price.peakStartTime) }}–{{
                      time(c.price.closingTime)
                    }}</small
                  ><strong>{{ money(c.price.peakPricePerHour) }}<em>/ giờ</em></strong>
                </div>
              </div>
            </article>
            <p v-if="!priceRows(g).length" class="unavailable">
              {{ loading ? 'Đang tải bảng giá…' : 'Chưa có bảng giá đang hoạt động.' }}
            </p>
            <div class="room-pills">
              <span v-for="r in g.rooms" :key="r.id"
                >{{ r.name }} <strong>{{ r.courts.length }} sân</strong></span
              >
            </div>
          </div>
        </template>
        <div v-else class="daily-levels">
          <article v-for="l in levels" :key="l.level">
            <span aria-hidden="true">{{ l.icon }}</span>
            <h3>{{ l.level }}</h3>
            <p>{{ l.description }}</p>
            <strong>{{
              l.fee ? (l.max !== l.fee ? 'Từ ' : '') + money(l.fee) : 'Đang cập nhật'
            }}</strong
            ><small>/ người / ca</small>
          </article>
          <p class="daily-note">
            ◷ Ca có giờ cố định · Chọn ca tại trang đặt sân · Ca đầy có thể vào danh sách
            chờ.
          </p>
        </div>
        <footer class="card-footer">
          <span>{{
            g.key === 'DAILY_VISITOR'
              ? 'Phí cố định theo trình độ, không chia giờ cao điểm.'
              : 'Giá tính theo giờ chơi và bảng giá đang hoạt động.'
          }}</span
          ><span aria-hidden="true">{{ g.number }} / 04</span>
        </footer>
      </section>
    </div>
    <aside class="pricing-note">
      <span aria-hidden="true">✦</span>
      <div>
        <strong>Chọn đúng sân, buổi chơi thêm trọn vẹn.</strong>
        <p>
          Bấm nút ở đầu mỗi phân khúc để sang trang đặt sân, xem lịch trống và chọn phòng
          / sân. Giá cuối cùng được hệ thống xác nhận theo lựa chọn của bạn.
        </p>
      </div>
    </aside>
  </main>
</template>

<style scoped>
.pricing-page {
  max-width: 1400px;
  margin: auto;
  padding: 32px 28px 80px;
  color: #242620;
  font-family: inherit;
}
.pricing-page * {
  box-sizing: border-box;
}
.pricing-hero {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 30px;
  padding: 55px 55px 45px;
  border-radius: 26px;
  background: #171d19;
  color: #fff;
}
.pricing-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: radial-gradient(ellipse at 85% 10%, #f5a73b22, transparent 55%);
}
.hero-copy {
  position: relative;
  z-index: 1;
}
.eyebrow {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 2px;
}
.pricing-hero .eyebrow {
  color: #d6bc91;
  display: flex;
  gap: 10px;
  align-items: center;
}
.eyebrow > span {
  height: 7px;
  width: 7px;
  background: #f4a64b;
  border-radius: 50%;
  box-shadow: 0 0 0 5px #f4a64b15;
}
.pricing-hero h1 {
  font-size: clamp(38px, 5.3vw, 70px);
  letter-spacing: -3px;
  line-height: 1.05;
  margin: 25px 0;
}
.pricing-hero h1 em {
  color: #f3b35a;
  font-style: normal;
}
.hero-description {
  font-size: 14px;
  line-height: 1.9;
  color: #b9c0b7;
}
.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 28px;
}
.hero-tags span {
  font-size: 10px;
  padding: 9px 12px;
  border: 1px solid #ffffff20;
  border-radius: 30px;
  color: #d9ddd3;
}
.hero-index {
  display: flex;
  gap: 15px;
  align-items: center;
  position: relative;
  z-index: 1;
}
.hero-index strong {
  font-size: clamp(90px, 12vw, 165px);
  line-height: 1;
  letter-spacing: -10px;
  color: #f8dcb923;
  font-weight: 900;
}
.hero-index > span {
  font-size: 10px;
  line-height: 1.8;
  letter-spacing: 3px;
  writing-mode: vertical-rl;
  color: #cebd9f;
}
.hero-court {
  position: absolute;
  right: -35px;
  top: -80px;
  width: 380px;
  height: 560px;
  border: 2px solid #eed9b510;
  transform: rotate(25deg);
  pointer-events: none;
}
.hero-court::before {
  content: '';
  position: absolute;
  inset: 0 40px;
  border-inline: 2px solid #eed9b510;
}
.hero-court::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  border-top: 2px solid #eed9b510;
}
.hero-court i {
  position: absolute;
  inset: 0 50%;
  border-left: 2px solid #eed9b510;
}
.collection-intro {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
  margin: 44px 0 26px;
}
.collection-intro .eyebrow {
  color: #98723d;
}
.collection-intro h2 {
  font-size: clamp(25px, 3vw, 34px);
  letter-spacing: -1px;
  margin: 12px 0 0;
}
.collection-intro > p {
  font-size: 12px;
  line-height: 1.8;
  color: #89897b;
  margin: 0;
}
.collection-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 26px;
  align-items: start;
}
.collection-card {
  --accent: #986329;
  --ink: #302d26;
  --muted: #847c6e;
  --line: #e9ddc8;
  --surface: #fbf4e8;
  --tint: #f4e9d5;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 22px;
  overflow: hidden;
  padding: 25px;
  background: var(--surface);
  color: var(--ink);
  position: relative;
  transition:
    transform 0.35s ease,
    box-shadow 0.35s ease,
    border-color 0.35s ease;
  animation: card-in 0.65s both;
  animation-delay: var(--entry-delay);
}
.collection-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 24px 55px #32200a12;
  border-color: var(--accent);
}
.premium {
  --accent: #f5be6b;
  --ink: #fff5e7;
  --muted: #baae9a;
  --line: #ffffff18;
  --surface: #20231f;
  --tint: #ffffff06;
}
.basic {
  --accent: #b85c31;
  --ink: #39271f;
  --muted: #977f6f;
  --line: #f1dccd;
  --surface: #fff8f2;
  --tint: #faede2;
}
.daily_visitor {
  --accent: #70609c;
  --ink: #302d42;
  --muted: #878195;
  --line: #e1dbed;
  --surface: #f8f5fc;
  --tint: #eee8f6;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}
.collection-label {
  display: flex;
  gap: 9px;
  align-items: center;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--accent);
}
.collection-symbol {
  font-size: 25px;
  line-height: 1;
}
.book-button {
  flex-shrink: 0;
  border: 1px solid var(--accent);
  color: var(--surface);
  background: var(--accent);
  border-radius: 30px;
  min-height: 41px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 15px;
  font: inherit;
  font-size: 11px;
  font-weight: 800;
  cursor: pointer;
  transition:
    box-shadow 0.25s,
    transform 0.25s;
}
.premium .book-button {
  color: #29251d;
}
.book-button > span {
  font-size: 18px;
  transition: transform 0.25s;
}
.book-button:hover {
  box-shadow: 0 5px 20px #7c50102a;
  transform: translateY(-2px);
}
.book-button:hover > span {
  transform: translate(2px, -2px);
}
.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 26px 0 20px;
}
.collection-number {
  font-size: 8px;
  letter-spacing: 2px;
  color: var(--muted);
  font-weight: 700;
}
.title-row h2 {
  font-size: clamp(32px, 3.2vw, 48px);
  line-height: 1.1;
  letter-spacing: -1.8px;
  margin: 7px 0;
}
.starting-price {
  display: grid;
  text-align: right;
  gap: 4px;
  flex-shrink: 0;
}
.starting-price small,
.starting-price > span {
  font-size: 9px;
  color: var(--muted);
}
.starting-price strong {
  font-size: 20px;
  color: var(--accent);
  letter-spacing: -0.6px;
}
.collection-visual {
  position: relative;
  overflow: hidden;
  border-radius: 13px;
  background: var(--tint);
}
.collection-visual :deep(.court-visual) {
  aspect-ratio: 2.1;
  border-radius: 0;
  transition: transform 0.7s;
  background: var(--tint);
  color: var(--accent);
}
.collection-visual :deep(img) {
  transition: transform 0.8s;
  object-fit: cover;
}
.collection-card:hover .collection-visual :deep(img) {
  transform: scale(1.06);
}
.collection-visual :deep(.court-placeholder) {
  padding-bottom: 42px;
}
.visual-caption {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: end;
  padding: 36px 16px 15px;
  background: linear-gradient(transparent, #161611bd);
  color: white;
  pointer-events: none;
}
.visual-caption > span {
  font-size: 13px;
  max-width: 65%;
  font-weight: 750;
  line-height: 1.4;
}
.visual-caption small {
  font-size: 8px;
  line-height: 1.5;
  max-width: 35%;
  text-align: right;
}
.photo-shine {
  position: absolute;
  inset: -100%;
  pointer-events: none;
  background: linear-gradient(110deg, transparent 42%, #ffffff16 50%, transparent 58%);
  transform: translateX(-55%);
  transition: transform 0.85s;
}
.collection-card:hover .photo-shine {
  transform: translateX(55%);
}
.collection-description {
  font-size: 12px;
  line-height: 1.9;
  color: var(--muted);
  min-height: 68px;
  margin: 20px 0;
}
.benefits {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
  margin-bottom: 25px;
}
.benefits > div {
  padding: 13px 9px;
  background: var(--tint);
  border: 1px solid var(--line);
  border-radius: 11px;
  display: grid;
  gap: 8px;
  transition: transform 0.25s;
}
.benefits > div:hover {
  transform: translateY(-3px);
}
.benefits > div > span {
  font-size: 23px;
  color: var(--accent);
}
.benefits strong {
  font-size: 10px;
  line-height: 1.5;
}
.benefits small {
  font-size: 9px;
  line-height: 1.7;
  color: var(--muted);
}
.section-label {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 15px;
}
.section-label > span {
  font-size: 8px;
  letter-spacing: 1.5px;
  font-weight: 800;
  color: var(--accent);
  white-space: nowrap;
}
.section-label i {
  height: 1px;
  background: var(--line);
  flex: 1;
}
.room-prices,
.type-prices {
  display: grid;
  gap: 12px;
}
.price-row {
  display: grid;
  grid-template-columns: 85px minmax(0, 1fr);
  gap: 13px;
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--tint);
  transition: border-color 0.3s;
}
.price-row:hover {
  border-color: var(--accent);
}
.price-row > :deep(.court-visual) {
  height: 100%;
  min-height: 110px;
  aspect-ratio: auto;
  border-radius: 8px;
  background: var(--tint);
  color: var(--accent);
}
.price-row :deep(.court-placeholder small),
.price-row :deep(.court-placeholder span) {
  display: none;
}
.price-row :deep(.court-placeholder) {
  padding: 8px;
}
.room-info > small {
  font-size: 7px;
  letter-spacing: 1px;
  color: var(--accent);
}
.room-info h3 {
  margin: 6px 0;
  font-size: 15px;
}
.room-info > p {
  font-size: 9px;
  color: var(--muted);
  line-height: 1.6;
  margin: 5px 0 10px;
  overflow-wrap: anywhere;
}
.price-pair {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.price-pair > div {
  display: grid;
  gap: 6px;
}
.price-pair small {
  font-size: 8px;
  color: var(--muted);
  line-height: 1.6;
}
.price-pair strong {
  font-size: 16px;
  white-space: nowrap;
  letter-spacing: -0.5px;
}
.price-pair em {
  font-size: 8px;
  font-weight: 400;
  font-style: normal;
  color: var(--muted);
  margin-left: 3px;
}
.type-prices > article {
  padding: 18px;
  border-radius: 12px;
  background: var(--tint);
  border: 1px solid var(--line);
}
.price-type {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 18px;
}
.price-type > span {
  font-size: 19px;
  color: var(--accent);
}
.price-type h3 {
  font-size: 15px;
  margin: 0;
}
.type-prices .price-pair strong {
  font-size: 22px;
}
.room-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.room-pills > span {
  font-size: 9px;
  padding: 10px 12px;
  border-radius: 9px;
  border: 1px solid var(--line);
  color: var(--muted);
}
.room-pills strong {
  color: var(--ink);
  margin-left: 8px;
}
.daily-levels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.daily-levels > article {
  text-align: center;
  padding: 19px 9px;
  background: var(--tint);
  border: 1px solid var(--line);
  border-radius: 12px;
  transition:
    transform 0.3s,
    box-shadow 0.3s;
}
.daily-levels > article:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 20px #70609c10;
}
.daily-levels article > span {
  font-size: 24px;
  color: var(--accent);
}
.daily-levels h3 {
  font-size: 23px;
  margin: 9px 0;
  letter-spacing: -1px;
}
.daily-levels article p {
  min-height: 32px;
  font-size: 9px;
  line-height: 1.7;
  color: var(--muted);
}
.daily-levels strong {
  font-size: 16px;
  display: block;
  color: var(--accent);
}
.daily-levels article > small {
  font-size: 9px;
  display: block;
  color: var(--muted);
  margin-top: 6px;
}
.daily-note {
  grid-column: 1/-1;
  font-size: 10px;
  line-height: 1.8;
  color: var(--muted);
  padding: 12px;
  background: var(--tint);
  border-radius: 9px;
  margin: 0;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  align-items: center;
  border-top: 1px solid var(--line);
  padding-top: 17px;
  margin-top: 22px;
  color: var(--muted);
  font-size: 9px;
  line-height: 1.7;
}
.card-footer > span:last-child {
  white-space: nowrap;
  color: var(--accent);
  letter-spacing: 1px;
}
.pricing-note {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 25px 28px;
  border: 1px solid #e6dfd3;
  border-radius: 15px;
  background: #faf7f1;
  margin-top: 28px;
}
.pricing-note > span {
  font-size: 35px;
  color: #b7843b;
}
.pricing-note strong {
  font-size: 14px;
}
.pricing-note p {
  font-size: 11px;
  color: #8b8374;
  line-height: 1.8;
  margin: 8px 0 0;
}
.message {
  padding: 15px 18px;
  border-radius: 12px;
  background: #faf4e8;
  font-size: 12px;
  color: #8b6d3e;
  margin-bottom: 20px;
}
.message.error {
  background: #fff0e7;
  color: #a15232;
}
.message button {
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-decoration: underline;
  cursor: pointer;
}
button:focus-visible {
  outline: 3px solid #ff9b37;
  outline-offset: 4px;
}
@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(22px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (max-width: 1050px) {
  .collection-card {
    padding: 20px;
  }
  .title-row h2 {
    font-size: 35px;
  }
  .starting-price strong {
    font-size: 17px;
  }
  .price-row {
    grid-template-columns: 65px minmax(0, 1fr);
  }
  .price-pair strong {
    font-size: 14px;
  }
  .price-pair em {
    display: block;
    margin-left: 0;
  }
  .hero-index {
    display: none;
  }
  .benefits {
    gap: 6px;
  }
  .collection-label {
    font-size: 8px;
    letter-spacing: 0.5px;
  }
}
@media (max-width: 820px) {
  .collection-grid {
    grid-template-columns: 1fr;
  }
  .pricing-page {
    padding: 24px 18px 55px;
  }
  .pricing-hero {
    padding: 38px 28px;
  }
  .collection-intro > p {
    display: none;
  }
  .collection-card {
    padding: 24px;
  }
  .title-row h2 {
    font-size: 43px;
  }
  .price-row {
    grid-template-columns: 90px minmax(0, 1fr);
  }
  .price-pair strong {
    font-size: 19px;
  }
  .price-pair em {
    display: inline;
    margin-left: 4px;
  }
  .collection-description {
    min-height: 0;
  }
  .starting-price strong {
    font-size: 21px;
  }
}
@media (max-width: 430px) {
  .pricing-page {
    padding-inline: 12px;
  }
  .pricing-hero {
    padding: 30px 22px;
    border-radius: 19px;
  }
  .pricing-hero h1 {
    letter-spacing: -2px;
  }
  .hero-description {
    font-size: 12px;
  }
  .hero-tags {
    gap: 7px;
  }
  .hero-tags span {
    font-size: 8px;
  }
  .collection-card {
    padding: 18px;
    border-radius: 17px;
  }
  .title-row h2 {
    font-size: 33px;
  }
  .starting-price strong {
    font-size: 17px;
  }
  .collection-label {
    max-width: 55%;
    font-size: 7px;
  }
  .book-button {
    padding: 9px 12px;
    font-size: 10px;
    gap: 9px;
  }
  .benefits strong {
    font-size: 9px;
  }
  .benefits small {
    font-size: 8px;
  }
  .price-row {
    grid-template-columns: 58px minmax(0, 1fr);
    gap: 10px;
    padding: 10px;
  }
  .price-pair strong {
    font-size: 14px;
  }
  .price-pair em {
    display: block;
    margin: 0;
  }
  .room-info h3 {
    font-size: 13px;
  }
  .daily-levels {
    gap: 6px;
  }
  .daily-levels strong {
    font-size: 13px;
  }
  .daily-levels > article {
    padding-inline: 5px;
  }
  .pricing-note {
    padding: 18px;
    gap: 12px;
  }
  .visual-caption > span {
    font-size: 11px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .pricing-page *,
  .pricing-page *::before,
  .pricing-page *::after {
    animation: none !important;
    transition: none !important;
  }
  .collection-card:hover,
  .benefits > div:hover,
  .daily-levels > article:hover,
  .book-button:hover {
    transform: none;
  }
}
</style>

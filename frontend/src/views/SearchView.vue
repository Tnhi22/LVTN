<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import CourtImage from '../components/CourtImage.vue'
import ProductPhoto from '../components/ProductPhoto.vue'
import {
  buildGroups,
  groupKey,
  publicRequest,
  money,
  time,
  todayVN,
} from '../services/customerPortalUtils.js'
import { siteContact, roomImages } from '../services/customerSiteConfig.js'
import { articles } from '../services/customerArticles.js'
const props = defineProps({
  query: { type: String, default: '' },
  scope: { type: String, default: 'all' },
  searchDate: String,
  articleId: String,
})
const page = 'search'
const emit = defineEmits(['navigate'])
const courts = ref([]),
  products = ref([]),
  daily = ref([]),
  loading = ref(false),
  error = ref('')
const localSearch = ref('')
const selectedBrand = ref('')
const maxDate = ref('')
const query = computed(() =>
  ('search' === 'search' ? props.query : localSearch.value).trim().toLowerCase(),
)
const groups = computed(() => buildGroups(courts.value))
const filteredGroups = computed(() =>
  groups.value.filter((g) =>
    `${g.name} ${g.description}`.toLowerCase().includes(query.value),
  ),
)
const filteredProducts = computed(() =>
  products.value.filter(
    (p) =>
      `${p.name} ${p.brand} ${p.detail || ''}`.toLowerCase().includes(query.value) &&
      (!selectedBrand.value || p.brand === selectedBrand.value),
  ),
)
const brands = computed(() => [...new Set(products.value.map((p) => p.brand))])
const availableMatches = computed(() => {
  const day = props.searchDate || todayVN(),
    rows = []
  if (maxDate.value && day > maxDate.value) return rows
  const parse = (value) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5))
  const format = (value) =>
    `${String(Math.floor(value / 60)).padStart(2, '0')}:${value % 60 ? '30' : '00'}`
  for (const c of courts.value) {
    if (
      !c.active ||
      !c.price ||
      groupKey(c) === 'DAILY_VISITOR' ||
      `${c.name} ${c.roomName} ${c.typeName}`.toLowerCase().includes(query.value) ===
        false
    )
      continue
    for (
      let m = parse(c.price.openingTime);
      m + 60 <= parse(c.price.closingTime);
      m += 30
    ) {
      const start = new Date(`${day}T${format(m)}:00+07:00`).getTime(),
        end = start + 3600000
      if (
        start <= Date.now() ||
        c.busy.some(
          (b) =>
            start < (b.end ? new Date(`${b.end}+07:00`).getTime() : Infinity) &&
            end > new Date(`${b.start}+07:00`).getTime(),
        )
      )
        continue
      rows.push({ court: c, start: format(m), end: format(m + 60), day })
      break
    }
  }
  return rows.slice(0, 12)
})
const article = computed(() => articles.find((a) => a.id === props.articleId))
const pageTitles = {
  products: ['GEAR UP', 'Sẵn sàng trước khi ra sân.'],
  pricing: ['COURT COLLECTIONS', 'Bảng giá. Chọn chất chơi.'],
  news: ['READ. LEARN. PLAY.', 'Câu chuyện ngoài sân.'],
  contact: ['LET’S CONNECT', 'Hẹn gặp bạn trên sân.'],
  search: ['FIND YOUR GAME', 'Tìm nhanh. Chơi ngay.'],
}
const heading = computed(() => pageTitles['search'] || pageTitles.search)
const mapUrl = computed(() =>
  siteContact.mapQuery
    ? `https://www.google.com/maps?q=${encodeURIComponent(siteContact.mapQuery)}&output=embed`
    : '',
)
let sequence = 0
async function load() {
  const own = ++sequence
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    publicRequest(`/api/courts/schedule?date=${props.searchDate || todayVN()}`),
    publicRequest('/api/courts/catalogue'),
  ])
  if (own !== sequence) return
  if (results[0].status === 'fulfilled') {
    courts.value = results[0].value.courts
    maxDate.value = results[0].value.maxBookingDate
  }
  if (results[1].status === 'fulfilled') {
    products.value = results[1].value.products
    daily.value = results[1].value.dailySchedules
  }
  error.value = results
    .filter((r) => r.status === 'rejected')
    .map((r) => r.reason.message)
    .join(' ')
  loading.value = false
}
function book(key) {
  emit(
    'navigate',
    `booking?type=${key}${props.searchDate ? `&date=${encodeURIComponent(props.searchDate)}` : ''}`,
  )
}
function privateRoom(room) {
  return `${room.group || ''} ${room.name}`.toUpperCase().includes('PRIVATE')
}
function priceRows(g) {
  return [
    ...new Map(
      g.courts.filter((c) => c.price).map((c) => [c.typeId || c.typeName, c]),
    ).values(),
  ]
}
watch(() => props.searchDate, load)
watch(
  () => 'search',
  () => {
    localSearch.value = ''
    selectedBrand.value = ''
  },
)
onMounted(load)
</script>
<template>
  <div class="portal-page">
    <header class="portal-heading">
      <span>{{ heading[0] }}</span>
      <h1>{{ heading[1] }}</h1>
      <p v-if="page === 'products'">
        Cầu lông đang bán tại Carrot. Chọn mua kèm khi đặt sân và nhận hàng lúc check-in.
      </p>
      <p v-else-if="page === 'pricing'">
        Chọn phân khúc và khung giờ phù hợp với buổi chơi của bạn.
      </p>
      <p v-else-if="page === 'search'">
        Kết quả cho “{{ query || 'tất cả' }}”{{ searchDate ? ` · ${searchDate}` : '' }}.
      </p>
      <p v-else-if="page === 'news'">
        Cẩm nang chọn thiết bị và đường dẫn theo dõi giải đấu từ nguồn chính thức.
      </p>
      <p v-else>Carrot kết nối người chơi với lịch sân phù hợp.</p>
    </header>
    <div
      v-if="error && ['products', 'pricing', 'search'].includes(page)"
      class="portal-error"
      role="alert"
    >
      {{ error }} <button @click="load">Thử lại</button>
    </div>
    <div
      v-if="loading && ['products', 'pricing', 'search'].includes(page)"
      class="portal-empty"
    >
      Đang tải dữ liệu…
    </div>
    <section
      v-if="page === 'search' && ['all', 'booking'].includes(scope)"
      class="search-availability"
    >
      <h2>Gợi ý giờ trống · buổi chơi 60 phút</h2>
      <p v-if="searchDate && maxDate && searchDate > maxDate" class="portal-error">
        Ngày vượt hạn đặt sân {{ maxDate }}. Chọn lại ngày ở thanh tìm kiếm.
      </p>
      <div v-else class="available-grid">
        <article v-for="item in availableMatches" :key="item.court.id">
          <small>{{ item.court.typeName }}</small>
          <h3>{{ item.court.name }}</h3>
          <p>{{ item.day }} · {{ item.start }} – {{ item.end }}</p>
          <button
            class="portal-button dark"
            @click="
              emit(
                'navigate',
                `booking?type=${groupKey(item.court)}&date=${item.day}&start=${item.start}&end=${item.end}&court=${item.court.id}`,
              )
            "
          >
            Chọn khung giờ ↗
          </button>
        </article>
        <p v-if="!loading && !availableMatches.length">
          Không tìm thấy sân trống phù hợp trong ngày này.
        </p>
      </div>
    </section>
    <template
      v-if="
        page === 'products' || (page === 'search' && ['all', 'products'].includes(scope))
      "
    >
      <div class="portal-toolbar">
        <input
          v-if="page === 'products'"
          v-model="localSearch"
          placeholder="Tìm sản phẩm, thương hiệu…"
          aria-label="Tìm sản phẩm"
        /><select v-model="selectedBrand" aria-label="Thương hiệu">
          <option value="">Tất cả thương hiệu</option>
          <option v-for="brand in brands" :key="brand">{{ brand }}</option></select
        ><span>{{ filteredProducts.length }} sản phẩm</span>
      </div>
      <div v-if="!loading && !filteredProducts.length" class="portal-empty">
        Chưa có sản phẩm phù hợp để hiển thị.
      </div>
      <div class="product-grid">
        <article v-for="p in filteredProducts" :key="p.id" class="product-card">
          <ProductPhoto :src="p.imageUrl" :name="p.name" />
          <div class="product-card-body">
            <small>{{ p.brand }}</small>
            <h2>{{ p.name }}</h2>
            <p>{{ p.detail || 'Xem quy cách và chọn mua kèm buổi chơi của bạn.' }}</p>
            <div class="product-prices">
              <strong
                >{{ money(p.tubePrice) }}
                <span>/ ống · {{ p.piecesPerTube }} quả</span></strong
              ><strong v-if="p.piecePrice"
                >{{ money(p.piecePrice) }} <span>/ quả lẻ</span></strong
              >
            </div>
            <div class="stock-label" :class="{ out: !p.availableQuantityTubes }">
              {{ p.availableQuantityTubes ? `Còn hàng` : 'Tạm hết hàng' }}
            </div>
            <p class="catalogue-note">Chỉ mua kèm trong bước đặt sân.</p>
          </div>
        </article>
      </div>
    </template>
    <template
      v-if="
        page === 'pricing' ||
        (page === 'search' && ['all', 'pricing', 'booking'].includes(scope))
      "
    >
      <div v-if="!loading && !filteredGroups.length" class="portal-empty">
        Không có phân khúc phù hợp.
      </div>
      <section
        v-for="g in filteredGroups"
        :key="g.key"
        class="pricing-section"
        :class="g.key.toLowerCase()"
      >
        <div class="pricing-intro">
          <div>
            <span>{{ g.tag }}</span>
            <h2>{{ g.name }}</h2>
            <p>{{ g.rooms.length }} phòng · {{ g.courts.length }} sân đang hoạt động</p>
          </div>
          <button class="portal-button dark" @click="book(g.key)">
            {{ g.key === 'DAILY_VISITOR' ? 'Xem buổi chơi' : 'Gợi ý sân trống' }} ↗
          </button>
        </div>
        <CourtImage :src="g.image" :name="g.name" />
        <div v-if="g.key !== 'DAILY_VISITOR'" class="price-table">
          <article v-for="c in priceRows(g)" :key="c.typeId">
            <small>{{ c.typeName }}</small>
            <div>
              <span
                >Giờ thường · {{ time(c.price.openingTime) }} –
                {{ time(c.price.peakStartTime) }}</span
              ><strong>{{ money(c.price.normalPricePerHour) }} <em>/ giờ</em></strong>
            </div>
            <div>
              <span
                >Cao điểm · {{ time(c.price.peakStartTime) }} –
                {{ time(c.price.closingTime) }}</span
              ><strong>{{ money(c.price.peakPricePerHour) }} <em>/ giờ</em></strong>
            </div>
          </article>
          <p v-if="!priceRows(g).length">Chưa có bảng giá đang hoạt động.</p>
        </div>
        <div v-else class="daily-prices">
          <article v-for="d in daily" :key="d.id">
            <h3>{{ d.courtName }} · {{ d.skillLevel }}</h3>
            <p>
              {{ time(d.startTime) }} – {{ time(d.endTime) }} · Tối đa
              {{ d.maxParticipants }} người
            </p>
            <strong>{{
              d.fixedFee
                ? `${money(d.fixedFee)} / lượt`
                : 'Phí theo cấu hình buổi chơi, xác nhận tại quầy'
            }}</strong>
          </article>
          <p v-if="!daily.length">Chưa có lịch Daily Visitor đang hoạt động.</p>
        </div>
        <div class="room-grid">
          <article
            v-for="r in [...g.rooms].sort(
              (a, b) => Number(privateRoom(b)) - Number(privateRoom(a)),
            )"
            :key="r.id"
          >
            <CourtImage
              v-if="g.key === 'PREMIUM'"
              :src="roomImages[r.id]"
              :name="r.name"
            />
            <div>
              <small>{{ privateRoom(r) ? 'PRIVATE' : 'PUBLIC / CỤM SÂN' }}</small>
              <h3>{{ r.name }}</h3>
              <p>
                {{ r.courts.length }} sân · {{ r.courts.map((c) => c.name).join(', ') }}
              </p>
              <button
                class="portal-button light"
                @click="emit('navigate', `booking?type=${g.key}&room=${r.id}`)"
              >
                Chọn phòng / sân ↗
              </button>
            </div>
          </article>
        </div>
      </section>
    </template>
    <template v-if="page === 'news'">
      <article v-if="article" class="article-detail">
        <a href="#news" class="back-link">← Tất cả bài viết</a
        ><img :src="article.image" :alt="article.title" /><small>{{
          article.category
        }}</small>
        <h2>{{ article.title }}</h2>
        <p v-for="(paragraph, i) in article.paragraphs" :key="i">{{ paragraph }}</p>
        <div class="article-sources">
          <strong>Nguồn tham khảo</strong
          ><a
            v-for="source in article.sources"
            :key="source.url"
            :href="source.url"
            target="_blank"
            rel="noopener noreferrer"
            >{{ source.label }} ↗</a
          >
        </div>
      </article>
      <div v-else class="news-grid">
        <a
          v-for="a in articles"
          :key="a.id"
          :href="`#news?article=${a.id}`"
          class="news-card"
          ><img :src="a.image" :alt="a.title" loading="lazy" />
          <div>
            <small>{{ a.category }}</small>
            <h2>{{ a.title }}</h2>
            <p>{{ a.summary }}</p>
            <span>Đọc bài viết ↗</span>
          </div></a
        >
      </div>
    </template>
    <section v-if="page === 'contact'" class="contact-grid">
      <div class="contact-info">
        <span>CARROT BADMINTON</span>
        <h2>Một nơi hẹn.<br />Nhiều trận cầu.</h2>
        <p>
          Website giúp bạn xem lịch trống, đặt sân, chọn cầu và theo dõi những buổi chơi.
          Nhân viên tại quầy hỗ trợ nhận sân khi bạn đến.
        </p>
        <dl>
          <div>
            <dt>Người sáng lập</dt>
            <dd>{{ siteContact.founder || 'Đang cập nhật' }}</dd>
          </div>
          <div>
            <dt>Hotline</dt>
            <dd>
              <a
                v-if="siteContact.hotline"
                :href="`tel:${siteContact.hotline.replace(/[^+\d]/g, '')}`"
                >{{ siteContact.hotline }}</a
              ><span v-else>Đang cập nhật</span>
            </dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>
              <a v-if="siteContact.email" :href="`mailto:${siteContact.email}`">{{
                siteContact.email
              }}</a
              ><span v-else>Đang cập nhật</span>
            </dd>
          </div>
          <div>
            <dt>Địa chỉ sân</dt>
            <dd>{{ siteContact.address || 'Đang cập nhật' }}</dd>
          </div>
        </dl>
        <a
          v-if="siteContact.mapQuery"
          :href="`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(siteContact.mapQuery)}`"
          target="_blank"
          rel="noopener noreferrer"
          class="portal-button dark"
          >Chỉ đường đến sân ↗</a
        >
      </div>
      <div class="contact-map">
        <iframe
          v-if="mapUrl"
          :src="mapUrl"
          title="Bản đồ vị trí sân Carrot"
          loading="lazy"
          referrerpolicy="no-referrer-when-downgrade"
          allowfullscreen
        ></iframe>
        <div v-else class="map-placeholder">
          <span>⌖</span>
          <h3>Vị trí sân đang cập nhật</h3>
          <p>Bản đồ sẽ hiển thị khi địa chỉ thực tế được bổ sung.</p>
        </div>
      </div>
    </section>
  </div>
</template>
<style scoped>
.portal-page {
  max-width: 1300px;
  margin: auto;
  padding: 45px 32px 75px;
  color: #19271e;
}
.portal-heading {
  padding: 0 0 35px;
  border-bottom: 1px solid #e1e7da;
  margin-bottom: 32px;
}
.portal-heading > span,
.pricing-intro span,
.contact-info > span {
  font-size: 10px;
  letter-spacing: 2px;
  font-weight: 850;
  color: #718664;
}
.portal-heading h1 {
  font-size: clamp(30px, 4vw, 50px);
  letter-spacing: -2px;
  line-height: 1.12;
  margin: 18px 0;
  font-weight: 900;
}
.portal-heading p {
  font-size: 13px;
  color: #839176;
  line-height: 1.8;
}
.portal-button {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-height: 43px;
  padding: 12px 20px;
  border: 1px solid transparent;
  border-radius: 4px;
  font: inherit;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  text-decoration: none;
}
.portal-button.dark {
  background: #183b28;
  color: white;
}
.portal-button.light {
  background: white;
  border-color: #d5dfcb;
  color: #456638;
}
.portal-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.portal-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 25px 0;
}
.portal-toolbar input,
.portal-toolbar select {
  min-height: 43px;
  padding: 12px;
  border: 1px solid #dce4d2;
  border-radius: 4px;
  font: inherit;
  font-size: 13px;
  background: #fafcf6;
}
.portal-toolbar input {
  flex: 1;
}
.portal-toolbar span {
  font-size: 11px;
  color: #7a8d68;
}
.product-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 40px;
}
.product-card {
  border: 1px solid #e1e8d8;
  border-radius: 7px;
  overflow: hidden;
}
.product-card :deep(.product-photo) {
  border-radius: 0;
}
.product-card-body {
  padding: 24px;
}
.product-card small,
.news-card small,
.article-detail > small {
  color: #80906e;
  font-size: 9px;
  letter-spacing: 1.5px;
  font-weight: 800;
}
.product-card h2 {
  font-size: 23px;
  margin: 12px 0;
  letter-spacing: -0.8px;
}
.product-card p {
  font-size: 12px;
  line-height: 1.8;
  color: #879577;
  white-space: pre-line;
}
.product-prices {
  display: grid;
  gap: 10px;
  margin: 20px 0;
}
.product-prices strong {
  font-size: 20px;
  letter-spacing: -0.5px;
}
.product-prices span {
  font-size: 10px;
  font-weight: 400;
  color: #839671;
}
.stock-label {
  font-size: 10px;
  color: #598347;
  margin: 18px 0;
}
.stock-label.out {
  color: #a57f64;
}
.product-card .portal-button {
  width: 100%;
}
.pricing-section {
  padding: 30px;
  border: 1px solid #dde5d4;
  border-radius: 8px;
  margin: 28px 0 45px;
  background: #fff;
}
.pricing-section.premium {
  background: #19281e;
  border-color: #19281e;
  color: #fff;
}
.pricing-section.premium .pricing-intro span {
  color: #bfcfac;
}
.pricing-section.premium .pricing-intro p {
  color: #95aa83;
}
.pricing-section.premium .portal-button.dark {
  background: #ff8500;
  color: #18211a;
}
.pricing-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 25px;
}
.pricing-intro h2 {
  font-size: 45px;
  margin: 12px 0;
  letter-spacing: -2px;
}
.pricing-intro p {
  font-size: 12px;
  color: #879a73;
}
.pricing-section > :deep(.court-visual) {
  aspect-ratio: 24/7;
  border-radius: 5px;
}
.price-table {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
  margin: 26px 0;
}
.price-table article {
  padding: 22px;
  border: 1px solid #cad9b93d;
  border-radius: 5px;
  background: #94aa7710;
}
.price-table small {
  font-size: 11px;
  font-weight: 800;
}
.price-table article > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 20px;
}
.price-table span {
  font-size: 10px;
  color: #879b72;
}
.price-table strong {
  font-size: 21px;
  white-space: nowrap;
}
.price-table em {
  font-size: 10px;
  font-weight: 400;
  font-style: normal;
}
.room-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.room-grid article {
  border: 1px solid #c8d8b62e;
  border-radius: 5px;
  overflow: hidden;
  background: #94aa7710;
}
.room-grid article > div:last-child {
  padding: 18px;
}
.room-grid small {
  font-size: 8px;
  letter-spacing: 1px;
  color: #8ba572;
}
.room-grid h3 {
  margin: 12px 0;
  font-size: 20px;
}
.room-grid p {
  font-size: 11px;
  line-height: 1.8;
  color: #8da477;
}
.daily-prices {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin: 25px 0;
}
.daily-prices article {
  border: 1px solid #e2ead8;
  padding: 20px;
}
.daily-prices h3 {
  font-size: 17px;
  margin: 0;
}
.daily-prices p {
  font-size: 11px;
  color: #8ba175;
}
.daily-prices strong {
  font-size: 14px;
}
.news-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px;
}
.news-card {
  text-decoration: none;
  color: inherit;
  border: 1px solid #e0e8d5;
  border-radius: 6px;
  overflow: hidden;
}
.news-card img {
  width: 100%;
  aspect-ratio: 16/9;
  object-fit: cover;
}
.news-card > div {
  padding: 25px;
}
.news-card h2 {
  font-size: 26px;
  letter-spacing: -0.8px;
  line-height: 1.25;
}
.news-card p {
  font-size: 13px;
  line-height: 1.8;
  color: #889b76;
}
.news-card span {
  font-size: 12px;
  font-weight: 850;
  color: #406735;
}
.article-detail {
  max-width: 820px;
  margin: auto;
}
.article-detail > img {
  width: 100%;
  max-height: 430px;
  object-fit: cover;
  margin: 25px 0;
}
.article-detail h2 {
  font-size: 38px;
  letter-spacing: -1.4px;
}
.article-detail p {
  font-size: 16px;
  line-height: 1.9;
  color: #6b805b;
}
.back-link {
  font-size: 12px;
  color: #496c37;
  text-decoration: none;
}
.article-sources {
  padding: 24px;
  background: #f3f7ec;
  display: grid;
  gap: 14px;
  margin-top: 28px;
}
.article-sources a {
  font-size: 13px;
  color: #476d34;
}
.contact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 42px;
}
.contact-info h2 {
  font-size: 43px;
  letter-spacing: -2px;
  line-height: 1.1;
}
.contact-info > p {
  font-size: 14px;
  line-height: 1.9;
  color: #879b72;
}
.contact-info dl {
  margin: 30px 0;
}
.contact-info dl > div {
  display: grid;
  grid-template-columns: 125px 1fr;
  gap: 15px;
  padding: 17px 0;
  border-bottom: 1px solid #e3ead8;
  font-size: 13px;
}
.contact-info dt {
  color: #92a47e;
}
.contact-info dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-weight: 750;
}
.contact-info a {
  color: #365d26;
}
.contact-info .portal-button {
  color: white;
}
.contact-map {
  min-height: 480px;
  overflow: hidden;
  border-radius: 7px;
  background: #edf3e3;
  border: 1px solid #dbe5cd;
}
.contact-map iframe {
  width: 100%;
  height: 100%;
  min-height: 480px;
  border: 0;
}
.map-placeholder {
  display: grid;
  align-content: center;
  justify-items: center;
  text-align: center;
  height: 100%;
  padding: 24px;
  box-sizing: border-box;
}
.map-placeholder > span {
  font-size: 70px;
  color: #91a77d;
}
.map-placeholder h3 {
  font-size: 22px;
  letter-spacing: -0.5px;
}
.map-placeholder p {
  font-size: 12px;
  color: #92a87c;
  line-height: 1.8;
}
.portal-empty {
  padding: 35px;
  text-align: center;
  color: #8b9e76;
  font-size: 13px;
}
.portal-error {
  padding: 16px;
  background: #fff1e7;
  color: #a76b49;
  font-size: 13px;
}
.portal-error button {
  background: transparent;
  border: 0;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
}
a:focus-visible,
button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 3px solid #ffb24d;
  outline-offset: 3px;
}
@media (max-width: 950px) {
  .product-grid,
  .room-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .price-table {
    grid-template-columns: 1fr;
  }
  .contact-grid {
    grid-template-columns: 1fr;
  }
  .contact-map {
    min-height: 400px;
  }
  .portal-page {
    padding: 35px 24px 60px;
  }
}
@media (max-width: 580px) {
  .portal-page {
    padding: 28px 16px 50px;
  }
  .product-grid,
  .room-grid,
  .news-grid,
  .daily-prices {
    grid-template-columns: 1fr;
  }
  .pricing-section {
    padding: 22px;
  }
  .pricing-intro {
    flex-wrap: wrap;
  }
  .pricing-intro h2 {
    font-size: 37px;
  }
  .price-table article > div {
    flex-wrap: wrap;
  }
  .portal-toolbar {
    flex-wrap: wrap;
  }
  .portal-toolbar input,
  .portal-toolbar select {
    width: 100%;
    flex: auto;
  }
  .portal-toolbar span {
    width: 100%;
  }
  .article-detail h2 {
    font-size: 29px;
  }
  .contact-info h2 {
    font-size: 34px;
  }
  .contact-info dl > div {
    grid-template-columns: 100px 1fr;
  }
}
.available-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin: 25px 0 40px;
}
.available-grid article {
  padding: 24px;
  border: 1px solid #dce6d1;
  background: #f6faef;
  border-radius: 5px;
}
.available-grid small {
  font-size: 10px;
  color: #849c70;
}
.available-grid h3 {
  font-size: 22px;
  letter-spacing: -0.8px;
}
.available-grid p {
  font-size: 12px;
  color: #88a16f;
}
.search-availability h2 {
  font-size: 24px;
  letter-spacing: -0.8px;
}
@media (max-width: 700px) {
  .available-grid {
    grid-template-columns: 1fr;
  }
}
</style>

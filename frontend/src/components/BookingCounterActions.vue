<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue';
import { getAdminData } from '../services/adminService.js';
const props = defineProps({
  auth: { type: Object, required: true },
  work: { type: Object, default: null },
});
const emit = defineEmits(['close', 'done', 'session-expired']);
const dialog = ref(null),
  form = ref({}),
  options = ref({ products: [], courts: [] });
const error = ref(''),
  loading = ref(false),
  saving = ref(false),
  sales = ref([]);
let generation = 0,
  disposed = false;
const controllers = new Set();
const kind = computed(() => props.work?.kind);
const booking = computed(() => props.work?.booking);
const titles = {
  create: 'Đặt sân tại quầy',
  daily: 'Daily Visitor tại quầy',
  edit: 'Sửa lịch đặt sân',
  extend: 'Gia hạn giờ chơi',
  addon: 'Thêm ống cầu vào booking',
  sale: 'Bán cầu tại quầy',
  history: 'Hóa đơn bán cầu riêng',
};
const money = (value) => Number(value || 0).toLocaleString('vi-VN') + ' đ';
const product = computed(() =>
  options.value.products.find((p) => p.id === Number(form.value.productId)),
);
const court = computed(() =>
  options.value.courts.find((c) => c.id === Number(form.value.courtId)),
);
const saleUnit = ref('TUBE');
const otp = ref('');
const otpNotice = ref('');
const verificationToken = ref('');
const verifiedPhone = ref('');
const otpBusy = ref(false);
const normalizePhone = () =>
  String(form.value.walkInPhone || '')
    .trim()
    .replace(/[\s.-]/g, '')
    .replace(/^\+84/, '0');
watch(
  () => form.value.walkInPhone,
  () => {
    verificationToken.value = '';
    verifiedPhone.value = '';
    otp.value = '';
    otpNotice.value = '';
  },
);
async function sendOtp() {
  if (otpBusy.value || saving.value) return;
  otpBusy.value = true;
  error.value = '';
  try {
    const result = await api(
      '/api/counter-booking-verification/request',
      'POST',
      { phone: normalizePhone() },
    );
    verificationToken.value = '';
    verifiedPhone.value = '';
    otpNotice.value = result.message;
  } catch (e) {
    error.value = message(e);
  } finally {
    otpBusy.value = false;
  }
}
async function verifyOtp() {
  if (otpBusy.value || saving.value) return;
  otpBusy.value = true;
  error.value = '';
  const phone = normalizePhone();
  try {
    const result = await api(
      '/api/counter-booking-verification/verify',
      'POST',
      { phone, otp: otp.value },
    );
    if (phone !== normalizePhone()) return;
    verificationToken.value = result.verificationToken;
    verifiedPhone.value = phone;
    otpNotice.value =
      'Đã xác minh số điện thoại, có thể đặt sân trong 10 phút.';
  } catch (e) {
    error.value = message(e);
  } finally {
    otpBusy.value = false;
  }
}
const tubeTotal = computed(
  () =>
    (kind.value === 'sale' && saleUnit.value === 'PIECE'
      ? product.value?.piecePrice || 0
      : product.value?.tubePrice || 0) * Number(form.value.quantityTubes || 0),
);
function minutes(value) {
  const [h, m] = String(value || '')
    .slice(0, 5)
    .split(':')
    .map(Number);
  return h * 60 + m;
}
const courtTotal = computed(() => {
  const price = court.value?.price;
  const start = minutes(
    kind.value === 'extend' ? booking.value?.endTime : form.value.startTime,
  );
  const end = minutes(form.value.endTime),
    peak = minutes(price?.peakStartTime);
  if (
    !price ||
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    end <= start
  )
    return 0;
  return (
    (Math.max(0, Math.min(end, peak) - start) * price.normalPricePerHour) / 60 +
    (Math.max(0, end - Math.max(start, peak)) * price.peakPricePerHour) / 60
  );
});
const due = computed(() =>
  kind.value === 'daily'
    ? Number(counterDailySession.value?.fixedFee || 0) *
      Number(form.value.slotCount || 1)
    : kind.value === 'sale' || kind.value === 'addon'
      ? tubeTotal.value
      : kind.value === 'extend'
        ? courtTotal.value
        : courtTotal.value +
          (kind.value === 'create'
            ? tubeTotal.value
            : booking.value?.shuttlecockAmount || 0),
);
const canSubmit = computed(
  () =>
    !loading.value &&
    !saving.value &&
    !otpBusy.value &&
    (!['create', 'daily'].includes(kind.value) ||
      (!!verificationToken.value && verifiedPhone.value === normalizePhone())),
);
async function api(path, method = 'GET', body) {
  const controller = new AbortController();
  controllers.add(controller);
  const timer = setTimeout(() => controller.abort(), 15000);
  try {
    return await getAdminData(path, props.auth.accessToken, controller.signal, {
      method,
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } finally {
    clearTimeout(timer);
    controllers.delete(controller);
  }
}
function message(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Chưa nhận được phản hồi. Kiểm tra lịch hoặc hóa đơn trước khi thử lại.'
    : e.message || 'Không kết nối được máy chủ.';
}
function close() {
  if (!saving.value) emit('close');
}
watch(
  () => props.work,
  async (work) => {
    const current = ++generation;
    error.value = '';
    sales.value = [];
    saleUnit.value = 'TUBE';
    verificationToken.value = '';
    verifiedPhone.value = '';
    otpNotice.value = '';
    otp.value = '';
    if (!work) {
      dialog.value?.close();
      return;
    }
    const b = work.booking;
    const today = new Intl.DateTimeFormat('sv-SE', {
      timeZone: 'Asia/Ho_Chi_Minh',
    }).format(new Date());
    form.value = {
      courtId: b?.court?.id || '',
      bookingDate: b?.bookingDate || today,
      startTime: b?.startTime?.slice(0, 5) || '18:00',
      endTime: b?.endTime?.slice(0, 5) || '19:00',
      productId: '',
      quantityTubes: 1,
      walkInName: '',
      saleCustomerName: '',
      saleCustomerPhone: '',
      walkInPhone: '',
      paymentMethod: 'CASH',
      amountReceived: '',
      skillLevel: 'TBY',
      sessionId: '',
      slotCount: 1,
    };
    await nextTick();
    if (disposed || current !== generation) return;
    dialog.value?.showModal();
    loading.value = true;
    try {
      const path =
        work.kind === 'history'
          ? '/api/booking-operations/counter-sales' +
            (b ? `?bookingId=${b.id}` : '')
          : '/api/booking-operations/options';
      const data = await api(path);
      if (disposed || current !== generation) return;
      if (work.kind === 'daily') await loadCounterDaily();
      if (work.kind === 'history') sales.value = data;
      else options.value = data;
    } catch (e) {
      if (current === generation && !disposed) error.value = message(e);
    } finally {
      if (current === generation) loading.value = false;
    }
  },
  { immediate: true },
);
watch(due, () => {
  if (kind.value === 'sale') form.value.amountReceived = due.value;
});
function changeMethod() {
  form.value.amountReceived = due.value;
}
function changeProduct() {
  if (!form.value.productId) form.value.quantityTubes = 1;
}
async function submit() {
  if (!canSubmit.value) return;
  if (kind.value === 'daily') {
    await submitCounterDaily();
    return;
  }
  error.value = '';
  const f = form.value,
    k = kind.value,
    b = booking.value;
  const tubes = Number(f.quantityTubes);
  if (['sale', 'addon'].includes(k) || (k === 'create' && f.productId)) {
    if (
      !product.value ||
      !Number.isSafeInteger(tubes) ||
      tubes < 1 ||
      tubes > 10000 ||
      (!(k === 'sale' && saleUnit.value === 'PIECE') &&
        tubes > product.value.availableQuantityTubes)
    ) {
      error.value = 'Chọn cầu và số ống không vượt tồn có thể bán.';
      return;
    }
  }
  let path,
    method = 'POST',
    body;
  if (k === 'create') {
    const phone = f.walkInPhone
      .trim()
      .replace(/[\s.-]/g, '')
      .replace(/^\+84/, '0');
    if (!f.walkInName.trim() || !/^0[35789]\d{8}$/.test(phone)) {
      error.value = 'Nhập tên và số điện thoại Việt Nam hợp lệ.';
      return;
    }
    if (!verificationToken.value || verifiedPhone.value !== phone) {
      error.value = 'Vui lòng xác minh OTP trước khi đặt sân.';
      return;
    }
    path = '/api/bookings/walk-in';
    body = {
      courtIds: [Number(f.courtId)],
      bookingDate: f.bookingDate,
      startTime: f.startTime,
      endTime: f.endTime,
      walkInName: f.walkInName.trim(),
      walkInPhone: phone,
      verificationToken: verificationToken.value,
      productId: f.productId ? Number(f.productId) : null,
      quantityTubes: f.productId ? tubes : 0,
      quantityPieces: 0,
    };
  } else if (k === 'edit' || k === 'extend') {
    path = `/api/booking-operations/${b.id}`;
    method = 'PATCH';
    body = {
      courtId: Number(f.courtId),
      bookingDate: f.bookingDate,
      startTime: f.startTime,
      endTime: f.endTime,
    };
  } else if (k === 'addon') {
    path = `/api/booking-operations/${b.id}/tubes`;
    body = { productId: Number(f.productId), quantityTubes: tubes };
  } else {
    const received = Number(f.amountReceived);
    if (
      f.amountReceived === '' ||
      !Number.isSafeInteger(received) ||
      received < due.value ||
      (f.paymentMethod === 'BANK_TRANSFER' && received !== due.value)
    ) {
      error.value = 'Tiền mặt phải đủ tiền, chuyển khoản phải đúng tổng tiền.';
      return;
    }
    path = '/api/booking-operations/counter-sales';
    body = {
      productId: Number(f.productId),
      quantityTubes: saleUnit.value === 'PIECE' ? 0 : tubes,
      quantityPieces: saleUnit.value === 'PIECE' ? tubes : 0,
      bookingId: b?.id || null,
      customerName: f.saleCustomerName?.trim() || null,
      customerPhone: f.saleCustomerPhone?.trim() || null,
      paymentMethod: f.paymentMethod,
      amountReceived: received,
    };
  }
  if (
    ['create', 'edit', 'extend'].includes(k) &&
    (!f.courtId ||
      !f.bookingDate ||
      !Number.isFinite(minutes(f.startTime)) ||
      !Number.isFinite(minutes(f.endTime)) ||
      minutes(f.endTime) - minutes(f.startTime) < 60 ||
      minutes(f.startTime) % 30 ||
      minutes(f.endTime) % 30)
  ) {
    error.value = 'Chọn sân, ngày, giờ :00 hoặc :30; tối thiểu một giờ.';
    return;
  }
  saving.value = true;
  try {
    const result = await api(path, method, body);
    if (disposed) return;
    if (k === 'sale') {
      sales.value = [result];
      form.value = { ...f };
      await nextTick();
      emit(
        'done',
        `Đã bán ${result.quantityPieces > 0 ? result.quantityPieces + ' quả' : result.quantityTubes + ' ống'}, nhận ${money(result.totalAmount)} bằng ${result.paymentMethod === 'CASH' ? 'tiền mặt' : 'chuyển khoản'}. Phiếu ${result.issueCode}. Tiền trả khách: ${money(result.changeAmount)}.`,
      );
    } else
      emit(
        'done',
        `${titles[k]} thành công. Tổng tiền đã được cập nhật theo backend.`,
      );
  } catch (e) {
    if (!disposed) error.value = message(e);
  } finally {
    saving.value = false;
  }
}
onUnmounted(() => {
  disposed = true;
  generation++;
  controllers.forEach((c) => c.abort());
});

const counterDailyRows = ref([]);
const counterDailyLoading = ref(false);
let counterDailySequence = 0;
const counterDailyChoices = computed(() =>
  counterDailyRows.value.filter(
    (s) => s.schedule?.skillLevel === form.value.skillLevel,
  ),
);
const counterDailySession = computed(() =>
  counterDailyRows.value.find((s) => s.id === Number(form.value.sessionId)),
);
function dailyPast(s) {
  return (
    new Date(
      `${s.sessionDate}T${String(s.startTime).slice(0, 8)}+07:00`,
    ).getTime() <= Date.now()
  );
}
async function loadCounterDaily() {
  const seq = ++counterDailySequence;
  const gen = generation;
  counterDailyLoading.value = true;
  form.value.sessionId = '';
  error.value = '';
  counterDailyRows.value = [];
  try {
    const data = await api(
      `/api/courts/daily-options?date=${form.value.bookingDate}`,
    );
    if (disposed || gen !== generation || seq !== counterDailySequence) return;
    if (!Array.isArray(data.sessions))
      throw new Error('Dữ liệu ca Daily không đúng định dạng.');
    counterDailyRows.value = data.sessions;
  } catch (e) {
    if (gen === generation && seq === counterDailySequence)
      error.value = message(e);
  } finally {
    if (seq === counterDailySequence) counterDailyLoading.value = false;
  }
}
watch(
  () => form.value.bookingDate,
  () => {
    if (kind.value === 'daily') loadCounterDaily();
  },
);
watch(
  () => form.value.skillLevel,
  () => {
    form.value.sessionId = '';
  },
);
async function submitCounterDaily() {
  const s = counterDailySession.value;
  const count = Number(form.value.slotCount);
  const phone = normalizePhone();
  if (!form.value.walkInName?.trim() || !/^0[35789]\d{8}$/.test(phone)) {
    error.value = 'Nhập tên khách và SĐT hợp lệ.';
    return;
  }
  if (!verificationToken.value || verifiedPhone.value !== phone) {
    error.value = 'Vui lòng xác minh OTP SĐT khách trước.';
    return;
  }
  if (
    !s ||
    s.schedule?.skillLevel !== form.value.skillLevel ||
    !['OPEN', 'FULL'].includes(s.status) ||
    dailyPast(s) ||
    ![1, 2].includes(count) ||
    s.remainingSlots < count
  ) {
    error.value =
      'Chọn ca còn đủ chỗ, chưa bắt đầu; mỗi lần chỉ đăng ký 1–2 người.';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await api('/api/counter-daily-bookings', 'POST', {
      sessionId: s.id,
      fullName: form.value.walkInName.trim(),
      phone,
      slotCount: count,
      verificationToken: verificationToken.value,
    });
    if (disposed) return;
    verificationToken.value = '';
    verifiedPhone.value = '';
    emit(
      'done',
      `Đã đăng ký Daily ${s.schedule.skillLevel} cho ${count} người, ca ${s.startTime.slice(0, 5)}–${s.endTime.slice(0, 5)}. Chưa nhận tiền; tiếp tục check-in/thu tiền tại mục Daily.`,
    );
  } catch (e) {
    if (!disposed) error.value = message(e);
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <dialog ref="dialog" class="counter-modal" @cancel.prevent="close">
    <header>
      <div>
        <small>CARROT / QUẦY DỊCH VỤ</small>
        <h2>{{ titles[kind] }}</h2>
        <p v-if="booking">
          Booking #{{ booking.id }} · {{ booking.court?.name }}
        </p>
      </div>
      <button :disabled="saving" @click="close" aria-label="Đóng">✕</button>
    </header>
    <p v-if="loading" class="note">Đang tải dữ liệu…</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section v-if="kind === 'history'" class="sale-list">
      <p class="note">
        100 hóa đơn gần nhất. Các khoản này được thu riêng, không cộng vào
        booking.
      </p>
      <article v-for="sale in sales" :key="sale.id">
        <strong>{{ sale.issueCode }}</strong>
        <p>
          {{ sale.productName }} ·
          {{
            sale.quantityPieces > 0
              ? sale.quantityPieces + ' quả'
              : sale.quantityTubes + ' ống'
          }}
          ×
          {{ money(sale.unitPrice) }}
        </p>
        <p>
          Booking:
          {{ sale.bookingId ? '#' + sale.bookingId : 'Mua riêng tại quầy' }}
        </p>
        <p>
          {{ sale.paymentMethod === 'CASH' ? 'Tiền mặt' : 'Chuyển khoản' }} ·
          {{ money(sale.totalAmount) }}
        </p>
        <small
          >{{ sale.paidAt?.replace('T', ' ') }} · Đã nhận
          {{ money(sale.amountReceived) }} · Trả lại
          {{ money(sale.changeAmount) }}</small
        >
      </article>
      <p v-if="!loading && !sales.length" class="note">
        Chưa có hóa đơn bán cầu riêng.
      </p>
    </section>
    <form v-else @submit.prevent="submit">
      <fieldset :disabled="loading || saving">
        <div v-if="['create', 'daily'].includes(kind)" class="grid">
          <label
            >Tên khách<input
              v-model="form.walkInName"
              required
              maxlength="150" /></label
          ><label
            >Số điện thoại<input v-model="form.walkInPhone" type="tel" required
          /></label>
        </div>

        <div v-if="kind === 'sale'" class="grid">
          <label
            >Tên người mua<input
              v-model="form.saleCustomerName"
              maxlength="150"
              placeholder="Nhập tên để theo dõi hóa đơn" /></label
          ><label
            >SĐT người mua<input
              v-model="form.saleCustomerPhone"
              type="tel"
              maxlength="30"
              placeholder="Không bắt buộc khi chỉ mua cầu"
          /></label>
        </div>
        <section v-if="kind === 'daily'" class="daily-counter-panel">
          <p class="note">
            Giờ chơi cố định theo ca · Giá mỗi người theo backend · Tối đa 2
            người mỗi lần đăng ký.
          </p>
          <div class="grid">
            <label
              >Ngày chơi<input
                v-model="form.bookingDate"
                type="date"
                required /></label
            ><label
              >Trình độ<select v-model="form.skillLevel">
                <option>TBY</option>
                <option>TB</option>
                <option>TB+</option>
              </select></label
            >
          </div>
          <label
            >Ca Daily Visitor<select
              v-model="form.sessionId"
              required
              :disabled="counterDailyLoading"
            >
              <option value="">
                {{ counterDailyLoading ? 'Đang tải ca…' : 'Chọn ca chơi' }}
              </option>
              <option
                v-for="s in counterDailyChoices"
                :key="s.id"
                :value="s.id"
                :disabled="
                  !['OPEN', 'FULL'].includes(s.status) ||
                  dailyPast(s) ||
                  s.remainingSlots < Number(form.slotCount)
                "
              >
                {{ s.schedule.court.name }} · {{ s.startTime.slice(0, 5) }}–{{
                  s.endTime.slice(0, 5)
                }}
                · {{ money(s.fixedFee) }}/người · Còn {{ s.remainingSlots }} chỗ
                · {{ s.status }}
              </option>
            </select></label
          >
          <p
            v-if="!counterDailyLoading && !counterDailyChoices.length"
            class="note"
          >
            Chưa có ca {{ form.skillLevel }} cho ngày đã chọn. Không tự tạo ca
            hoặc đổi giờ chơi.
          </p>
          <label
            >Số người<select v-model="form.slotCount">
              <option :value="1">1 người</option>
              <option :value="2">2 người</option>
            </select></label
          >
          <p v-if="counterDailySession" class="note">
            {{ money(counterDailySession.fixedFee) }}/người ×
            {{ form.slotCount }} người =
            <strong>{{
              money(
                Number(counterDailySession.fixedFee) * Number(form.slotCount),
              )
            }}</strong
            >. Tiền cầu của Daily theo cấu hình ca, không mua thêm ở đây.
          </p>
        </section>

        <section v-if="['create', 'daily'].includes(kind)" class="otp-panel">
          <strong>01 · Xác minh khách tại quầy</strong>
          <p>
            OTP mô phỏng cho luận văn: nhân viên lấy mã trong terminal backend,
            nhập vào ô bên dưới rồi xác minh để đặt sân.
          </p>
          <button
            type="button"
            :disabled="otpBusy || saving || loading"
            @click="sendOtp"
          >
            {{ otpBusy ? 'Đang xử lý…' : 'Kiểm tra & tạo OTP' }}
          </button>
          <div class="grid">
            <label
              >Mã OTP<input
                v-model="otp"
                inputmode="numeric"
                maxlength="6"
                autocomplete="one-time-code" /></label
            ><button
              type="button"
              :disabled="otpBusy || !/^\d{6}$/.test(otp)"
              @click="verifyOtp"
            >
              Xác minh OTP
            </button>
          </div>
          <p v-if="otpNotice" role="status">{{ otpNotice }}</p>
        </section>
        <template v-if="['create', 'edit', 'extend'].includes(kind)">
          <div class="grid">
            <label
              >Sân<select
                v-model="form.courtId"
                required
                :disabled="kind === 'extend'"
              >
                <option value="">Chọn sân</option>
                <option v-for="c in options.courts" :key="c.id" :value="c.id">
                  {{ c.name }} · {{ c.roomName }}
                </option>
              </select></label
            ><label
              >Ngày chơi<input
                v-model="form.bookingDate"
                type="date"
                required
                :disabled="
                  kind === 'extend' || (kind === 'edit' && !!booking?.user)
                "
            /></label>
          </div>
          <div class="grid">
            <label
              >Giờ bắt đầu<input
                v-model="form.startTime"
                type="time"
                step="1800"
                required
                :disabled="kind === 'extend'" /></label
            ><label
              >{{ kind === 'extend' ? 'Gia hạn đến' : 'Giờ kết thúc'
              }}<input v-model="form.endTime" type="time" step="1800" required
            /></label>
          </div>
          <p v-if="kind === 'extend'" class="note">
            Backend kiểm tra phần giờ thêm với booking, Daily Visitor, bảo trì
            và giờ mở cửa. Tiền đã chơi giữ nguyên, chỉ tính thêm phần gia hạn.
          </p>
        </template>
        <label v-if="kind === 'sale'"
          >Hình thức bán<select
            v-model="saleUnit"
            @change="form.quantityTubes = 1"
          >
            <option value="TUBE">Nguyên ống</option>
            <option value="PIECE">Cầu lẻ theo quả</option>
          </select></label
        >
        <template v-if="['create', 'addon', 'sale'].includes(kind)">
          <div class="grid">
            <label
              >Loại ống cầu<select
                v-model="form.productId"
                :required="kind !== 'create'"
                @change="changeProduct"
              >
                <option value="">
                  {{
                    kind === 'create' ? 'Không mua thêm cầu' : 'Chọn loại cầu'
                  }}
                </option>
                <option
                  v-for="p in options.products"
                  :key="p.id"
                  :value="p.id"
                  :disabled="
                    saleUnit === 'PIECE' && kind === 'sale'
                      ? !p.piecePrice
                      : p.availableQuantityTubes < 1
                  "
                >
                  {{ p.name }} ·
                  {{
                    money(
                      kind === 'sale' && saleUnit === 'PIECE'
                        ? p.piecePrice
                        : p.tubePrice,
                    )
                  }}
                  · tồn nguyên ống {{ p.availableQuantityTubes }} ống
                </option>
              </select></label
            ><label v-if="form.productId">
              {{ kind === 'sale' && saleUnit === 'PIECE' ? 'Số quả' : 'Số ống'
              }}<input
                v-model="form.quantityTubes"
                type="number"
                min="1"
                :max="
                  kind === 'sale' && saleUnit === 'PIECE'
                    ? 10000
                    : product?.availableQuantityTubes
                "
                step="1"
                required
            /></label>
          </div>
          <p v-if="kind === 'addon'" class="note">
            Chưa check-in: giữ thêm cùng loại cầu. Đã check-in: xác nhận giao
            cầu và trừ kho ngay. Tiền được cộng vào hóa đơn booking.
          </p>
          <p v-if="kind === 'sale'" class="note">
            Hóa đơn riêng, không cộng vào tiền sân. Chỉ xác nhận sau khi đã nhận
            tiền và giao cầu cho khách.
          </p>
        </template>
        <div class="estimate">
          <span>{{
            kind === 'extend'
              ? 'Tiền gia hạn dự kiến'
              : kind === 'addon'
                ? 'Tiền cầu thêm dự kiến'
                : 'Tổng tiền dự kiến'
          }}</span
          ><strong>{{ money(due) }}</strong
          ><small v-if="['create', 'edit'].includes(kind)"
            >Tiền sân {{ money(courtTotal)
            }}{{
              kind === 'create' ? ' · Tiền cầu ' + money(tubeTotal) : ''
            }}</small
          >
        </div>
        <template v-if="kind === 'sale'"
          ><div class="grid">
            <label
              >Phương thức<select
                v-model="form.paymentMethod"
                @change="changeMethod"
              >
                <option value="CASH">Tiền mặt</option>
                <option value="BANK_TRANSFER">Chuyển khoản</option>
              </select></label
            ><label
              >Tiền thực nhận<input
                v-model="form.amountReceived"
                type="number"
                :min="due"
                step="1"
                required
            /></label>
          </div>
          <p v-if="form.paymentMethod === 'CASH'" class="note">
            Tiền trả lại:
            <strong>{{
              money(Math.max(0, Number(form.amountReceived || 0) - due))
            }}</strong>
          </p></template
        >
        <p class="note">
          Giá và tình trạng sân được backend xác nhận tại thời điểm lưu.
        </p>
      </fieldset>
      <footer>
        <button type="button" :disabled="saving" @click="close">Quay lại</button
        ><button class="primary" :disabled="!canSubmit">
          {{
            saving
              ? 'Đang xử lý…'
              : kind === 'sale'
                ? 'Xác nhận giao cầu & đã nhận tiền'
                : kind === 'addon'
                  ? 'Xác nhận thêm cầu'
                  : 'Lưu thay đổi'
          }}
        </button>
      </footer>
    </form>
  </dialog>
</template>
<style scoped>
.counter-modal {
  width: min(680px, calc(100% - 28px));
  max-height: 90dvh;
  padding: 24px;
  border: 1px solid #e8ddd0;
  border-radius: 22px;
  background: #fffdf9;
  color: #352b23;
  box-sizing: border-box;
  overflow: auto;
  box-shadow: 0 25px 80px #20150e30;
}
.counter-modal::backdrop {
  background: #20150e90;
}
header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
header small {
  color: #b16d27;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 2px;
}
h2 {
  margin: 8px 0;
  font-size: 25px;
}
header p {
  margin: 0;
  font-size: 13px;
  color: #817568;
}
button {
  border: 1px solid #e8ddd0;
  border-radius: 10px;
  background: #fff;
  padding: 11px 15px;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}
.primary {
  background: #df802c;
  color: white;
  border-color: #df802c;
  font-weight: 700;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
fieldset {
  border: 0;
  padding: 0;
  margin: 0;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 16px;
}
label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
}
input,
select {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  border: 1px solid #e8ddd0;
  border-radius: 10px;
  padding: 12px;
  background: white;
  color: #352b23;
  font: inherit;
  font-weight: 400;
}
input:focus,
select:focus {
  outline: 2px solid #e9a156;
  outline-offset: 2px;
}
.note {
  font-size: 12px;
  line-height: 1.7;
  color: #817568;
}
.error {
  padding: 12px;
  background: #fff0eb;
  color: #a43e24;
  border-radius: 10px;
  font-size: 13px;
}
.estimate {
  display: grid;
  gap: 7px;
  background: #f8eddf;
  padding: 18px;
  border-radius: 14px;
  margin: 20px 0;
}
.estimate span,
.estimate small {
  font-size: 12px;
  color: #817568;
}
.estimate strong {
  font-size: 26px;
  color: #a9611f;
}
footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
.sale-list article {
  padding: 16px;
  border: 1px solid #e8ddd0;
  border-radius: 12px;
  margin: 12px 0;
  font-size: 13px;
}
.sale-list article p {
  margin: 8px 0;
}
.sale-list article small {
  color: #817568;
}
@media (max-width: 520px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .counter-modal {
    padding: 18px;
  }
  footer {
    flex-wrap: wrap;
  }
  footer button {
    flex: 1;
  }
}
</style>
<style scoped>
.otp-panel {
  padding: 18px;
  margin: 18px 0;
  border: 1px solid #e6cfad;
  border-radius: 14px;
  background: #fff8ed;
}
.otp-panel p {
  font-size: 12px;
  line-height: 1.7;
  color: #795c3d;
}
.otp-panel .grid {
  margin-top: 14px;
  align-items: end;
}
</style>

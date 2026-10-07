<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { getAdminData } from '../services/adminService';
const props = defineProps({
  auth: { type: Object, required: true },
  role: { type: String, default: 'CUSTOMER' },
});
const emit = defineEmits(['session-expired', 'changed']);
const rows = ref([]),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  success = ref('');
const query = ref(''),
  status = ref(''),
  page = ref(1),
  selection = ref(null),
  dialog = ref(null),
  formError = ref('');
const violations = ref([]),
  historyLoading = ref(false),
  historyError = ref('');
let disposed = false,
  listController,
  actionController,
  historyController;
const labels = {
  ACTIVE: 'Đang hoạt động',
  WARNING: 'Cảnh báo (WARNING)',
  SUSPENDED: 'Đã khóa (SUSPENDED)',
};
const title = computed(() =>
  props.role === 'STAFF' ? 'Quản lý nhân viên' : 'Quản lý người dùng',
);
const accounts = computed(() => rows.value.filter((u) => u.role === props.role));
const filtered = computed(() =>
  accounts.value
    .filter((u) => !status.value || u.status === status.value)
    .filter((u) =>
      `${u.fullName || ''} ${u.email || ''} ${u.phone || ''} ${u.id}`
        .toLocaleLowerCase('vi')
        .includes(query.value.trim().toLocaleLowerCase('vi')),
    ),
);
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10)));
const shown = computed(() =>
  filtered.value.slice((page.value - 1) * 10, page.value * 10),
);
const dates = (t) =>
  t
    ? new Date(/[Zz]|[+-]\d\d:\d\d$/.test(t) ? t : `${t}+07:00`).toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
      })
    : '—';
watch([query, status, () => props.role], () => {
  page.value = 1;
});
watch(pages, (value) => {
  page.value = Math.min(page.value, value);
});
watch(selection, async (value) => {
  await nextTick();
  if (disposed) return;
  if (value && !dialog.value?.open) dialog.value?.showModal();
  else if (!value) dialog.value?.close();
});
function message(e) {
  if (e.status === 401) emit('session-expired');
  return e.name === 'AbortError'
    ? 'Yêu cầu hết thời gian. Tải lại danh sách để kiểm tra kết quả trước khi thử lại.'
    : e.message;
}
async function request(path, current, options = {}) {
  const timeout = setTimeout(() => current.abort(), 15000);
  try {
    return await getAdminData(path, props.auth.accessToken, current.signal, options);
  } finally {
    clearTimeout(timeout);
  }
}
async function load() {
  if (saving.value) return;
  listController?.abort();
  const current = new AbortController();
  listController = current;
  loading.value = true;
  error.value = '';
  try {
    const data = await request('/api/admin/users', current);
    if (!Array.isArray(data)) throw Error('Danh sách tài khoản không hợp lệ.');
    if (!disposed && listController === current) rows.value = data;
  } catch (e) {
    if (!disposed && listController === current) error.value = message(e);
  } finally {
    if (listController === current) loading.value = false;
  }
}
function open(user, kind) {
  if (saving.value || loading.value) return;
  historyController?.abort();
  selection.value = {
    user,
    kind,
    form: {
      fullName: user.fullName || '',
      phone: user.phone || '',
      email: user.email || '',
      password: '',
      confirmPassword: '',
    },
  };
  formError.value = '';
  violations.value = [];
  historyError.value = '';
  if (kind === 'detail') loadHistory(user.id);
}
function openCreate() {
  open(
    { fullName: '', phone: '', email: '', authProvider: 'PHONE', role: props.role },
    'create',
  );
}
function close() {
  if (saving.value) return;
  selection.value = null;
  dialog.value?.close();
  historyController?.abort();
}
async function loadHistory(id) {
  const current = new AbortController();
  historyController = current;
  historyLoading.value = true;
  historyError.value = '';
  try {
    const data = await request(`/api/admin/users/${id}/violations`, current);
    if (!Array.isArray(data)) throw Error('Lịch sử vi phạm không hợp lệ.');
    if (!disposed && historyController === current) violations.value = data;
  } catch (e) {
    if (!disposed && historyController === current) historyError.value = message(e);
  } finally {
    if (historyController === current) historyLoading.value = false;
  }
}
async function submit() {
  if (saving.value || !selection.value || selection.value.kind === 'detail') return;
  const { user, kind, form } = selection.value;
  formError.value = '';
  let path = `/api/admin/users/${user.id}`,
    method = 'PATCH',
    body;
  if (kind === 'edit' || kind === 'create') {
    if (!form.fullName.trim()) {
      formError.value = 'Vui lòng nhập họ tên.';
      return;
    }
    const phone = form.phone.trim().replace(/\s+/g, '').replace(/^\+84/, '0');
    if (
      (phone && !/^0[35789]\d{8}$/.test(phone)) ||
      (user.authProvider === 'PHONE' && !phone)
    ) {
      formError.value = 'Nhập số điện thoại Việt Nam hợp lệ.';
      return;
    }
    method = kind === 'create' ? 'POST' : 'PUT';
    body = {
      fullName: form.fullName.trim(),
      email: form.email.trim() || null,
      phone: phone || null,
    };
    if (kind === 'create') {
      if (
        form.password.length < 8 ||
        new TextEncoder().encode(form.password).length > 72 ||
        !form.password.trim()
      ) {
        formError.value = 'Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte.';
        return;
      }
      if (form.password !== form.confirmPassword) {
        formError.value = 'Mật khẩu xác nhận không khớp.';
        return;
      }
      path = '/api/admin/users';
      body.role = user.role;
      body.password = form.password;
    }
  } else if (kind === 'delete') method = 'DELETE';
  else path += kind === 'suspend' ? '/suspend' : '/activate';
  saving.value = true;
  actionController = new AbortController();
  try {
    const data = await request(path, actionController, {
      method,
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (disposed) return;
    if (kind === 'create') {
      rows.value = [data, ...rows.value];
      query.value = '';
      status.value = '';
      page.value = 1;
    } else if (kind === 'delete') rows.value = rows.value.filter((u) => u.id !== user.id);
    else rows.value = rows.value.map((u) => (u.id === user.id ? data : u));
    saving.value = false;
    close();
    success.value =
      kind === 'create'
        ? 'Đã tạo tài khoản thành công. Đăng nhập bằng số điện thoại và mật khẩu vừa tạo.'
        : kind === 'edit'
          ? 'Đã cập nhật thông tin tài khoản.'
          : kind === 'delete'
            ? 'Đã xóa tài khoản chưa có dữ liệu liên quan.'
            : kind === 'suspend'
              ? 'Đã khóa tài khoản. Các yêu cầu dùng JWT cũ cũng sẽ bị chặn.'
              : 'Đã duyệt mở lại tài khoản về ACTIVE. Lịch sử vi phạm được giữ nguyên.';
    emit('changed');
  } catch (e) {
    if (!disposed) formError.value = message(e);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
onUnmounted(() => {
  disposed = true;
  listController?.abort();
  actionController?.abort();
  historyController?.abort();
});
</script>
<template>
  <section class="users-panel">
    <header>
      <div>
        <h2>{{ title }}</h2>
        <p>
          Tất cả tài khoản, không lọc theo kỳ thống kê · {{ accounts.length }} tài khoản
        </p>
      </div>
      <div class="actions">
        <button class="primary" :disabled="loading || saving" @click="openCreate">
          {{ role === 'STAFF' ? '+ Thêm nhân viên' : '+ Thêm người dùng' }}
        </button>
        <button :disabled="loading || saving" @click="load">↻ Tải lại</button>
      </div>
    </header>
    <p v-if="error" class="message error" role="alert">{{ error }}</p>
    <p v-if="success" class="message success" role="status">{{ success }}</p>
    <div class="metrics">
      <article v-for="s in ['ACTIVE', 'WARNING', 'SUSPENDED']" :key="s">
        <small>{{ labels[s] }}</small
        ><strong>{{ accounts.filter((u) => u.status === s).length }}</strong>
      </article>
    </div>
    <p class="hint" v-if="role === 'CUSTOMER'">
      WARNING là cảnh báo vi phạm. SUSPENDED là khóa tài khoản, chặn đăng nhập và API cần
      đăng nhập. Admin có thể xem xét rồi duyệt mở lại.
    </p>
    <div class="filters">
      <input
        v-model="query"
        placeholder="Tìm họ tên, email, số điện thoại…"
        aria-label="Tìm tài khoản"
      /><select v-model="status" aria-label="Lọc trạng thái">
        <option value="">Tất cả trạng thái</option>
        <option v-for="s in ['ACTIVE', 'WARNING', 'SUSPENDED']" :key="s" :value="s">
          {{ labels[s] }}
        </option>
      </select>
    </div>
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Tài khoản</th>
            <th>Liên hệ</th>
            <th>Trạng thái</th>
            <th>Vi phạm</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in shown" :key="user.id">
            <td>
              <strong>{{ user.fullName }}</strong
              ><small>#{{ user.id }} · {{ user.authProvider }}</small>
            </td>
            <td>
              {{ user.phone || 'Chưa có số điện thoại'
              }}<small>{{ user.email || 'Chưa có email' }}</small>
            </td>
            <td>
              <span class="badge" :class="user.status">{{
                labels[user.status] || user.status
              }}</span>
            </td>
            <td>{{ user.violationCount || 0 }} lần</td>
            <td>
              <div class="account-actions">
                <button :disabled="saving || loading" @click="open(user, 'detail')">
                  Chi tiết
                </button>
                <button :disabled="saving || loading" @click="open(user, 'edit')">
                  Sửa
                </button>
                <button
                  class="danger"
                  :disabled="saving || loading"
                  @click="open(user, 'delete')"
                >
                  Xóa
                </button>
                <button
                  v-if="['WARNING', 'SUSPENDED'].includes(user.status)"
                  class="primary"
                  :disabled="saving || loading"
                  @click="open(user, 'activate')"
                >
                  Duyệt mở lại
                </button>
                <button
                  v-else
                  class="danger"
                  :disabled="saving || loading"
                  @click="open(user, 'suspend')"
                >
                  Khóa
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="!shown.length">
            <td colspan="5" class="empty">
              {{
                loading
                  ? 'Đang tải…'
                  : error
                    ? 'Chưa tải được dữ liệu.'
                    : 'Không có tài khoản phù hợp.'
              }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <footer class="pagination">
      <span>{{ filtered.length }} kết quả</span
      ><button :disabled="page <= 1" @click="page--">Trước</button
      ><span>{{ page }} / {{ pages }}</span
      ><button :disabled="page >= pages" @click="page++">Sau</button>
    </footer>
    <dialog
      ref="dialog"
      class="modal"
      aria-labelledby="user-dialog-title"
      @cancel.prevent="close"
    >
      <form v-if="selection" @submit.prevent="submit">
        <header>
          <div>
            <h3 id="user-dialog-title">
              {{
                selection.kind === 'create'
                  ? selection.user.role === 'STAFF'
                    ? 'Thêm nhân viên'
                    : 'Thêm người dùng'
                  : selection.kind === 'edit'
                    ? 'Sửa thông tin'
                    : selection.kind === 'detail'
                      ? 'Chi tiết tài khoản'
                      : selection.kind === 'suspend'
                        ? 'Xác nhận khóa'
                        : selection.kind === 'delete'
                          ? 'Xác nhận xóa'
                          : 'Duyệt mở lại tài khoản'
              }}
            </h3>
            <p v-if="selection.kind !== 'create'">
              {{ selection.user.fullName }} · #{{ selection.user.id }}
            </p>
            <p v-else>Tài khoản ACTIVE · Đăng nhập bằng số điện thoại</p>
          </div>
          <button type="button" :disabled="saving" aria-label="Đóng" @click="close">
            ✕
          </button>
        </header>
        <template v-if="['edit', 'create'].includes(selection.kind)"
          ><label
            >Họ tên<input
              v-model="selection.form.fullName"
              required
              maxlength="255"
              :disabled="saving" /></label
          ><label
            >Số điện thoại<input
              v-model="selection.form.phone"
              type="tel"
              maxlength="30"
              :required="selection.user.authProvider === 'PHONE'"
              :disabled="saving" /></label
          ><label
            >Email<input
              v-model="selection.form.email"
              type="email"
              maxlength="255"
              :disabled="saving || selection.user.authProvider === 'GOOGLE'"
          /></label>
          <template v-if="selection.kind === 'create'">
            <label
              >Mật khẩu
              <input
                v-model="selection.form.password"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                maxlength="72"
                :disabled="saving"
              />
            </label>
            <label
              >Xác nhận mật khẩu
              <input
                v-model="selection.form.confirmPassword"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                maxlength="72"
                :disabled="saving"
              />
            </label>
            <p class="hint">
              Email là thông tin liên hệ. Số điện thoại và email chưa được xác minh.
            </p>
          </template>
          <p class="hint" v-if="selection.user.authProvider === 'GOOGLE'">
            Email được giữ theo tài khoản Google đăng nhập.
          </p></template
        >
        <template v-else-if="selection.kind === 'detail'"
          ><p>
            <span class="badge" :class="selection.user.status">{{
              labels[selection.user.status]
            }}</span>
          </p>
          <p class="hint">
            {{ selection.user.phone || 'Chưa có số điện thoại' }} ·
            {{ selection.user.email || 'Chưa có email' }}
          </p>
          <h4>Lịch sử vi phạm booking</h4>
          <p v-if="historyLoading" class="hint">Đang tải…</p>
          <p v-else-if="historyError" class="message error" role="alert">
            {{ historyError }}
            <button type="button" @click="loadHistory(selection.user.id)">Tải lại</button>
          </p>
          <div v-else class="history">
            <article v-for="v in violations" :key="v.id">
              <strong
                >{{ v.violationType === 'NO_SHOW' ? 'Không đến sân' : v.violationType }} ·
                Booking #{{ v.bookingId }}</strong
              ><small
                >{{ dates(v.createdAt) }} ·
                {{ labels[v.userStatusAfter] || v.userStatusAfter }}</small
              >
            </article>
            <p v-if="!violations.length" class="hint">Chưa có bản ghi vi phạm booking.</p>
          </div>
          <p class="hint">
            Duyệt mở lại giữ lịch sử vi phạm. Trạng thái hiện tại có thể khác trạng thái
            tại thời điểm vi phạm.
          </p></template
        >
        <p v-else-if="selection.kind === 'suspend'" class="callout">
          Khóa tài khoản sẽ chuyển sang SUSPENDED. Tài khoản không thể đăng nhập hoặc tiếp
          tục gọi API bằng JWT đang có. Lịch sử và giao dịch được giữ nguyên.
        </p>
        <p v-else-if="selection.kind === 'delete'" class="callout">
          Xóa tài khoản này? Chỉ xóa được khi chưa có booking, giao dịch hoặc dữ liệu liên
          quan. Nếu đã có lịch sử, hãy dùng nút Khóa.
        </p>
        <p v-else class="callout">
          Admin xác nhận đã xem xét và cho phép tài khoản hoạt động trở lại? Trạng thái sẽ
          chuyển từ {{ labels[selection.user.status] }} sang ACTIVE. Lịch sử vi phạm được
          giữ nguyên; các booking đã hủy không tự khôi phục.
        </p>
        <p v-if="formError" class="message error" role="alert">{{ formError }}</p>
        <footer class="actions">
          <button type="button" :disabled="saving" @click="close">Đóng</button
          ><button
            v-if="selection.kind !== 'detail'"
            :class="['suspend', 'delete'].includes(selection.kind) ? 'danger' : 'primary'"
            :disabled="saving"
          >
            {{
              saving
                ? 'Đang lưu…'
                : selection.kind === 'create'
                  ? selection.user.role === 'STAFF'
                    ? 'Thêm nhân viên'
                    : 'Thêm người dùng'
                  : selection.kind === 'create'
                    ? 'Tạo tài khoản'
                    : selection.kind === 'edit'
                      ? 'Lưu thay đổi'
                      : selection.kind === 'delete'
                        ? 'Xác nhận xóa'
                        : selection.kind === 'suspend'
                          ? 'Xác nhận khóa'
                          : 'Duyệt về ACTIVE'
            }}</button
          ><button
            v-else-if="['WARNING', 'SUSPENDED'].includes(selection.user.status)"
            type="button"
            class="primary"
            @click="open(selection.user, 'activate')"
          >
            Duyệt mở lại
          </button>
        </footer>
      </form>
    </dialog>
  </section>
</template>
<style scoped>
.users-panel {
  background: white;
  border: 1px solid #e1eae4;
  border-radius: 14px;
  padding: 22px;
  margin-bottom: 22px;
  color: #234732;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}
h2,
h3,
h4 {
  margin: 0 0 8px;
}
h2 {
  font-size: 19px;
}
h3 {
  font-size: 20px;
}
header p,
.hint {
  font-size: 12px;
  color: #708276;
  line-height: 1.8;
  margin: 0;
}
.hint {
  margin: 12px 0;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 18px 0;
}
.metrics article {
  border: 1px solid #e1eae4;
  border-radius: 10px;
  padding: 16px;
}
.metrics strong {
  display: block;
  font-size: 27px;
  margin-top: 8px;
  color: #176c46;
}
small {
  display: block;
  font-size: 11px;
  color: #819589;
  line-height: 1.7;
}
.filters,
.actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.filters {
  margin: 20px 0;
}
.filters input {
  flex: 1;
  min-width: 180px;
}
input,
select {
  font: inherit;
  font-size: 13px;
  border: 1px solid #dce7df;
  border-radius: 8px;
  padding: 10px;
  color: #244e36;
  background: white;
  min-width: 0;
}
button {
  font: inherit;
  font-size: 12px;
  border: 1px solid #dce7df;
  border-radius: 8px;
  padding: 9px 12px;
  background: white;
  color: #245239;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.primary {
  background: #176c46;
  border-color: #176c46;
  color: white;
}
.danger {
  color: #a34d35;
  border-color: #e8c7bc;
  background: #fff8f4;
}
.table-wrap {
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
  text-align: left;
  font-size: 12px;
}
th {
  padding: 12px;
  background: #f4f8f5;
  font-size: 11px;
  color: #768b7c;
}
td {
  padding: 15px 12px;
  border-bottom: 1px solid #edf2ee;
}
.badge {
  display: inline-block;
  border-radius: 6px;
  padding: 5px 8px;
  font-size: 11px;
  font-weight: 650;
}
.ACTIVE {
  background: #eaf6ee;
  color: #287546;
}
.WARNING {
  background: #fff3d8;
  color: #976521;
}
.SUSPENDED {
  background: #fff0e9;
  color: #a45136;
}
.empty {
  text-align: center;
  padding: 35px;
  color: #829789;
}
.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  font-size: 12px;
  margin-top: 16px;
}
.pagination > span:first-child {
  margin-right: auto;
}
.message {
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 1.7;
  margin: 12px 0;
}
.error {
  background: #fff0ec;
  color: #a44d30;
}
.success {
  background: #e7f5ed;
  color: #287749;
}
.modal {
  border: 0;
  border-radius: 16px;
  padding: 24px;
  width: min(570px, calc(100vw - 28px));
  max-height: 85vh;
  overflow: auto;
  color: #234732;
  box-shadow: 0 20px 80px #17392340;
}
.modal::backdrop {
  background: #12302170;
}
.modal label {
  display: grid;
  gap: 7px;
  margin: 16px 0;
  font-size: 12px;
}
.modal footer {
  justify-content: flex-end;
  margin-top: 24px;
}
.callout {
  font-size: 13px;
  line-height: 1.9;
  background: #f5f8ef;
  border-radius: 10px;
  padding: 16px;
}
.history article {
  padding: 14px 0;
  border-bottom: 1px solid #edf2ee;
  font-size: 12px;
}
input:focus,
select:focus {
  outline: 2px solid #9fc899;
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .users-panel {
    padding: 15px;
  }
  .metrics {
    gap: 6px;
  }
  .metrics article {
    padding: 12px 8px;
  }
  .metrics small {
    font-size: 10px;
  }
  .filters select {
    width: 100%;
  }
}
.account-actions {
  display: grid;
  grid-template-columns: 80px 70px 70px 125px;
  gap: 8px;
  align-items: center;
}
.account-actions button {
  width: 100%;
  height: 38px;
  padding: 0 10px;
  white-space: nowrap;
}
</style>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { getAdminData } from '../services/adminService'
const props = defineProps({ auth: { type: Object, required: true } })
const emit = defineEmits(['session-expired'])
const courts = ref([]), rooms = ref([]), prices = ref([]), types = ref([])
const loading = ref(false), saving = ref(false), error = ref(''), success = ref('')
const query = ref(''), status = ref('all'), page = ref(1)
const editor = ref(null), confirmAction = ref(null)
const courtDialog = ref(null), confirmDialog = ref(null)
watch(editor, async value => { await nextTick(); if(value && courtDialog.value && !courtDialog.value.open) courtDialog.value.showModal(); else if(!value) courtDialog.value?.close() })
watch(confirmAction, async value => { await nextTick(); if(value && confirmDialog.value && !confirmDialog.value.open) confirmDialog.value.showModal(); else if(!value) confirmDialog.value?.close() })
const form = ref({})
const selectedType = ref('')
const roomMode = ref('existing')
const explicitGroup = ref('')
const newRoomName = ref('')
const typeOptions = computed(() => types.value)
const selectedRoom = computed(() => rooms.value.find(r => r.id === Number(form.value.roomId)))
const availableRooms = computed(() => rooms.value.filter(r => r.courtTypeId === Number(selectedType.value)))
const selectedTypeName = computed(() => types.value.find(t=>t.id===Number(selectedType.value))?.name || '')
const typeKey = computed(() => selectedTypeName.value.toUpperCase().replace(/[^A-Z0-9]/g,''))
const allowsNewPrivate = computed(() => typeKey.value.includes('PREMIUM') && !typeKey.value.includes('SHARED'))
const groupOptions = computed(() => typeKey.value.includes('PREMIUM') ? typeKey.value.includes('PRIVATE') ? ['PREMIUM_PRIVATE'] : typeKey.value.includes('SHARED') ? ['PREMIUM_SHARED'] : ['PREMIUM_SHARED','PREMIUM_PRIVATE'] : typeKey.value.includes('GOLD') ? ['GOLD'] : typeKey.value.includes('BASIC') ? ['BASIC'] : /MISTFAN|DAILYVISITOR/.test(typeKey.value) ? ['DAILY_VISITOR'] : [])
const chosenGroup = computed(() => roomMode.value==='new' ? 'PREMIUM_PRIVATE' : selectedRoom.value?.roomGroup==='UNCONFIGURED' ? explicitGroup.value : selectedRoom.value?.roomGroup)
const isDaily = computed(() => chosenGroup.value === 'DAILY_VISITOR')
const groups = {PREMIUM_SHARED:'Premium Shared',PREMIUM_PRIVATE:'Premium Private',GOLD:'Gold',BASIC:'Basic',DAILY_VISITOR:'Mist_fan / DailyVisitor',UNCONFIGURED:'Chưa cấu hình'}
function changeType(){ roomMode.value='existing';form.value.roomId='';explicitGroup.value='';form.value.dailySchedule=null }
function changeRoom(){ form.value.dailySchedule=isDaily.value ? {skillLevel:'',startTime:'',endTime:'',fixedFee:'',minParticipants:6,maxParticipants:8} : null }
const canAdd = computed(() => types.value.some(t=>t.active))
const priceDraft = ref({})
let controller = new AbortController()
const filtered = computed(() => courts.value.filter(c => `${c.name} ${c.roomName} ${c.courtTypeName}`.toLowerCase().includes(query.value.toLowerCase()) && (status.value === 'all' || String(c.active) === status.value)))
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10)))
const visible = computed(() => filtered.value.slice((Math.min(page.value, pageCount.value) - 1) * 10, Math.min(page.value, pageCount.value) * 10))
const money = n => new Intl.NumberFormat('vi-VN').format(n) + ' ₫'
function report(e) {
  error.value = e.name === 'AbortError' ? 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.' : e instanceof TypeError ? 'Không kết nối được backend. Kiểm tra máy chủ và CORS.' : e.message
  if (e.status === 401) emit('session-expired')
}
async function request(path, method = 'GET', body) {
  const timeout = window.setTimeout(() => controller.abort(), 15000)
  try { return await getAdminData(path, props.auth.accessToken, controller.signal, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }) }
  finally { window.clearTimeout(timeout) }
}
async function load() {
  if (loading.value || saving.value) return
  controller = new AbortController(); loading.value = true; error.value = ''
  const tasks = [['courts', '/api/admin/courts'], ['rooms', '/api/admin/courts/rooms'], ['prices', '/api/admin/prices/courts'], ['types','/api/admin/courts/types']]
  const results = await Promise.allSettled(tasks.map(async ([key,path]) => [key,await request(path)]))
  results.forEach(r => { if(r.status === 'rejected') report(r.reason); else { const [key,value] = r.value; if(key === 'courts') courts.value=value; if(key === 'rooms') rooms.value=value; if(key === 'types') types.value=value; if(key === 'prices') {prices.value=value; priceDraft.value=Object.fromEntries(value.map(p => [p.id,{normalPricePerHour:p.normalPricePerHour,peakPricePerHour:p.peakPricePerHour}]))} } })
  loading.value = false
}
function openEditor(court) {
  error.value = ''; success.value = ''
  form.value = court ? { ...court, nextMaintenanceAt: court.nextMaintenanceAt?.slice(0,16) || '' } : { name:'', roomId:'', active:true, maintenanceIntervalMonths:'', nextMaintenanceAt:'' }
  selectedType.value = court ? rooms.value.find(r=>r.id===court.roomId)?.courtTypeId || '' : ''
  roomMode.value='existing';explicitGroup.value='';newRoomName.value=''
  editor.value = court ? 'edit' : 'create'
}
async function saveCourt() {
  if (saving.value) return
  if (!form.value.name?.trim()) {error.value='Nhập tên sân.';return}
  if(roomMode.value==='new') {
    if(!allowsNewPrivate.value || !newRoomName.value.trim()){error.value='Chọn loại Premium phù hợp và nhập tên phòng Private mới.';return}
  } else {
    if(!form.value.roomId){error.value='Chọn phòng sân.';return}
    if(editor.value==='create' && (!selectedRoom.value?.active || !chosenGroup.value || chosenGroup.value==='UNCONFIGURED')){error.value='Chọn phòng hoạt động và cấu trúc phòng phù hợp.';return}
    if(editor.value==='create' && chosenGroup.value==='PREMIUM_PRIVATE' && selectedRoom.value.courtCount>=1){error.value='Private mỗi phòng chỉ có một sân. Hãy tạo phòng mới.';return}
  }
  if(editor.value==='create' && isDaily.value){const d=form.value.dailySchedule;if(!d || !d.skillLevel || !d.startTime || !d.endTime || d.endTime<=d.startTime || ![d.fixedFee,d.minParticipants,d.maxParticipants].every(v=>Number.isSafeInteger(v)&&v>0) || d.minParticipants>d.maxParticipants){error.value='Kiểm tra trình độ, giờ, phí và số người của lịch vãng lai.';return}}
  saving.value=true;error.value='';controller = new AbortController()
  try {
    const body={name:form.value.name.trim(),...(roomMode.value==='new' ? {newRoom:{name:newRoomName.value.trim(),courtTypeId:Number(selectedType.value),roomGroup:'PREMIUM_PRIVATE'}} : {roomId:Number(form.value.roomId),...(explicitGroup.value ? {roomGroup:explicitGroup.value} : {})}),active:form.value.active,maintenanceIntervalMonths:form.value.maintenanceIntervalMonths === '' || form.value.maintenanceIntervalMonths == null ? null : Number(form.value.maintenanceIntervalMonths),nextMaintenanceAt:form.value.nextMaintenanceAt || null,...(editor.value==='create' && isDaily.value ? {dailySchedule:form.value.dailySchedule} : {})}
    const result=await request(editor.value==='create'?'/api/admin/courts':`/api/admin/courts/${form.value.id}`,editor.value==='create'?'POST':'PUT',body)
    const index=courts.value.findIndex(c=>c.id===result.id);if(index<0){courts.value.push(result);const r=rooms.value.find(r=>r.id===result.roomId);if(r)r.courtCount++}else courts.value[index]=result
    editor.value=null;success.value='Đã lưu thông tin sân.'
    rooms.value=await request('/api/admin/courts/rooms')
  }catch(e){report(e)}finally{saving.value=false}
}
function askPrice(p) {
  const draft=priceDraft.value[p.id]
  if (![draft.normalPricePerHour,draft.peakPricePerHour].every(v => Number.isSafeInteger(Number(v)) && Number(v)>0)) {error.value='Giá phải là số nguyên lớn hơn 0.';return}
  confirmAction.value={kind:'price',item:p,body:{normalPricePerHour:Number(draft.normalPricePerHour),peakPricePerHour:Number(draft.peakPricePerHour)}}
}
async function confirmSave() {
  if(saving.value || !confirmAction.value)return
  saving.value=true;error.value='';success.value='';controller=new AbortController()
  const action=confirmAction.value
  try {
    if(action.kind==='active') {
      const result=await request(`/api/admin/courts/${action.item.id}/active`,'PATCH',{active:!action.item.active});courts.value[courts.value.findIndex(c=>c.id===result.id)]=result
      success.value=`Đã ${result.active?'bật':'tắt'} sân ${result.name}.`
    } else if(action.kind==='delete') {
      await request(`/api/admin/courts/${action.item.id}`,'DELETE')
      courts.value=courts.value.filter(c=>c.id!==action.item.id)
      const r=rooms.value.find(r=>r.id===action.item.roomId);if(r)r.courtCount--
      success.value='Đã xóa sân.'
    } else {
      const result=await request(`/api/admin/prices/courts/${action.item.id}`,'PUT',action.body);prices.value[prices.value.findIndex(p=>p.id===result.id)]=result
      success.value='Đã cập nhật bảng giá.'
    }
    confirmAction.value=null
  }catch(e){report(e)}finally{saving.value=false}
}
onMounted(load)
onUnmounted(()=>controller.abort())
</script>
<template>
  <div class="management">
    <p v-if="error" class="message error" role="alert">{{ error }}</p><p v-if="success" class="message success" role="status">{{ success }}</p>
    <section class="card"><header><div><h2>Danh sách sân</h2><p>{{ courts.length }} sân · {{ courts.filter(c => c.active).length }} đang hoạt động</p></div><div class="actions"><button :disabled="loading || saving" @click="load">↻ Tải lại</button><button class="primary" :disabled="saving || loading || !canAdd" @click="openEditor(null)">+ Thêm sân</button></div></header>
    <div class="filters"><input v-model="query" @input="page=1" placeholder="Tìm sân, phòng, loại sân…" aria-label="Tìm sân" /><select v-model="status" @change="page=1" aria-label="Lọc trạng thái"><option value="all">Tất cả trạng thái</option><option value="true">Đang hoạt động</option><option value="false">Ngừng hoạt động</option></select></div>
    <p class="hint" v-if="!loading && !canAdd">Chưa có loại sân hoạt động hoặc chưa tải được dữ liệu loại sân.</p>
    <div class="table-wrap"><table><thead><tr><th>Sân</th><th>Phòng / loại sân</th><th>Trạng thái</th><th>Chu kỳ bảo trì</th><th>Thao tác</th></tr></thead><tbody><tr v-for="court in visible" :key="court.id"><td><strong>{{ court.name }}</strong></td><td>{{ court.roomName }}<small>{{ court.courtTypeName }} · {{groups[court.roomGroup]}}</small><small v-for="d in court.dailySchedules" :key="d.id">{{d.skillLevel==='TBY'?'TB− (TBY)':d.skillLevel}} · {{d.startTime}}–{{d.endTime}}</small></td><td><span class="badge" :class="{ off: !court.active }">{{ court.active?'Hoạt động':'Ngừng hoạt động' }}</span></td><td>{{ court.maintenanceIntervalMonths ? `${court.maintenanceIntervalMonths} tháng` : 'Chưa đặt' }}</td><td class="actions"><button :disabled="saving" @click="openEditor(court)">Sửa</button><button :disabled="saving" @click="confirmAction={kind:'active',item:court}">{{ court.active?'Tắt sân':'Bật sân' }}</button><button :disabled="saving" @click="confirmAction={kind:'delete',item:court}">Xóa</button></td></tr><tr v-if="!visible.length"><td colspan="5" class="empty">{{loading?'Đang tải…':'Chưa có sân phù hợp hoặc dữ liệu chưa tải được.'}}</td></tr></tbody></table></div>
    <div class="pagination"><span>{{filtered.length}} kết quả</span><button :disabled="page<=1" @click="page=Math.min(page,pageCount)-1">Trước</button><span>{{Math.min(page,pageCount)}} / {{pageCount}}</span><button :disabled="page>=pageCount" @click="page++">Sau</button></div></section>
    <section class="card"><header><div><h2>Bảng giá theo loại sân</h2><p>Giá VND / giờ · Thay đổi áp dụng chung cho loại sân tương ứng.</p></div></header><div class="table-wrap"><table><thead><tr><th>Loại sân</th><th>Giờ mở / cao điểm / đóng</th><th>Giờ thường</th><th>Giờ cao điểm</th><th>Thao tác</th></tr></thead><tbody><tr v-for="price in prices" :key="price.id"><td><strong>{{price.courtTypeName}}</strong><small>{{price.active?'Đang áp dụng':'Ngừng áp dụng'}}</small></td><td>{{price.openingTime?.slice(0,5)}} / {{price.peakStartTime?.slice(0,5)}} / {{price.closingTime?.slice(0,5)}}</td><td><input v-model.number="priceDraft[price.id].normalPricePerHour" type="number" min="1" step="1" :aria-label="`Giá thường ${price.courtTypeName}`" :disabled="saving" /></td><td><input v-model.number="priceDraft[price.id].peakPricePerHour" type="number" min="1" step="1" :aria-label="`Giá cao điểm ${price.courtTypeName}`" :disabled="saving" /></td><td><button class="primary" :disabled="saving" @click="askPrice(price)">Lưu giá</button></td></tr><tr v-if="!prices.length"><td colspan="5" class="empty">{{loading?'Đang tải…':'Chưa có bảng giá hoặc dữ liệu chưa tải được.'}}</td></tr></tbody></table></div></section>
    <dialog ref="courtDialog" @cancel.prevent="!saving && (editor=null)" class="modal" aria-labelledby="court-editor-title" @keydown.esc.prevent="!saving && (editor=null)"><form @submit.prevent="saveCourt"><h2 id="court-editor-title">{{editor==='create'?'Thêm sân mới':'Sửa thông tin sân'}}</h2><p v-if="error" class="message error" role="alert">{{error}}</p><label>Tên sân<input v-model="form.name" required maxlength="255" :disabled="saving" /></label><label>Loại sân<select v-model="selectedType" required :disabled="saving || editor==='edit'" @change="changeType"><option value="" disabled>Chọn loại sân</option><option v-for="t in typeOptions" :key="t.id" :value="t.id" :disabled="!t.active && editor==='create'">{{t.name}}</option></select></label>
<label v-if="editor==='create' && allowsNewPrivate">Phòng cho sân mới<select v-model="roomMode" :disabled="saving" @change="form.roomId='';explicitGroup='';form.dailySchedule=null"><option value="existing">Chọn phòng có sẵn</option><option value="new">Tạo phòng Private mới + 1 sân</option></select></label>
<label v-if="roomMode==='new'">Tên phòng Private mới<input v-model="newRoomName" required maxlength="255" placeholder="Ví dụ: P1-02" :disabled="saving" /></label>
<p v-if="roomMode==='new'" class="hint">Phòng mới và sân được tạo cùng một lần lưu. Tên phòng không quyết định loại phòng.</p>
<label v-if="roomMode==='existing'">Phòng sân<select v-model="form.roomId" required :disabled="saving || editor==='edit'" @change="changeRoom"><option value="" disabled>Chọn phòng sân</option><option v-for="room in availableRooms" :key="room.id" :value="room.id" :disabled="editor==='create' && (!room.active || (room.roomGroup==='PREMIUM_PRIVATE' && room.courtCount>=1))">{{room.name}} — {{groups[room.roomGroup]}} ({{room.courtCount}} sân{{room.roomGroup==='PREMIUM_PRIVATE' ? ' / tối đa 1' : ''}})</option></select></label>
<label v-if="editor==='create' && roomMode==='existing' && selectedRoom?.roomGroup==='UNCONFIGURED'">Cấu trúc phòng<select v-model="explicitGroup" required :disabled="saving" @change="changeRoom"><option value="" disabled>Chọn cấu trúc phòng</option><option v-for="g in groupOptions" :key="g" :value="g">{{groups[g]}}</option></select></label>
<fieldset v-if="editor==='create' && isDaily && form.dailySchedule" :disabled="saving"><legend>Lịch vãng lai cố định của sân mới</legend>
<label>Trình độ<select v-model="form.dailySchedule.skillLevel" required><option value="" disabled>Chọn trình độ</option><option value="TBY">TB− (mã TBY)</option><option value="TB">TB</option><option value="TB+">TB+</option></select></label>
<label>Giờ mở<input v-model="form.dailySchedule.startTime" type="time" required /></label><label>Giờ kết thúc<input v-model="form.dailySchedule.endTime" type="time" required /></label>
<label>Phí cố định / người<input v-model.number="form.dailySchedule.fixedFee" type="number" min="1" step="1" required /></label>
<label>Số người tối thiểu<input v-model.number="form.dailySchedule.minParticipants" type="number" min="1" step="1" required /></label><label>Số người tối đa<input v-model.number="form.dailySchedule.maxParticipants" type="number" :min="form.dailySchedule.minParticipants || 1" step="1" required /></label>
<p class="hint">Sân và lịch được lưu cùng nhau. Sửa tên sân không thay đổi các lịch hoặc buổi chơi đã tạo.</p></fieldset><p class="hint" v-if="editor==='edit'">Phòng sân được giữ cố định sau khi tạo.</p><label>Chu kỳ bảo trì (tháng)<input v-model="form.maintenanceIntervalMonths" type="number" min="1" max="120" step="1" :disabled="saving" /></label><label>Bảo trì tiếp theo<input v-model="form.nextMaintenanceAt" type="datetime-local" :disabled="saving" /></label><label class="check"><input v-model="form.active" type="checkbox" :disabled="saving" /> Sân hoạt động</label><div class="actions"><button type="button" :disabled="saving" @click="editor=null">Hủy</button><button class="primary" :disabled="saving">{{saving?'Đang lưu…':'Lưu sân'}}</button></div></form></dialog>
    <dialog ref="confirmDialog" @cancel.prevent="!saving && (confirmAction=null)" class="modal" aria-labelledby="confirm-title" @keydown.esc.prevent="!saving && (confirmAction=null)"><h2 id="confirm-title">Xác nhận thay đổi</h2><template v-if="confirmAction?.kind==='active'"><p>{{confirmAction.item.active?'Tắt':'Bật'}} sân <strong>{{confirmAction.item.name}}</strong>?</p><p class="hint">Tắt sân sẽ ngăn đặt mới. Lịch đã đặt không tự động bị hủy.</p></template><template v-else-if="confirmAction?.kind==='delete'"><p>Xóa sân <strong>{{confirmAction.item.name}}</strong>?</p><p class="hint">Chỉ xóa sân chưa có dữ liệu liên quan. Sân đã sử dụng cần tắt hoạt động.</p></template><template v-else-if="confirmAction"><p>Cập nhật giá {{confirmAction.item.courtTypeName}}?</p><p>Giờ thường: {{money(confirmAction.body.normalPricePerHour)}}<br />Cao điểm: {{money(confirmAction.body.peakPricePerHour)}}</p></template><p v-if="error" class="message error" role="alert">{{error}}</p><div class="actions"><button :disabled="saving" @click="confirmAction=null">Hủy</button><button class="primary" :disabled="saving" @click="confirmSave">{{saving?'Đang lưu…':'Xác nhận'}}</button></div></dialog>

  </div>
</template>
<style scoped>
.management{display:grid;gap:22px}.card{background:white;border:1px solid #e1eae4;border-radius:14px;padding:22px;min-width:0}header{display:flex;align-items:center;justify-content:space-between;gap:14px;flex-wrap:wrap;margin-bottom:20px}h2{font-size:17px;margin:0 0 8px}header p,.hint{font-size:12px;color:#708276;margin:0}.actions{display:flex;gap:8px;align-items:center}button{border:1px solid #dce7df;padding:8px 12px;border-radius:7px;background:white;color:#245239;font-size:12px}.primary{background:#005b35;color:white;border-color:#005b35}button:disabled{opacity:.5;cursor:default}.filters{display:flex;gap:12px;margin-bottom:18px;flex-wrap:wrap}input,select{border:1px solid #dce7df;border-radius:7px;padding:9px 11px;background:white;color:#244e36;max-width:100%;font:inherit;font-size:13px}.filters input{flex:1;min-width:160px}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;white-space:nowrap;font-size:13px}th{font-size:11px;color:#768b7c;background:#f4f8f5;text-align:left;padding:12px}td{padding:14px 12px;border-bottom:1px solid #edf2ee}td input{width:140px}td small{display:block;font-size:11px;color:#8b9b91}.badge{display:inline-block;padding:4px 9px;border-radius:6px;background:#eaf6ee;color:#287546;font-size:11px}.off{background:#fff1e9;color:#a76942}.empty{text-align:center;padding:35px;color:#829789}.pagination{display:flex;align-items:center;justify-content:flex-end;gap:10px;font-size:12px;padding-top:16px}.pagination>span:first-child{margin-right:auto}.message{padding:12px 16px;border-radius:8px;font-size:13px;margin:0}.error{background:#fff0ec;color:#a44d30}.success{background:#e7f5ed;color:#287749}.backdrop{position:fixed;inset:0;background:#12302170;z-index:70}.modal{position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);margin:0;width:min(460px,calc(100vw - 28px));max-height:85vh;overflow:auto;border:0;border-radius:15px;padding:25px;z-index:71;box-shadow:0 20px 90px #102b2540;color:#234732}.modal::backdrop{background:#12302170}.modal:not([open]){display:none}.modal label{display:grid;gap:6px;margin:14px 0;font-size:12px}.modal .check{display:flex;align-items:center}.modal .actions{justify-content:flex-end;margin-top:20px}.modal .hint{line-height:1.7}.modal .message{margin:10px 0}@media(max-width:600px){.card{padding:14px}.filters{flex-direction:column}.pagination{gap:6px}}
</style>

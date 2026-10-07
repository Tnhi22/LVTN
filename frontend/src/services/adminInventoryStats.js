export function vietnamDay(value = Date.now()) {
  const date = typeof value === 'string' ? new Date(/[Zz]|[+-]\d\d:\d\d$/.test(value)?value:`${value}+07:00`) : new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(date)
  return ['year','month','day'].map(t=>parts.find(p=>p.type===t).value).join('-')
}
export function periodRange(preset, currentDay = vietnamDay()) {
  if (preset === 'today') return {from:currentDay,to:currentDay}
  if (preset === 'month') return {from:currentDay.slice(0,8)+'01',to:currentDay}
  const date = new Date(`${currentDay}T12:00:00+07:00`)
  const daysSinceMonday = (date.getUTCDay()+6)%7
  date.setUTCDate(date.getUTCDate()-daysSinceMonday)
  return {from:vietnamDay(date.getTime()),to:currentDay}
}
export const isReceived = b => (b.status || 'RECEIVED') === 'RECEIVED'
export function receivedInRange(b, range) {
  const day = b.receivedAt ? vietnamDay(b.receivedAt) : ''
  return isReceived(b) && !!day && !!range.from && !!range.to && range.from<=range.to && day>=range.from && day<=range.to
}
export const supplierKey = b => b.supplier?.id ?? 'unassigned'
export function supplierStatistics(suppliers, batches, range) {
  const map = new Map(suppliers.map(s=>[s.id,{...s,count:0,tubes:0,amount:0,pendingCount:0,pendingAmount:0}]))
  for (const b of batches) {
    const key=supplierKey(b)
    if (!map.has(key)) map.set(key,{...(b.supplier||{id:'unassigned',name:'Lô chưa có nhà cung cấp'}),count:0,tubes:0,amount:0,pendingCount:0,pendingAmount:0})
    const row=map.get(key)
    if (receivedInRange(b,range)) {
      row.count++;row.tubes+=Number(b.quantityReceivedTubes||0)
      row.amount+=Number(b.quantityReceivedTubes||0)*Number(b.importPricePerTube||0)
    }
    if (b.status==='PENDING') {
      row.pendingCount++;row.pendingAmount+=Number(b.quantityOrderedTubes||0)*Number(b.importPricePerTube||0)
    }
  }
  return [...map.values()].sort((a,b)=>b.amount-a.amount||a.name.localeCompare(b.name,'vi'))
}

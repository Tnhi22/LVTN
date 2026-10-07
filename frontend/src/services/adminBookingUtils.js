export const bookingStatuses = { PENDING:'Chờ nhận sân', CHECKED_IN:'Đang chơi', COMPLETED:'Hoàn thành', CANCELLED:'Đã hủy', NO_SHOW:'Không đến' }
export const customerName = b => b.user?.fullName || b.visitor?.fullName || 'Chưa có tên'
export const customerPhone = b => b.user?.phone || b.visitor?.phone || ''
export const typeName = b => b.court?.room?.courtType?.name || ''
export const bookingTime = (b, end = false) => {
  const time = end ? b.endTime : b.startTime
  return b.bookingDate && time ? Date.parse(`${b.bookingDate}T${time}+07:00`) : NaN
}
export function actionReason(b, action, now = Date.now()) {
  if (!b) return 'Chọn lịch đặt sân.'
  if (action === 'check-in') {
    if (b.status !== 'PENDING') return 'Chỉ nhận sân cho lịch đang chờ.'
    const start = bookingTime(b)
    if (!Number.isFinite(start)) return 'Thiếu ngày hoặc giờ đặt sân.'
    if (now < start) return 'Chưa đến giờ nhận sân.'
    if (now >= start + 30 * 60000) return 'Đã quá 30 phút nhận sân.'
  } else if (action === 'complete') {
    if (b.status !== 'CHECKED_IN') return 'Chỉ hoàn thành lịch đang chơi.'
    const end = bookingTime(b, true)
    if (!Number.isFinite(end)) return 'Thiếu ngày hoặc giờ kết thúc.'
    if (now < end) return 'Chưa hết giờ chơi.'
  } else if (action === 'settle') {
    if (b.status !== 'COMPLETED') return 'Hoàn thành sân trước khi thu tiền.'
    if (b.paidAt) return 'Đã ghi nhận thanh toán.'
    if (!Number.isSafeInteger(b.totalAmount) || b.totalAmount < 0) return 'Chưa có tổng tiền hợp lệ.'
  } else if (action === 'cancel') {
    if (!b.visitor) return 'API hiện tại chỉ cho ADMIN hủy booking khách tại quầy.'
    if (b.status !== 'PENDING') return 'Chỉ hủy lịch khách tại quầy đang chờ.'
    const start = bookingTime(b)
    if (!Number.isFinite(start) || start - now < 120 * 60000) return 'Chỉ hủy trước giờ chơi ít nhất 2 tiếng.'
  } else return 'Thao tác không hợp lệ.'
  return ''
}
export function filterBookings(rows, filters) {
  const query = (filters.query || '').trim().toLocaleLowerCase('vi')
  return rows.filter(b => {
    const text = [b.id,customerName(b),customerPhone(b),b.court?.name,b.court?.room?.name,typeName(b)].join(' ').toLocaleLowerCase('vi')
    return (!query || text.includes(query)) && (!filters.from || b.bookingDate >= filters.from) && (!filters.to || b.bookingDate <= filters.to)
      && (!filters.status || b.status === filters.status) && (!filters.type || typeName(b) === filters.type)
      && (!filters.source || (filters.source === 'walk-in' ? !!b.visitor : !!b.user))
      && (!filters.payment || (filters.payment === 'paid' ? !!b.paidAt : !b.paidAt && !['CANCELLED','NO_SHOW'].includes(b.status)))
  }).sort((a,b) => `${b.bookingDate}T${b.startTime}`.localeCompare(`${a.bookingDate}T${a.startTime}`) || b.id-a.id)
}
export function settlementBody(method, amount, total) {
  const received = Number(amount)
  if (!['CASH','BANK_TRANSFER'].includes(method) || amount === '' || amount == null || !Number.isSafeInteger(received) || received < 0 || !Number.isSafeInteger(total) || total < 0 || received < total || (method === 'BANK_TRANSFER' && received !== total)) {
    throw new Error('Tiền mặt phải đủ tổng hóa đơn; chuyển khoản phải bằng đúng tổng hóa đơn. Số tiền phải là số nguyên.')
  }
  return {paymentMethod:method,amountReceived:received}
}

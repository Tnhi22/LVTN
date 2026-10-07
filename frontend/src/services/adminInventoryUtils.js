export const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '')
export function imageSource(value) {
  const path = String(value || '').trim()
  if (!path) return ''
  if (/^https?:\/\//i.test(path)) return path
  if (path.startsWith('/uploads/')) return `${API_BASE}${path}`
  if (path.startsWith('/images/')) return path
  return ''
}
export const available = p => Math.max(0, (p.stockQuantityTubes || 0) - (p.reservedQuantityTubes || 0))
export function stockState(p) {
  const count = available(p)
  return count === 0 ? 'OUT' : count <= (p.minimumStockTubes ?? 20) ? 'LOW' : 'OK'
}
export const money = n => n == null ? '—' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(n)
function integer(value, label, minimum = 1) {
  if (value === '' || value == null || !Number.isSafeInteger(Number(value)) || Number(value) < minimum) throw new Error(`${label} phải là số nguyên ${minimum ? 'lớn hơn 0' : 'không âm'}.`)
  return Number(value)
}
export function productBody(f, existing) {
  const detail = String(f.detail || '').trim()
  const name = f.name.trim(), brand = f.brand.trim(), imageUrl = f.imageUrl.trim()
  if (!name || !brand) throw new Error('Nhập tên sản phẩm và thương hiệu.')
  if (!imageSource(imageUrl)) throw new Error('Ảnh cần là URL http/https, /images/... hoặc /uploads/products/...')
  const piecesPerTube = integer(f.piecesPerTube, 'Số quả/ống')
  if (existing && piecesPerTube !== existing.piecesPerTube) throw new Error('Không đổi quy cách sản phẩm đã tạo; hãy tạo sản phẩm mới.')
  const tubePrice = integer(f.tubePrice, 'Giá bán/ống')
  const piecePrice = f.piecePrice === '' || f.piecePrice == null ? null : integer(f.piecePrice, 'Giá bán/quả')
  const minimumStockTubes = integer(f.minimumStockTubes, 'Ngưỡng nhập thêm', 0)
  const targetStockTubes = integer(f.targetStockTubes, 'Tồn kho mục tiêu')
  if (targetStockTubes <= minimumStockTubes) throw new Error('Tồn kho mục tiêu phải lớn hơn ngưỡng nhập thêm.')
  return { name, brand, detail, imageUrl, piecesPerTube, tubePrice, piecePrice, minimumStockTubes, targetStockTubes }
}
export function batchBody(f) {
  const productId = integer(f.productId, 'Sản phẩm'), supplierId = integer(f.supplierId, 'Nhà cung cấp')
  const quantityTubes = integer(f.quantityTubes, 'Số ống nhập'), importPricePerTube = integer(f.importPricePerTube, 'Giá nhập/ống')
  if (!Number.isSafeInteger(quantityTubes * importPricePerTube)) throw new Error('Tổng giá trị lô hàng quá lớn.')
  return { productId, supplierId, quantityTubes, importPricePerTube }
}
export function saleBody(f, p) {
  if (!p || !p.active) throw new Error('Chọn sản phẩm đang bán.')
  if (!['TUBES', 'PIECES'].includes(f.unit)) throw new Error('Chọn đơn vị bán.')
  const quantity = integer(f.quantity, 'Số lượng bán')
  const price = f.unit === 'PIECES' ? p.piecePrice : p.tubePrice
  if (!Number.isSafeInteger(price) || price <= 0) throw new Error('Sản phẩm chưa có giá bán cho đơn vị này.')
  if (f.unit === 'TUBES' && quantity > available(p)) throw new Error('Số ống bán vượt tồn kho khả dụng.')
  if (!Number.isSafeInteger(price * quantity)) throw new Error('Tổng giá trị đơn bán quá lớn.')
  return { productId: p.id, [f.unit === 'PIECES' ? 'quantityPieces' : 'quantityTubes']: quantity }
}

export function validateProductImage(file) {
  if (!file) throw new Error('Vui lòng chọn ảnh.')
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) throw new Error('Chỉ hỗ trợ ảnh JPG, PNG hoặc WEBP.')
  if (file.size === 0 || file.size > 5 * 1024 * 1024) throw new Error('Ảnh phải có dữ liệu và không vượt quá 5 MB.')
}
export async function uploadProductImage(file, token, signal) {
  validateProductImage(file)
  const formData = new FormData()
  formData.append('image', file)
  const response = await fetch(`${API_BASE}/api/products/images`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: formData, signal
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(data?.message || (response.status === 404 ? 'Backend chưa có API tải ảnh /api/products/images.' : 'Không tải ảnh lên được.'))
    error.status = response.status
    throw error
  }
  if (!data?.imageUrl || !imageSource(data.imageUrl)) throw new Error('Backend chưa trả về đường dẫn ảnh hợp lệ.')
  return data.imageUrl
}

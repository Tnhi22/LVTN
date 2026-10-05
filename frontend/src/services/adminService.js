const BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/+$/, '')

export async function getAdminData(path, token, signal, options = {}) {
  const response = await fetch(`${BASE}${path}`, {
    ...options,
    headers: { Accept: 'application/json', 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    signal,
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(response.status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' : response.status === 403 ? 'Tài khoản không có quyền xem dữ liệu này.' : data?.message || `Không tải được dữ liệu (${response.status}).`)
    error.status = response.status
    throw error
  }
  if (data === null) throw new Error('Máy chủ trả về dữ liệu không hợp lệ.')
  return data
}

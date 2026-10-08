const BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(
  /\/+$/,
  '',
)

/** Public reads omit Authorization; private calls use the current session. */
export async function getCustomerData(path, token, signal, options = {}) {
  const headers = { Accept: 'application/json', 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${BASE}${path}`, { ...options, headers, signal })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      response.status === 401
        ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
        : data?.message ||
          (response.status === 403
            ? 'Tài khoản không có quyền thực hiện thao tác này.'
            : `Không thể xử lý yêu cầu (${response.status}).`)
    const error = new Error(message)
    error.status = response.status
    throw error
  }
  if (data === null) throw new Error('Máy chủ trả về dữ liệu không hợp lệ.')
  return data
}

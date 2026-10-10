const BASE = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
).replace(/\/+$/, '')

export function customerImageUrl(value) {
  if (!value) return ''

  if (/^https?:\/\//i.test(value)) {
    return value
  }

  return `${BASE}/${String(value).replace(/^\/+/, '')}`
}

export async function getCustomerData(
  path,
  token,
  signal,
  options = {},
) {
  const headers = {
    Accept: 'application/json',
  }

  // Khi gửi ảnh, trình duyệt tự tạo Content-Type multipart.
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE}${path}`, {
    ...options,
    headers,
    signal,
  })

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

  if (data === null) {
    throw new Error('Máy chủ trả về dữ liệu không hợp lệ.')
  }

  const method = (options.method || 'GET').toUpperCase()

  if (token && !['GET', 'HEAD'].includes(method)) {
    window.dispatchEvent(
      new CustomEvent('carrot:customer-updated'),
    )
  }

  return data
}
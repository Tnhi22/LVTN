const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
).replace(/\/+$/, '')

export function normalizePhone(value) {
  let phone = value.trim().replace(/[\s.-]/g, '')
  if (phone.startsWith('+84')) phone = `0${phone.slice(3)}`
  return phone
}

async function postAuth(path, payload) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), 15000)
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload),
      signal: controller.signal,
    })
    const data = await response.json().catch(() => null)
    if (!response.ok) {
      throw new Error(
        data?.message ||
          (response.status === 401
            ? 'Email, số điện thoại hoặc mật khẩu không đúng.'
            : 'Không thể xử lý yêu cầu. Vui lòng thử lại.'),
      )
    }
    if (!data) throw new Error('Máy chủ trả về dữ liệu không hợp lệ.')
    return data
  } catch (error) {
    if (error.name === 'AbortError')
      throw new Error('Máy chủ phản hồi quá lâu. Vui lòng thử lại.')
    if (error instanceof TypeError)
      throw new Error('Không kết nối được máy chủ. Kiểm tra backend và cấu hình CORS.')
    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }
}

function toSession(data) {
  if (
    !data?.accessToken ||
    data?.id == null ||
    !['CUSTOMER', 'STAFF', 'ADMIN'].includes(data.role)
  ) {
    throw new Error('Dữ liệu đăng nhập từ máy chủ không hợp lệ.')
  }
  return {
    accessToken: data.accessToken,
    tokenType: data.tokenType || 'Bearer',
    user: {
      id: data.id,
      fullName: data.fullName,
      email: data.email,
      phone: data.phone,
      role: data.role,
    },
  }
}

export async function loginWithIdentifier({ identifier, password }) {
  const trimmed = identifier.trim()
  const normalized = trimmed.includes('@')
    ? trimmed.toLowerCase()
    : normalizePhone(trimmed)
  return toSession(
    await postAuth('/api/auth/login', { identifier: normalized, password }),
  )
}

// Giữ hàm cũ cho những màn hình đang dùng đăng nhập bằng số điện thoại.
export async function loginByPhone({ phone, password }) {
  return toSession(
    await postAuth('/api/auth/login/phone', { phone: normalizePhone(phone), password }),
  )
}

export async function registerCustomer({
  fullName,
  phone,
  email,
  password,
  confirmPassword,
}) {
  const data = await postAuth('/api/auth/register', {
    fullName: fullName.trim(),
    phone: normalizePhone(phone),
    email: email.trim().toLowerCase(),
    password,
    confirmPassword,
  })
  if (data.id == null || typeof data.message !== 'string') {
    throw new Error('Dữ liệu đăng ký từ máy chủ không hợp lệ.')
  }
  return data
}

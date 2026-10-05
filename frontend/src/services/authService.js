const API_BASE_URL = (
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
).replace(/\/+$/, '')

export async function loginByPhone({ phone, password }) {
  const controller = new AbortController()

  const timeoutId = window.setTimeout(() => {
    controller.abort()
  }, 15000)

  try {
    const response = await fetch(
      `${API_BASE_URL}/api/auth/login/phone`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: JSON.stringify({
          phone: phone.trim(),
          password,
        }),
        signal: controller.signal,
      },
    )

    const text = await response.text()
    let data = null

    if (text) {
      try {
        data = JSON.parse(text)
      } catch {
        data = null
      }
    }

    if (!response.ok) {
      const errorMessage =
        typeof data?.message === 'string'
          ? data.message
          : response.status === 401
            ? 'Số điện thoại hoặc mật khẩu không đúng.'
            : 'Không thể đăng nhập. Vui lòng thử lại.'

      throw new Error(errorMessage)
    }

    if (
      !data?.accessToken ||
      data?.id == null ||
      !['CUSTOMER', 'STAFF', 'ADMIN'].includes(data?.role)
    ) {
      throw new Error(
        'Dữ liệu đăng nhập từ máy chủ không hợp lệ.',
      )
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
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error(
        'Máy chủ phản hồi quá lâu. Vui lòng thử lại.',
      )
    }

    if (error instanceof TypeError) {
      throw new Error(
        'Không kết nối được máy chủ. Kiểm tra backend và cấu hình CORS.',
      )
    }

    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }
}
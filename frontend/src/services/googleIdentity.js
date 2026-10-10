let sdkPromise
let initializedClientId
let currentOwner
let currentCallback

function loadGoogleSdk() {
  if (window.google?.accounts?.id) {
    return Promise.resolve(window.google.accounts.id)
  }

  if (sdkPromise) {
    return sdkPromise
  }

  sdkPromise = new Promise((resolve, reject) => {
    let script = document.getElementById(
      'carrot-google-identity',
    )

    if (script) {
      script.remove()
    }

    script = document.createElement('script')
    script.id = 'carrot-google-identity'
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true

    const timer = window.setTimeout(
      () => fail(),
      15000,
    )

    function fail() {
      window.clearTimeout(timer)

      script.onload = null
      script.onerror = null
      script.remove()

      sdkPromise = undefined

      reject(
        new Error(
          'Không tải được đăng nhập Google. Kiểm tra kết nối rồi thử lại.',
        ),
      )
    }

    script.onerror = fail

    script.onload = () => {
      window.clearTimeout(timer)

      const sdk = window.google?.accounts?.id

      if (!sdk) {
        fail()
        return
      }

      script.onload = null
      script.onerror = null

      resolve(sdk)
    }

    document.head.appendChild(script)
  })

  return sdkPromise
}

export async function mountGoogleButton({
  element,
  clientId,
  onCredential,
  isActive,
}) {
  if (!clientId?.endsWith('.apps.googleusercontent.com')) {
    throw new Error(
      'Chưa cấu hình Google Client ID cho frontend.',
    )
  }

  const sdk = await loadGoogleSdk()

  if (!isActive()) {
    return () => {}
  }

  if (
    initializedClientId &&
    initializedClientId !== clientId
  ) {
    throw new Error(
      'Google Client ID đã thay đổi. Vui lòng tải lại trang.',
    )
  }

  const owner = Symbol('google-button')

  currentOwner = owner
  currentCallback = onCredential

  if (!initializedClientId) {
    sdk.initialize({
      client_id: clientId,
      ux_mode: 'popup',
      auto_select: false,
      callback: (response) => {
        currentCallback?.(response)
      },
    })

    initializedClientId = clientId
  }

  element.replaceChildren()

  sdk.renderButton(element, {
    type: 'standard',
    theme: 'outline',
    size: 'large',
    text: 'continue_with',
    shape: 'pill',
    logo_alignment: 'left',
    width: Math.min(
      400,
      Math.max(200, element.clientWidth || 300),
    ),
    locale: 'vi',
  })

  return () => {
    if (currentOwner === owner) {
      currentOwner = null
      currentCallback = null
    }

    element.replaceChildren()
  }
}
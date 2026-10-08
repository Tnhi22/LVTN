import { courtGroups } from './customerSiteConfig.js'
import { getCustomerData } from './customerService.js'

export const money = (value) =>
  new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(value || 0)

export const time = (value) => value?.slice(0, 5) || '—'

export const todayVN = () =>
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date())

export function groupKey(court) {
  const normalize = (value) =>
    String(value || '')
      .toUpperCase()
      .replace(/[^A-Z0-9]/g, '')

  const classify = (value) => {
    if (
      value.includes('DAILYVISITOR') ||
      value.includes('MISTFAN')
    ) {
      return 'DAILY_VISITOR'
    }

    if (value.includes('PREMIUM')) return 'PREMIUM'
    if (value.includes('GOLD')) return 'GOLD'
    if (value.includes('BASIC')) return 'BASIC'

    return null
  }

  // Ưu tiên nhóm phòng đã được backend cấu hình.
  return (
    classify(normalize(court.roomGroup)) ||
    classify(normalize(court.typeName)) ||
    classify(normalize(court.roomName)) ||
    'OTHER'
  )
}

export function buildGroups(courts, includeInactive = false) {
  return courtGroups.map((group) => {
    const rows = courts.filter(
      (court) =>
        (includeInactive || court.active) &&
        groupKey(court) === group.key,
    )

    const rooms = [
      ...new Map(
        rows.map((court) => [
          court.roomId || court.roomName,
          {
            id: court.roomId,
            name: court.roomName,
            group: court.roomGroup,
            courts: rows.filter(
              (item) => item.roomId === court.roomId,
            ),
            price: court.price,
          },
        ]),
      ).values(),
    ]

    return {
      ...group,
      courts: rows,
      rooms,
    }
  })
}

export async function publicRequest(path) {
  const controller = new AbortController()

  const timeout = window.setTimeout(
    () => controller.abort(),
    15000,
  )

  try {
    return await getCustomerData(
      path,
      '',
      controller.signal,
    )
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error(
        'Máy chủ phản hồi quá lâu. Vui lòng thử lại.',
      )
    }

    throw error
  } finally {
    window.clearTimeout(timeout)
  }
}
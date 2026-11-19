export const uz = {
  common: {
    loading: 'Yuklanmoqda…',
    retry: 'Qayta urinish',
    copy: 'Nusxalash',
    copied: 'Nusxalandi',
    share: 'Telegramda yuborish',
    cancel: 'Bekor qilish',
    done: 'Tayyor',
  },
  auth: {
    outsideTitle: 'Ilovani Telegram orqali oching',
    outsideText: 'EduBot Telegram ichida ishlaydi. Botga o‘ting va «Ilovani ochish» tugmasini bosing.',
    failedTitle: 'Kirib bo‘lmadi',
    failedText: 'Ilovani yopib, qayta oching. Muammo takrorlansa, administratorga murojaat qiling.',
  },
  onboarding: {
    noCenterTitle: 'Siz hali markazga ulanmagansiz',
    noCenterText:
      'Markaz administratori yuborgan taklif havolasini oching. O‘quv markazingizni EduBot’ga ulamoqchi bo‘lsangiz, biz bilan bog‘laning.',
    pickCenterTitle: 'Markazni tanlang',
    pickCenterText: 'Siz bir nechta markazda ro‘yxatdan o‘tgansiz.',
  },
  roles: {
    OWNER: 'Egasi',
    ADMIN: 'Administrator',
    TEACHER: 'O‘qituvchi',
    PARENT: 'Ota-ona',
  },
  home: {
    greeting: 'Assalomu alaykum, {{name}}!',
    inProgress:
      'O‘quvchilar, guruhlar, davomat va to‘lovlar bo‘limlari tayyorlanmoqda va tez orada shu yerda paydo bo‘ladi.',
    platform: 'Platforma boshqaruvi',
    platformHint: 'Markazlar, tariflar va obunalar',
    switchCenter: 'Markazni almashtirish',
  },
  platform: {
    title: 'Markazlar',
    create: 'Yangi markaz',
    name: 'Markaz nomi',
    centerPhone: 'Markaz telefoni (ixtiyoriy)',
    ownerPhone: 'Egasining telefoni',
    ownerPhoneHint: 'Ega taklifni aynan shu raqam bilan Telegramda tasdiqlaydi',
    plan: 'Tarif',
    trialDays: 'Sinov muddati, kun',
    submit: 'Markazni yaratish',
    empty: 'Hali markazlar yo‘q',
    emptyHint: 'Birinchi o‘quv markazini yarating va egasiga taklif havolasini yuboring.',
    inviteTitle: 'Markaz yaratildi',
    inviteText: 'Havolani markaz egasiga yuboring. U 7 kun amal qiladi va faqat bir marta ishlaydi.',
    inviteShareText: 'EduBot’ga taklif',
    until: '{{date}} gacha',
    perMonth: '/oy',
    total: 'Jami: {{count}}',
    status: {
      PENDING: 'Kutilmoqda',
      ACTIVE: 'Faol',
      SUSPENDED: 'To‘xtatilgan',
      DEACTIVATED: 'O‘chirilgan',
    },
    subscription: {
      TRIAL: 'Sinov',
      ACTIVE: 'Faol obuna',
      EXPIRED: 'Muddati tugagan',
      CANCELLED: 'Bekor qilingan',
    },
  },
  errors: {
    INVALID_PHONE: 'Telefon raqami noto‘g‘ri',
    PLAN_NOT_FOUND: 'Tarif topilmadi',
    NETWORK_ERROR: 'Internet aloqasini tekshiring',
    FORBIDDEN: 'Bu amal uchun ruxsatingiz yo‘q',
    VALIDATION_FAILED: 'Maydonlarni to‘g‘ri to‘ldiring',
    UNKNOWN: 'Xatolik yuz berdi. Qayta urinib ko‘ring',
  },
}

export type Translations = typeof uz

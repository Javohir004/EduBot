package uz.edubot.bot;

import uz.edubot.center.MembershipRole;

/**
 * User-facing bot texts (Uzbek, Latin script). Russian will be added together with frontend i18n.
 */
final class BotMessages {

    static final String WELCOME = "Assalomu alaykum! EduBot — o'quv markazingizni Telegram orqali boshqarish tizimi.";
    static final String OPEN_APP = "Ilovani ochish";
    static final String OPEN_APP_HINT = "Ilovani quyidagi tugma orqali oching:";
    static final String NO_APP_URL = "Ilova manzili hali sozlanmagan. Administrator bilan bog'laning.";
    static final String SHARE_CONTACT_REQUEST =
            "Taklifni tasdiqlash uchun telefon raqamingizni yuboring. Raqam taklifdagi raqamga mos kelishi kerak.";
    static final String SHARE_CONTACT_BUTTON = "📱 Raqamni yuborish";
    static final String NOT_OWN_CONTACT = "Iltimos, o'zingizning raqamingizni tugma orqali yuboring.";
    static final String NO_PENDING_INVITE = "Avval markaz yuborgan taklif havolasini oching.";
    static final String INVITE_NOT_FOUND = "Taklif topilmadi. Havolani to'liq nusxalaganingizni tekshiring.";
    static final String INVITE_USED = "Bu taklif allaqachon ishlatilgan.";
    static final String INVITE_EXPIRED = "Taklif muddati tugagan yoki bekor qilingan. Markazdan yangi havola so'rang.";
    static final String PHONE_MISMATCH =
            "Raqamingiz taklifdagi raqamga mos kelmadi. Markaz administratoriga murojaat qiling.";
    static final String UNEXPECTED_ERROR = "Xatolik yuz berdi. Birozdan keyin qayta urinib ko'ring.";
    static final String HELP = "Ilovani ochish uchun /start buyrug'ini yuboring.";

    private BotMessages() {
    }

    static String linked(String centerName, MembershipRole role) {
        return "✅ Tayyor! Siz «" + centerName + "» markaziga " + roleName(role) + " sifatida qo'shildingiz.";
    }

    private static String roleName(MembershipRole role) {
        return switch (role) {
            case OWNER -> "egasi";
            case ADMIN -> "administrator";
            case TEACHER -> "o'qituvchi";
            case PARENT -> "ota-ona";
        };
    }
}

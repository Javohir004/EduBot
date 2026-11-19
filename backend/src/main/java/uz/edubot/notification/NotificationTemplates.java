package uz.edubot.notification;

import java.util.Map;

/** Message texts per notification type and language (uz default, ru). */
final class NotificationTemplates {

    private NotificationTemplates() {
    }

    static String render(NotificationType type, Map<String, Object> p, String languageCode) {
        boolean ru = languageCode != null && languageCode.toLowerCase().startsWith("ru");
        return switch (type) {
            case ABSENCE -> ru
                    ? "🔴 %s не пришёл(ла) на занятие «%s» %s в %s.\n%s".formatted(
                            p.get("studentName"), p.get("groupName"), p.get("date"), p.get("time"), p.get("centerName"))
                    : "🔴 %s %s kuni soat %s dagi «%s» darsiga kelmadi.\n%s".formatted(
                            p.get("studentName"), p.get("date"), p.get("time"), p.get("groupName"), p.get("centerName"));
            case PAYMENT_REMINDER -> ru
                    ? "💳 Напоминание: по ученику %s есть просроченная оплата — %s сум.\n%s".formatted(
                            p.get("studentName"), p.get("amount"), p.get("centerName"))
                    : "💳 Eslatma: %s uchun %s so‘m to‘lov muddati o‘tgan.\n%s".formatted(
                            p.get("studentName"), p.get("amount"), p.get("centerName"));
            case SUBSCRIPTION_EXPIRING -> ru
                    ? "⏳ Подписка EduBot центра «%s» заканчивается %s. Чтобы продлить, свяжитесь с нами.".formatted(
                            p.get("centerName"), p.get("date"))
                    : "⏳ «%s» markazining EduBot obunasi %s kuni tugaydi. Uzaytirish uchun biz bilan bog‘laning.".formatted(
                            p.get("centerName"), p.get("date"));
        };
    }
}

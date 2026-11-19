package uz.edubot.notification;

public enum NotificationType {
    /** A student was marked ABSENT; sent to guardians who use the app. */
    ABSENCE,
    /** Overdue tuition; sent to guardians who use the app. */
    PAYMENT_REMINDER,
    /** The center's EduBot subscription ends soon; sent to owners. */
    SUBSCRIPTION_EXPIRING
}

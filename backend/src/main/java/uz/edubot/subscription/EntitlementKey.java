package uz.edubot.subscription;

/**
 * Keys are code, values are data: limits live in plan_entitlement / entitlement_override and are
 * changed by the platform admin without a deploy.
 */
public enum EntitlementKey {
    MAX_STUDENTS,
    MAX_TEACHERS,
    MAX_GROUPS,
    FEATURE_NOTIFICATIONS,
    FEATURE_PARENT_PORTAL,
    FEATURE_EXPORT
}

package uz.edubot.common;

/**
 * Lifecycle of people records. Records are archived, never deleted, to keep financial and
 * attendance history intact.
 */
public enum PersonStatus {
    ACTIVE, ARCHIVED
}

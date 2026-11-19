package uz.edubot.security;

import java.util.EnumSet;
import java.util.Set;
import uz.edubot.center.MembershipRole;

/**
 * Fine-grained capabilities. Code checks permissions, never role names, so role definitions can
 * change in one place ({@link #forRole}).
 */
public enum Permission {
    CENTER_READ,
    CENTER_MANAGE,
    STAFF_MANAGE,
    INVITE_ADMIN,
    INVITE_STAFF,
    STUDENT_READ,
    STUDENT_WRITE,
    GROUP_READ,
    GROUP_WRITE,
    ATTENDANCE_MARK,
    PAYMENT_READ,
    PAYMENT_WRITE,
    PAYMENT_VOID,
    REVENUE_READ,
    CHILD_READ,
    PLATFORM_MANAGE;

    public static Set<Permission> forRole(MembershipRole role) {
        return switch (role) {
            case OWNER -> EnumSet.of(CENTER_READ, CENTER_MANAGE, STAFF_MANAGE, INVITE_ADMIN, INVITE_STAFF,
                    STUDENT_READ, STUDENT_WRITE, GROUP_READ, GROUP_WRITE, ATTENDANCE_MARK,
                    PAYMENT_READ, PAYMENT_WRITE, PAYMENT_VOID, REVENUE_READ);
            case ADMIN -> EnumSet.of(CENTER_READ, INVITE_STAFF, STUDENT_READ, STUDENT_WRITE, GROUP_READ,
                    GROUP_WRITE, ATTENDANCE_MARK, PAYMENT_READ, PAYMENT_WRITE);
            case TEACHER -> EnumSet.of(CENTER_READ, STUDENT_READ, GROUP_READ, ATTENDANCE_MARK);
            case PARENT -> EnumSet.of(CENTER_READ, CHILD_READ);
        };
    }
}

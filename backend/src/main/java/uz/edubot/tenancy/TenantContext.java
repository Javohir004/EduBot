package uz.edubot.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Center id of the current unit of work. Set from the authenticated membership for HTTP requests
 * and explicitly via {@link #runAs} for scheduled jobs. Never set from client input.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        UUID centerId = CURRENT.get();
        if (centerId == null) {
            throw new IllegalStateException("No tenant bound to the current thread");
        }
        return centerId;
    }

    public static <T> T runAs(UUID centerId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(centerId);
        try {
            return work.get();
        } finally {
            restore(previous);
        }
    }

    /** Binds a tenant for the rest of the request; the caller must call {@link #clear()} in finally. */
    public static void bind(UUID centerId) {
        CURRENT.set(centerId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static void restore(UUID previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}

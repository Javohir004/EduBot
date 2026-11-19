package uz.edubot.common.web;

import org.springframework.http.HttpStatus;
import uz.edubot.common.error.ApiException;

/** Optimistic locking: update requests must echo the version the client last read. */
public final class Versions {

    private Versions() {
    }

    public static long require(Long version) {
        if (version == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VERSION_REQUIRED", "version is required");
        }
        return version;
    }

    public static void check(long actual, long expected) {
        if (actual != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_VERSION", "The record was changed by someone else");
        }
    }
}

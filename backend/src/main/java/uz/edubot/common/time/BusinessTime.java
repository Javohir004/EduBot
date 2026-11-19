package uz.edubot.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Business dates (lesson days, billing months) are local to the center. All centers are in
 * Uzbekistan for now; storage stays UTC.
 */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    private BusinessTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}

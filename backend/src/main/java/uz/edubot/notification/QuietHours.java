package uz.edubot.notification;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Moves a send time out of the center's quiet hours (e.g. 22:00-08:00) to the next morning. */
public final class QuietHours {

    private QuietHours() {
    }

    public static Instant shift(Instant at, LocalTime start, LocalTime end, ZoneId zone) {
        ZonedDateTime local = at.atZone(zone);
        LocalTime time = local.toLocalTime();
        boolean overnight = start.isAfter(end);
        boolean quiet = overnight
                ? !time.isBefore(start) || time.isBefore(end)
                : !time.isBefore(start) && time.isBefore(end);
        if (!quiet) {
            return at;
        }
        ZonedDateTime resume = local.with(end).withSecond(0).withNano(0);
        if (!resume.isAfter(local)) {
            resume = resume.plusDays(1);
        }
        return resume.toInstant();
    }
}

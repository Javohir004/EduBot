package uz.edubot.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class QuietHoursTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final LocalTime START = LocalTime.of(22, 0);
    private static final LocalTime END = LocalTime.of(8, 0);

    @Test
    void daytimeIsUnchanged() {
        Instant at = Instant.parse("2026-11-19T09:00:00Z"); // 14:00 Tashkent
        assertThat(QuietHours.shift(at, START, END, TASHKENT)).isEqualTo(at);
    }

    @Test
    void lateEveningMovesToNextMorning() {
        Instant at = Instant.parse("2026-11-19T17:30:00Z"); // 22:30 Tashkent
        assertThat(QuietHours.shift(at, START, END, TASHKENT)).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
    }

    @Test
    void earlyMorningMovesToSameMorning() {
        Instant at = Instant.parse("2026-11-19T01:00:00Z"); // 06:00 Tashkent
        assertThat(QuietHours.shift(at, START, END, TASHKENT)).isEqualTo(Instant.parse("2026-11-19T03:00:00Z"));
    }
}

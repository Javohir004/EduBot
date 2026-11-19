package uz.edubot.group;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GroupData(String name, String subject, UUID teacherId, BigDecimal monthlyFee, LocalDate startDate,
                        LocalDate endDate, Integer capacity) {
}

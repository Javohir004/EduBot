package uz.edubot.billing;

import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.tenancy.TenantContext;

/**
 * Daily (idempotent) charge generation for the current month in every active center. Runs daily
 * rather than once a month so a missed run (deploy, downtime) heals itself the next night.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MonthlyChargeJob {

    private final BillingRepository repository;
    private final BillingService billingService;
    private final Clock clock;

    /** Weekly payment reminders, queued at 10:00 so they arrive during the day. */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "payment-reminders", lockAtMostFor = "PT30M")
    void reminders() {
        for (UUID centerId : repository.activeCenterIds()) {
            try {
                TenantContext.runAs(centerId, billingService::queueWeeklyReminders);
            } catch (RuntimeException e) {
                log.error("Payment reminders failed centerId={}", centerId, e);
            }
        }
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Tashkent")
    @SchedulerLock(name = "monthly-charges", lockAtMostFor = "PT30M")
    void run() {
        YearMonth month = YearMonth.from(BusinessTime.today(clock));
        for (UUID centerId : repository.activeCenterIds()) {
            try {
                // runAs wraps the @Transactional call, so the new session gets this center as tenant.
                int charged = TenantContext.runAs(centerId, () -> billingService.generateForMonth(month));
                if (charged > 0) {
                    log.info("Generated charges centerId={} month={} students={}", centerId, month, charged);
                }
            } catch (RuntimeException e) {
                // One broken center must not stop billing for the others.
                log.error("Charge generation failed centerId={} month={}", centerId, month, e);
            }
        }
    }
}

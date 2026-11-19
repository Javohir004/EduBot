package uz.edubot.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uz.edubot.attendance.AttendanceService;
import uz.edubot.attendance.AttendanceStatus;
import uz.edubot.billing.BillingService;
import uz.edubot.center.CenterMembership;
import uz.edubot.center.CenterService;
import uz.edubot.center.EducationCenter;
import uz.edubot.center.MembershipRole;
import uz.edubot.center.MembershipService;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.group.Enrollment;
import uz.edubot.group.GroupData;
import uz.edubot.group.GroupService;
import uz.edubot.group.GroupService.Slot;
import uz.edubot.group.StudyGroup;
import uz.edubot.security.CurrentMember;
import uz.edubot.security.Permission;
import uz.edubot.student.GuardianRelation;
import uz.edubot.student.Student;
import uz.edubot.student.StudentData;
import uz.edubot.student.StudentService;
import uz.edubot.student.StudentService.GuardianInput;
import uz.edubot.subscription.SubscriptionService;
import uz.edubot.teacher.Teacher;
import uz.edubot.teacher.TeacherService;
import uz.edubot.tenancy.TenantContext;
import uz.edubot.user.AppUser;
import uz.edubot.user.UserService;

/**
 * Fills a clearly named demo center with realistic data (only with the {@code demo} profile).
 * It goes through the normal services, so demo data obeys every business rule. Runs once:
 * if the demo center exists, nothing happens. Phone numbers are fictional (+998 90 000 ...).
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    public static final String CENTER_NAME = "EduBot Demo Markazi";

    private static final String[][] STUDENTS = {
            {"Azizbek", "Karimov"}, {"Madina", "Yusupova"}, {"Jasur", "Rahimov"}, {"Nilufar", "Toshmatova"},
            {"Sardor", "Aliyev"}, {"Dilnoza", "Ergasheva"}, {"Bekzod", "Nazarov"}, {"Shahzoda", "Qodirova"},
            {"Otabek", "Sobirov"}, {"Malika", "Ismoilova"}, {"Javohir", "Umarov"}, {"Sevara", "Xolmatova"},
            {"Doniyor", "Abdullayev"}, {"Kamola", "Mirzayeva"}, {"Ulug'bek", "Tursunov"}, {"Zarina", "Hasanova"},
            {"Islom", "Jo'rayev"}, {"Mohira", "Saidova"}, {"Akmal", "Bakirov"}, {"Laylo", "Ortiqova"},
            {"Shoxrux", "Valiyev"}, {"Gulnoza", "Rustamova"}, {"Temur", "Ahmedov"}, {"Ozoda", "Karimova"},
    };

    private final UserService userService;
    private final CenterService centerService;
    private final MembershipService membershipService;
    private final SubscriptionService subscriptionService;
    private final TeacherService teacherService;
    private final StudentService studentService;
    private final GroupService groupService;
    private final AttendanceService attendanceService;
    private final BillingService billingService;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${edubot.demo.owner-telegram-id:}")
    private String ownerTelegramId;

    @Override
    public void run(ApplicationArguments args) {
        if (ownerTelegramId == null || ownerTelegramId.isBlank()) {
            log.warn("Demo profile active but DEMO_OWNER_TELEGRAM_ID is empty: no demo data created");
            return;
        }
        seed(Long.parseLong(ownerTelegramId.strip()));
    }

    /** @return the demo center id (existing or newly created) */
    public UUID seed(long ownerTelegramId) {
        List<UUID> existing = jdbc.queryForList("select id from education_center where name = ?", UUID.class, CENTER_NAME);
        if (!existing.isEmpty()) {
            log.info("Demo center already exists: {}", existing.getFirst());
            return existing.getFirst();
        }
        AppUser owner = userService.ensureExists(ownerTelegramId, "Demo");
        EducationCenter center = centerService.create(CENTER_NAME, "+998900000000", "Toshkent, Demo ko'chasi 1");
        subscriptionService.startTrial(center.getId(), "PREMIUM", Duration.ofDays(30), null);
        CenterMembership membership = membershipService.grant(center.getId(), owner.getId(), MembershipRole.OWNER);
        CurrentMember member = new CurrentMember(owner.getId(), null, membership.getId(), center.getId(),
                MembershipRole.OWNER, Permission.forRole(MembershipRole.OWNER));

        TenantContext.runAs(center.getId(), () -> {
            seedContent(member);
            return null;
        });
        log.info("Demo center created: {}", center.getId());
        return center.getId();
    }

    private void seedContent(CurrentMember member) {
        Random random = new Random(42);
        LocalDate today = BusinessTime.today(clock);
        LocalDate joined = YearMonth.from(today).minusMonths(1).atDay(1);

        Teacher aziza = teacherService.create("Aziza Karimova", null, "IELTS, General English");
        Teacher bobur = teacherService.create("Bobur Tursunov", null, "Matematika");
        Teacher dilshod = teacherService.create("Dilshod Rahimov", null, "Python");

        List<StudyGroup> groups = List.of(
                group("IELTS Intensive", "IELTS", aziza, "600000", joined, LocalTime.of(14, 0),
                        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                group("General English B1", "English", aziza, "450000", joined, LocalTime.of(10, 0),
                        DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                group("Matematika 9-sinf", "Matematika", bobur, "400000", joined, LocalTime.of(16, 0),
                        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                group("Python Junior", "Python", dilshod, "550000", joined, LocalTime.of(18, 0),
                        DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));

        List<Student> students = new ArrayList<>();
        for (int i = 0; i < STUDENTS.length; i++) {
            String phone = "+99890000%04d".formatted(100 + i);
            String parentPhone = "+99890000%04d".formatted(500 + i);
            students.add(studentService.create(
                    new StudentData(STUDENTS[i][0], STUDENTS[i][1], today.minusYears(12 + i % 8).withDayOfYear(1 + i * 9),
                            phone, null),
                    List.of(new GuardianInput("Ota-ona: " + STUDENTS[i][1], parentPhone,
                            i % 2 == 0 ? GuardianRelation.MOTHER : GuardianRelation.FATHER))));
        }
        for (int i = 0; i < students.size(); i++) {
            groupService.enroll(groups.get(i % groups.size()).getId(), students.get(i).getId(), joined, null);
            if (i % 5 == 0) {
                groupService.enroll(groups.get((i + 1) % groups.size()).getId(), students.get(i).getId(), joined, null);
            }
        }

        seedAttendance(member, groups, today, random);
        seedPayments(member, students, random);
    }

    private StudyGroup group(String name, String subject, Teacher teacher, String fee, LocalDate start, LocalTime time,
                             DayOfWeek... days) {
        StudyGroup group = groupService.create(new GroupData(name, subject, teacher.getId(), new BigDecimal(fee),
                start, null, 16));
        List<Slot> slots = new ArrayList<>();
        for (DayOfWeek day : days) {
            slots.add(new Slot(day, time, time.plusMinutes(90), "10" + (slots.size() + 1)));
        }
        groupService.replaceSchedule(group.getId(), slots);
        return group;
    }

    private void seedAttendance(CurrentMember member, List<StudyGroup> groups, LocalDate today, Random random) {
        for (LocalDate day = today.minusDays(14); day.isBefore(today); day = day.plusDays(1)) {
            for (StudyGroup group : groups) {
                LocalDate date = day;
                groupService.scheduleOf(group.getId()).stream()
                        .filter(s -> s.getDayOfWeek() == date.getDayOfWeek().getValue())
                        .forEach(slot -> {
                            UUID lessonId = attendanceService.openLesson(member, group.getId(), date, slot.getStartTime());
                            List<AttendanceService.Mark> marks = groupService.membersOn(group.getId(), date).stream()
                                    .map(Enrollment::getStudentId)
                                    .map(id -> new AttendanceService.Mark(id, randomStatus(random)))
                                    .toList();
                            if (!marks.isEmpty()) {
                                attendanceService.mark(member, lessonId, marks);
                            }
                        });
            }
        }
    }

    private void seedPayments(CurrentMember member, List<Student> students, Random random) {
        for (Student student : students) {
            BigDecimal monthly = billingService.balance(student.getId()).debt();
            // Most families paid last month; this month is a mix of paid, partial and unpaid.
            double roll = random.nextDouble();
            BigDecimal amount = roll < 0.5 ? monthly
                    : roll < 0.8 ? monthly.divide(BigDecimal.TWO).setScale(-3, java.math.RoundingMode.DOWN)
                    : monthly.divide(BigDecimal.TWO).setScale(-3, java.math.RoundingMode.DOWN).max(BigDecimal.ZERO)
                            .multiply(random.nextBoolean() ? BigDecimal.ONE : BigDecimal.ZERO);
            if (amount.signum() > 0) {
                billingService.recordPayment(member, student.getId(), amount, random.nextBoolean() ? "CASH" : "CARD",
                        null, null, "demo-" + student.getId());
            }
        }
    }

    private static AttendanceStatus randomStatus(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.84) {
            return AttendanceStatus.PRESENT;
        }
        if (roll < 0.92) {
            return AttendanceStatus.ABSENT;
        }
        return roll < 0.97 ? AttendanceStatus.LATE : AttendanceStatus.EXCUSED;
    }
}

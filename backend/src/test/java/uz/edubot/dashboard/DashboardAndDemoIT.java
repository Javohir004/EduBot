package uz.edubot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import uz.edubot.attendance.AttendanceService;
import uz.edubot.billing.BillingService;
import uz.edubot.center.CenterService;
import uz.edubot.center.MembershipService;
import uz.edubot.demo.DemoDataSeeder;
import uz.edubot.group.GroupService;
import uz.edubot.student.StudentService;
import uz.edubot.subscription.SubscriptionService;
import uz.edubot.support.IntegrationTestSupport;
import uz.edubot.teacher.TeacherService;
import uz.edubot.user.UserService;

class DashboardAndDemoIT extends IntegrationTestSupport {

    @Autowired private UserService userService;
    @Autowired private CenterService centerService;
    @Autowired private MembershipService membershipService;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private TeacherService teacherService;
    @Autowired private StudentService studentService;
    @Autowired private GroupService groupService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private BillingService billingService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @Test
    void demoSeederCreatesARealisticCenterOnce() throws Exception {
        DemoDataSeeder seeder = new DemoDataSeeder(userService, centerService, membershipService, subscriptionService,
                teacherService, studentService, groupService, attendanceService, billingService, jdbcTemplate, clock);

        UUID centerId = seeder.seed(9500L);
        assertThat(seeder.seed(9500L)).isEqualTo(centerId);

        assertThat(count("select count(*) from education_center where name = 'EduBot Demo Markazi'")).isEqualTo(1);
        assertThat(count("select count(*) from student")).isEqualTo(24);
        assertThat(count("select count(*) from study_group")).isEqualTo(4);
        assertThat(count("select count(*) from attendance")).isGreaterThan(50);
        assertThat(count("select count(*) from payment")).isGreaterThan(5);
        // Demo guardians are not linked to Telegram accounts, so no real person is messaged.
        assertThat(count("select count(*) from notification")).isZero();

        String owner = loginAndGetToken(9500L, "Demo");
        mvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeStudents").value(24))
                .andExpect(jsonPath("$.activeGroups").value(4))
                .andExpect(jsonPath("$.activeTeachers").value(3))
                .andExpect(jsonPath("$.revenueThisMonth").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.debtorCount").value(greaterThan(0)));
        mvc.perform(get("/api/v1/activity").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$[0].type").exists());
    }

    @Test
    void moneyIsHiddenFromAdminsAndTeachersCannotOpenTheDashboard() throws Exception {
        String owner = newCenterOwner("Alpha", 9601L, "+998901000701");
        String admin = invite(owner, "ADMIN", "+998935550011", 9602L);
        String teacher = invite(owner, "TEACHER", "+998935550012", 9603L);

        mvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenueThisMonth").value(nullValue()))
                .andExpect(jsonPath("$.outstandingDebt").value(0.0));
        mvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardCountsOnlyTheOwnCenter() throws Exception {
        String ownerA = newCenterOwner("Alpha", 9604L, "+998901000704");
        String ownerB = newCenterOwner("Beta", 9605L, "+998901000705");
        mvc.perform(post("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(ownerA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"student\":{\"firstName\":\"A\",\"lastName\":\"B\"}}"));

        mvc.perform(get("/api/v1/dashboard/summary").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(jsonPath("$.activeStudents").value(0));
        mvc.perform(get("/api/v1/activity").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String invite(String owner, String role, String phone, long telegramId) throws Exception {
        String link = JsonPath.read(mvc.perform(post("/api/v1/invites").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\",\"phone\":\"" + phone + "\"}"))
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, telegramId, phone);
        return loginAndGetToken(telegramId, role);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}

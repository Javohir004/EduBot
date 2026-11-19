package uz.edubot.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.support.IntegrationTestSupport;

/** Parent view, payment reminders, staff management and center settings. */
class ParentsRemindersStaffIT extends IntegrationTestSupport {

    private static final String PARENT_PHONE = "+998934445566";
    private static final long PARENT_ID = 9100L;

    @Autowired
    private MonthlyChargeJob jobs;

    private String owner;
    private String childId;
    private String otherId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newCenterOwner("Alpha", 9101L, "+998901000601");
        String groupId = id(call(owner, "/api/v1/groups", "{\"name\":\"IELTS\",\"monthlyFee\":500000,\"startDate\":\"2025-01-01\"}"));
        String body = call(owner, "/api/v1/students", "{\"student\":{\"firstName\":\"Ali\",\"lastName\":\"Valiyev\"},"
                + "\"guardians\":[{\"fullName\":\"Ona\",\"phone\":\"" + PARENT_PHONE + "\",\"relation\":\"MOTHER\"}]}")
                .andReturn().getResponse().getContentAsString();
        childId = JsonPath.read(body, "$.id");
        String guardianId = JsonPath.read(body, "$.guardians[0].id");
        otherId = id(call(owner, "/api/v1/students", "{\"student\":{\"firstName\":\"Vali\",\"lastName\":\"Aliyev\"}}"));
        LocalDate monthStart = YearMonth.from(LocalDate.now(BusinessTime.ZONE)).atDay(1);
        for (String student : new String[] {childId, otherId}) {
            call(owner, "/api/v1/groups/" + groupId + "/enrollments",
                    "{\"studentId\":\"" + student + "\",\"joinedOn\":\"" + monthStart + "\"}");
        }
        String link = JsonPath.read(call(owner, "/api/v1/students/" + childId + "/guardians/" + guardianId + "/invite", "")
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, PARENT_ID, PARENT_PHONE);
    }

    @Test
    void parentPortalRequiresThePlanFeature() throws Exception {
        String parent = loginAndGetToken(PARENT_ID, "Ona");

        mvc.perform(get("/api/v1/parent/children").header(HttpHeaders.AUTHORIZATION, bearer(parent)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"));
    }

    @Test
    void parentSeesOnlyOwnChildWithGroupsAndDebt() throws Exception {
        upgradeToStandard();
        String parent = loginAndGetToken(PARENT_ID, "Ona");

        mvc.perform(get("/api/v1/parent/children").header(HttpHeaders.AUTHORIZATION, bearer(parent)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Ali"))
                .andExpect(jsonPath("$[0].debt").value(500000.0));
        mvc.perform(get("/api/v1/parent/children/" + childId).header(HttpHeaders.AUTHORIZATION, bearer(parent)))
                .andExpect(jsonPath("$.groups[0]").value("IELTS"))
                .andExpect(jsonPath("$.charges", hasSize(1)));
        mvc.perform(get("/api/v1/parent/children/" + otherId).header(HttpHeaders.AUTHORIZATION, bearer(parent)))
                .andExpect(status().isNotFound());
        // Parents have no access to the staff API.
        mvc.perform(get("/api/v1/students/" + childId).header(HttpHeaders.AUTHORIZATION, bearer(parent)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parent/children").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void weeklyRemindersAreQueuedOncePerWeekForOverdueDebt() throws Exception {
        jdbc.update("update tuition_charge set due_date = current_date + 3");
        jobs.reminders();
        assertThat(reminders()).isZero(); // not overdue yet

        jdbc.update("update tuition_charge set due_date = current_date - 3");
        jobs.reminders();
        jobs.reminders();
        // Only the linked parent of the first student gets a message; the other student has none.
        assertThat(reminders()).isEqualTo(1);

        jdbc.update("update center_settings set payment_reminders_enabled = false");
        jdbc.update("delete from notification");
        jobs.reminders();
        assertThat(reminders()).isZero();
    }

    @Test
    void manualReminderFromDebtorsList() throws Exception {
        call(owner, "/api/v1/debtors/" + childId + "/remind", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.queued").value(1));
        call(owner, "/api/v1/debtors/" + otherId + "/remind", "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_LINKED_PARENT"));
        assertThat(reminders()).isEqualTo(1);
    }

    @Test
    void ownerManagesStaffAndSettings() throws Exception {
        String link = JsonPath.read(call(owner, "/api/v1/invites", "{\"role\":\"ADMIN\",\"phone\":\"+998935550001\"}")
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, 9102L, "+998935550001");
        String admin = loginAndGetToken(9102L, "Admin");

        String staff = mvc.perform(get("/api/v1/staff").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        String ownerMembership = JsonPath.read(staff, "$[0].membershipId");
        String adminMembership = JsonPath.read(staff, "$[1].membershipId");

        mvc.perform(get("/api/v1/staff").header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/center/settings").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentDueDay\":10,\"absenceNotificationsEnabled\":true,\"paymentRemindersEnabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/center/settings").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentDueDay\":10,\"absenceNotificationsEnabled\":false,\"paymentRemindersEnabled\":true}"))
                .andExpect(jsonPath("$.paymentDueDay").value(10))
                .andExpect(jsonPath("$.absenceNotificationsEnabled").value(false));

        mvc.perform(delete("/api/v1/staff/" + ownerMembership).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_REVOKE_SELF"));
        mvc.perform(delete("/api/v1/staff/" + adminMembership).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnauthorized());
    }

    private void upgradeToStandard() throws Exception {
        String platformAdmin = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");
        call(platformAdmin, "/api/v1/platform/centers/" + centerIdOf(owner) + "/subscriptions",
                "{\"planCode\":\"STANDARD\",\"months\":1}").andExpect(status().isOk());
    }

    private int reminders() {
        Integer value = jdbc.queryForObject("select count(*) from notification where type = 'PAYMENT_REMINDER'", Integer.class);
        return value == null ? 0 : value;
    }

    private ResultActions call(String token, String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}

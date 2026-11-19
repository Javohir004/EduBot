package uz.edubot.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.support.IntegrationTestSupport;

class BillingApiIT extends IntegrationTestSupport {

    @Autowired
    private MonthlyChargeJob monthlyChargeJob;

    private String owner;
    private String groupId;
    private String studentId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newCenterOwner("Alpha", 8001L, "+998901000401");
        groupId = id(call(owner, "/api/v1/groups", "{\"name\":\"IELTS\",\"monthlyFee\":500000,\"startDate\":\"2025-01-01\"}"));
        studentId = id(call(owner, "/api/v1/students", "{\"student\":{\"firstName\":\"Ali\",\"lastName\":\"Valiyev\"},"
                + "\"guardians\":[{\"fullName\":\"Ona\",\"phone\":\"+998934445566\",\"relation\":\"MOTHER\"}]}"));
    }

    @Test
    void enrollmentChargesCurrentMonthAndShowsInDebtors() throws Exception {
        enroll(studentId, monthStart(0), null);

        balance(owner, studentId)
                .andExpect(jsonPath("$.debt").value(500000.0))
                .andExpect(jsonPath("$.charges", hasSize(1)))
                .andExpect(jsonPath("$.charges[0].paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.charges[0].groupName").value("IELTS"));
        mvc.perform(get("/api/v1/debtors").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.students").value(1))
                .andExpect(jsonPath("$.total").value(500000.0))
                .andExpect(jsonPath("$.items[0].guardianPhone").value("+998934445566"));
    }

    @Test
    void partialThenFullPayment() throws Exception {
        enroll(studentId, monthStart(0), null);

        pay(owner, studentId, "200000", UUID.randomUUID().toString()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.debt").value(300000.0))
                .andExpect(jsonPath("$.charges[0].paymentStatus").value("PARTIAL"));
        pay(owner, studentId, "300000", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.debt").value(0.0))
                .andExpect(jsonPath("$.charges[0].paymentStatus").value("PAID"));
        mvc.perform(get("/api/v1/debtors").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void overpaymentBecomesCreditThatPaysTheNextCharge() throws Exception {
        enroll(studentId, monthStart(0), null);
        pay(owner, studentId, "700000", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.debt").value(0.0))
                .andExpect(jsonPath("$.credit").value(200000.0));

        mvc.perform(post("/api/v1/billing/periods/" + YearMonth.from(today()).plusMonths(1) + "/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.studentsCharged").value(1));

        balance(owner, studentId)
                .andExpect(jsonPath("$.credit").value(0.0))
                // Next month is not debt yet, but the credit is already applied to it.
                .andExpect(jsonPath("$.charges[0].paidAmount").value(200000.0))
                .andExpect(jsonPath("$.charges[0].paymentStatus").value("PARTIAL"));
    }

    @Test
    void paymentsAreAppliedOldestFirst() throws Exception {
        enroll(studentId, monthStart(-2), null);

        pay(owner, studentId, "750000", UUID.randomUUID().toString())
                .andExpect(jsonPath("$.debt").value(750000.0))
                .andExpect(jsonPath("$.charges", hasSize(3)))
                .andExpect(jsonPath("$.charges[2].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.charges[1].paymentStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.charges[1].paidAmount").value(250000.0))
                .andExpect(jsonPath("$.charges[0].paymentStatus").value("UNPAID"));
    }

    @Test
    void retriedRequestWithSameIdempotencyKeyRecordsOnePayment() throws Exception {
        enroll(studentId, monthStart(0), null);
        String key = UUID.randomUUID().toString();

        pay(owner, studentId, "100000", key).andExpect(status().isCreated());
        pay(owner, studentId, "100000", key).andExpect(status().isCreated())
                .andExpect(jsonPath("$.debt").value(400000.0))
                .andExpect(jsonPath("$.payments", hasSize(1)));
        mvc.perform(post("/api/v1/students/" + studentId + "/payments").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1,\"method\":\"CASH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void individualFeeOverrideIsCharged() throws Exception {
        enroll(studentId, monthStart(0), "350000");

        balance(owner, studentId).andExpect(jsonPath("$.debt").value(350000.0));
    }

    @Test
    void voidingAPaymentRestoresDebtAndIsAudited() throws Exception {
        enroll(studentId, monthStart(0), null);
        String body = pay(owner, studentId, "500000", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(body, "$.payments[0].id");

        String admin = adminOfOwnersCenter();
        mvc.perform(post("/api/v1/payments/" + paymentId + "/void").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());

        voidPayment(owner, paymentId, "Wrong student").andExpect(status().isOk());
        balance(owner, studentId)
                .andExpect(jsonPath("$.debt").value(500000.0))
                .andExpect(jsonPath("$.payments[0].status").value("VOIDED"))
                .andExpect(jsonPath("$.payments[0].voidReason").value("Wrong student"));
        voidPayment(owner, paymentId, "again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_VOIDED"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PAYMENT_VOIDED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment", Integer.class)).isEqualTo(1);
    }

    @Test
    void voidedPaymentLetsOtherCreditCoverTheCharge() throws Exception {
        enroll(studentId, monthStart(0), null);
        String first = JsonPath.read(pay(owner, studentId, "500000", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString(), "$.payments[0].id");
        pay(owner, studentId, "500000", UUID.randomUUID().toString()).andExpect(jsonPath("$.credit").value(500000.0));

        voidPayment(owner, first, "duplicate entry");

        balance(owner, studentId)
                .andExpect(jsonPath("$.debt").value(0.0))
                .andExpect(jsonPath("$.credit").value(0.0));
    }

    @Test
    void chargeCorrectionsAndVoid() throws Exception {
        enroll(studentId, monthStart(0), null);
        pay(owner, studentId, "300000", UUID.randomUUID().toString());
        String chargeId = JsonPath.read(balance(owner, studentId).andReturn().getResponse().getContentAsString(),
                "$.charges[0].id");

        mvc.perform(put("/api/v1/charges/" + chargeId).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":200000,\"reason\":\"discount\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMOUNT_BELOW_PAID"));
        mvc.perform(put("/api/v1/charges/" + chargeId).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":400000,\"reason\":\"discount\"}"))
                .andExpect(jsonPath("$.amount").value(400000.0));
        balance(owner, studentId).andExpect(jsonPath("$.debt").value(100000.0));

        mvc.perform(post("/api/v1/charges/" + chargeId + "/void").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"joined by mistake\"}"))
                .andExpect(jsonPath("$.status").value("VOID"));
        balance(owner, studentId)
                .andExpect(jsonPath("$.debt").value(0.0))
                .andExpect(jsonPath("$.credit").value(300000.0));
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(2);
    }

    @Test
    void chargeGenerationIsIdempotentAndTheDailyJobHealsMissingCharges() throws Exception {
        enroll(studentId, monthStart(0), null);
        String month = YearMonth.from(today()).toString();
        mvc.perform(post("/api/v1/billing/periods/" + month + "/generate").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.studentsCharged").value(0));

        jdbc.update("delete from tuition_charge");
        monthlyChargeJob.run();
        monthlyChargeJob.run();

        assertThat(jdbc.queryForObject("select count(*) from tuition_charge", Integer.class)).isEqualTo(1);
    }

    @Test
    void anotherCenterCannotSeeOrChangeMoney() throws Exception {
        enroll(studentId, monthStart(0), null);
        String paymentId = JsonPath.read(pay(owner, studentId, "100000", UUID.randomUUID().toString())
                .andReturn().getResponse().getContentAsString(), "$.payments[0].id");
        String chargeId = JsonPath.read(balance(owner, studentId).andReturn().getResponse().getContentAsString(),
                "$.charges[0].id");
        String ownerB = newCenterOwner("Beta", 8002L, "+998901000402");

        balance(ownerB, studentId).andExpect(status().isNotFound());
        pay(ownerB, studentId, "100", UUID.randomUUID().toString()).andExpect(status().isNotFound());
        voidPayment(ownerB, paymentId, "x").andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/charges/" + chargeId + "/void").header(HttpHeaders.AUTHORIZATION, bearer(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/debtors").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(jsonPath("$.students").value(0));
        balance(owner, studentId).andExpect(jsonPath("$.debt").value(400000.0));
    }

    @Test
    void teachersCannotSeeMoney() throws Exception {
        String link = JsonPath.read(call(owner, "/api/v1/invites", "{\"role\":\"TEACHER\",\"phone\":\"+998935550000\"}")
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, 8003L, "+998935550000");
        String teacher = loginAndGetToken(8003L, "Teacher");

        balance(teacher, studentId).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/debtors").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private String adminOfOwnersCenter() throws Exception {
        String link = JsonPath.read(call(owner, "/api/v1/invites", "{\"role\":\"ADMIN\",\"phone\":\"+998935550001\"}")
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, 8004L, "+998935550001");
        return loginAndGetToken(8004L, "Admin");
    }

    private void enroll(String student, LocalDate joinedOn, String feeOverride) throws Exception {
        call(owner, "/api/v1/groups/" + groupId + "/enrollments", "{\"studentId\":\"" + student + "\",\"joinedOn\":\""
                + joinedOn + "\"" + (feeOverride == null ? "" : ",\"feeOverride\":" + feeOverride) + "}")
                .andExpect(status().isCreated());
    }

    private ResultActions pay(String token, String student, String amount, String key) throws Exception {
        return mvc.perform(post("/api/v1/students/" + student + "/payments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"method\":\"CASH\"}"));
    }

    private ResultActions voidPayment(String token, String paymentId, String reason) throws Exception {
        return mvc.perform(post("/api/v1/payments/" + paymentId + "/void").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions balance(String token, String student) throws Exception {
        return mvc.perform(get("/api/v1/students/" + student + "/balance").header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private ResultActions call(String token, String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static LocalDate today() {
        return LocalDate.now(BusinessTime.ZONE);
    }

    private static LocalDate monthStart(int monthsFromNow) {
        return YearMonth.from(today()).plusMonths(monthsFromNow).atDay(1);
    }
}

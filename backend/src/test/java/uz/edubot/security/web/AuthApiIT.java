package uz.edubot.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uz.edubot.TestcontainersConfiguration;
import uz.edubot.support.TelegramInitDataSigner;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthApiIT {

    private static final String BOT_TOKEN = "123456:TEST-ONLY-TOKEN";
    private static final long PLATFORM_ADMIN_ID = 999000L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate table center_membership, center_settings, subscription, entitlement_override, "
                + "education_center, app_user cascade");
    }

    @Test
    void newUserWithoutMembershipsGetsUnboundToken() throws Exception {
        mvc.perform(login(1001L, "Ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.me.user.firstName").value("Ali"))
                .andExpect(jsonPath("$.me.user.platformRole").value(nullValue()))
                .andExpect(jsonPath("$.me.memberships", hasSize(0)))
                .andExpect(jsonPath("$.me.activeMembershipId").value(nullValue()));

        assertThat(countUsers(1001L)).isEqualTo(1);
    }

    @Test
    void repeatedLoginUpdatesProfileWithoutDuplicatingUser() throws Exception {
        mvc.perform(login(1002L, "Old")).andExpect(status().isOk());
        mvc.perform(login(1002L, "New"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.user.firstName").value("New"));

        assertThat(countUsers(1002L)).isEqualTo(1);
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        String forged = TelegramInitDataSigner.sign("1:WRONG", 1003L, "Eve", Instant.now());

        mvc.perform(post("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                        .content(json(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INIT_DATA"));

        assertThat(countUsers(1003L)).isZero();
    }

    @Test
    void blankInitDataFailsValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initData\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void meRequiresToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void meRejectsGarbageToken() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        String token = loginAndGetToken(1004L, "Vali");

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.firstName").value("Vali"));
    }

    @Test
    void configuredPlatformAdminGetsSuperAdminRole() throws Exception {
        mvc.perform(login(PLATFORM_ADMIN_ID, "Owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.user.platformRole").value("SUPER_ADMIN"));
    }

    @Test
    void singleMembershipIsSelectedAutomatically() throws Exception {
        loginAndGetToken(1005L, "Teacher");
        UUID centerId = createCenter("Alpha", "ACTIVE");
        UUID membershipId = createMembership(centerId, 1005L, "TEACHER");

        mvc.perform(login(1005L, "Teacher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.memberships", hasSize(1)))
                .andExpect(jsonPath("$.me.activeMembershipId").value(membershipId.toString()));
    }

    @Test
    void membershipsOfInactiveCentersAreIgnored() throws Exception {
        loginAndGetToken(1006L, "Admin");
        createMembership(createCenter("Suspended", "SUSPENDED"), 1006L, "ADMIN");

        mvc.perform(login(1006L, "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.memberships", hasSize(0)));
    }

    @Test
    void userWithSeveralMembershipsSelectsOne() throws Exception {
        loginAndGetToken(1007L, "Multi");
        UUID first = createMembership(createCenter("Alpha", "ACTIVE"), 1007L, "ADMIN");
        createMembership(createCenter("Beta", "ACTIVE"), 1007L, "PARENT");

        String unbound = JsonPath.read(mvc.perform(login(1007L, "Multi"))
                .andExpect(jsonPath("$.me.memberships", hasSize(2)))
                .andExpect(jsonPath("$.me.activeMembershipId").value(nullValue()))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        String bound = JsonPath.read(mvc.perform(post("/api/v1/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, bearer(unbound))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + first + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.activeMembershipId").value(first.toString()))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(bound)))
                .andExpect(jsonPath("$.activeMembershipId").value(first.toString()));
    }

    @Test
    void cannotSelectAnotherUsersMembership() throws Exception {
        loginAndGetToken(1008L, "Victim");
        UUID victimMembership = createMembership(createCenter("Alpha", "ACTIVE"), 1008L, "OWNER");
        String attackerToken = loginAndGetToken(1009L, "Attacker");

        mvc.perform(post("/api/v1/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, bearer(attackerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + victimMembership + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_NOT_FOUND"));
    }

    @Test
    void revokedMembershipIsNotReportedAsActive() throws Exception {
        loginAndGetToken(1010L, "Revoked");
        UUID membershipId = createMembership(createCenter("Alpha", "ACTIVE"), 1010L, "ADMIN");
        String token = loginAndGetToken(1010L, "Revoked");

        jdbc.update("update center_membership set status = 'REVOKED' where id = ?", membershipId);

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMembershipId").value(nullValue()))
                .andExpect(jsonPath("$.memberships", hasSize(0)));
    }

    private org.springframework.test.web.servlet.RequestBuilder login(long telegramId, String firstName) {
        String initData = TelegramInitDataSigner.sign(BOT_TOKEN, telegramId, firstName, Instant.now());
        return post("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON).content(json(initData));
    }

    private String loginAndGetToken(long telegramId, String firstName) throws Exception {
        String body = mvc.perform(login(telegramId, firstName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String json(String initData) {
        return "{\"initData\":\"" + initData + "\"}";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private int countUsers(long telegramId) {
        Integer count = jdbc.queryForObject("select count(*) from app_user where telegram_id = ?",
                Integer.class, telegramId);
        return count == null ? 0 : count;
    }

    private UUID createCenter(String name, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into education_center (id, name, status, created_at, updated_at) "
                + "values (?, ?, ?, now(), now())", id, name, status);
        return id;
    }

    private UUID createMembership(UUID centerId, long telegramId, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into center_membership (id, center_id, user_id, role, status, created_at, updated_at) "
                + "select ?, ?, u.id, ?, 'ACTIVE', now(), now() from app_user u where u.telegram_id = ?",
                id, centerId, role, telegramId);
        return id;
    }
}

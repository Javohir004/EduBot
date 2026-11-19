package uz.edubot.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import uz.edubot.TestcontainersConfiguration;
import uz.edubot.invite.InviteService;
import uz.edubot.user.TelegramProfile;

/**
 * Base class for API tests against a real PostgreSQL (Testcontainers). All subclasses share one
 * Spring context; each test starts from empty tenant data (subscription plans are kept).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    protected static final String BOT_TOKEN = "123456:TEST-ONLY-TOKEN";
    protected static final long PLATFORM_ADMIN_ID = 999000L;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected InviteService inviteService;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate table student_guardian, guardian, student, teacher, invite, center_membership, center_settings, subscription, "
                + "entitlement_override, education_center, app_user cascade");
    }

    protected RequestBuilder login(long telegramId, String firstName) {
        String initData = TelegramInitDataSigner.sign(BOT_TOKEN, telegramId, firstName, Instant.now());
        return post("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                .content("{\"initData\":\"" + initData + "\"}");
    }

    protected String loginAndGetToken(long telegramId, String firstName) throws Exception {
        String body = mvc.perform(login(telegramId, firstName))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /** Creates a center through the platform API and returns the owner invite link. */
    protected String createCenterInvite(String centerName, String ownerPhone) throws Exception {
        String adminToken = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");
        String body = mvc.perform(post("/api/v1/platform/centers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + centerName + "\",\"ownerPhone\":\"" + ownerPhone
                                + "\",\"planCode\":\"START\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ownerInvite.link");
    }

    /** Creates a center, accepts its owner invite as the given Telegram user and returns a bound token. */
    protected String newCenterOwner(String centerName, long telegramId, String ownerPhone) throws Exception {
        acceptInvite(createCenterInvite(centerName, ownerPhone), telegramId, ownerPhone);
        return loginAndGetToken(telegramId, "Owner");
    }

    protected void acceptInvite(String link, long telegramId, String phone) {
        inviteService.accept(link.substring(link.indexOf("start=") + "start=".length()),
                new TelegramProfile(telegramId, "User" + telegramId, null, null, "uz"), phone);
    }

    protected UUID centerIdOf(String token) throws Exception {
        String body = mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.memberships[0].centerId"));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}

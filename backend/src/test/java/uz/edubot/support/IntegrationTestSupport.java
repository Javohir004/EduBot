package uz.edubot.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import uz.edubot.TestcontainersConfiguration;

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

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("truncate table invite, center_membership, center_settings, subscription, "
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

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}

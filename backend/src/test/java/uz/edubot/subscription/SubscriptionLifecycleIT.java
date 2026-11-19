package uz.edubot.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.support.IntegrationTestSupport;

class SubscriptionLifecycleIT extends IntegrationTestSupport {

    @Autowired
    private SubscriptionExpiryJob expiryJob;

    private String owner;
    private UUID centerId;

    @BeforeEach
    void setUp() throws Exception {
        owner = newCenterOwner("Alpha", 9001L, "+998901000501");
        centerId = centerIdOf(owner);
    }

    @Test
    void activeTrialShowsUsageAndLimits() throws Exception {
        createStudent().andExpect(status().isCreated());

        mvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.phase").value("ACTIVE"))
                .andExpect(jsonPath("$.planCode").value("START"))
                .andExpect(jsonPath("$.usage.students").value(1))
                .andExpect(jsonPath("$.limits.MAX_STUDENTS").value(100));
    }

    @Test
    void expiredCenterIsReadOnlyButReadable() throws Exception {
        createStudent();
        jdbc.update("update subscription set starts_at = now() - interval '60 days', ends_at = now() - interval '10 days', grace_until = now() - interval '5 days'");

        createStudent().andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("SUBSCRIPTION_EXPIRED"));
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.phase").value("EXPIRED"));
    }

    @Test
    void gracePeriodStillAllowsWrites() throws Exception {
        jdbc.update("update subscription set starts_at = now() - interval '60 days', ends_at = now() - interval '1 day', grace_until = now() + interval '4 days'");

        createStudent().andExpect(status().isCreated());
        mvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.phase").value("GRACE"));
    }

    @Test
    void platformAdminRenewsAndChangesPlan() throws Exception {
        jdbc.update("update subscription set starts_at = now() - interval '60 days', ends_at = now() - interval '10 days', grace_until = now() - interval '5 days'");
        String admin = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");

        mvc.perform(post("/api/v1/platform/centers/" + centerId + "/subscriptions").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"STANDARD\",\"months\":3}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/centers/" + centerId + "/subscriptions").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planCode\":\"STANDARD\",\"months\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("ACTIVE"))
                .andExpect(jsonPath("$.planCode").value("STANDARD"));

        createStudent().andExpect(status().isCreated());
        mvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.limits.MAX_STUDENTS").value(300));
    }

    @Test
    void dailyJobExpiresAndRemindsOwnersOnce() throws Exception {
        jdbc.update("update subscription set starts_at = now() - interval '60 days', ends_at = now() + interval '3 days' - interval '1 hour', "
                + "grace_until = now() + interval '8 days'");
        expiryJob.run();
        expiryJob.run();
        assertThat(count("select count(*) from notification where type = 'SUBSCRIPTION_EXPIRING'")).isEqualTo(1);

        jdbc.update("update subscription set starts_at = now() - interval '60 days', ends_at = now() - interval '10 days', grace_until = now() - interval '5 days'");
        expiryJob.run();
        assertThat(count("select count(*) from subscription where status = 'EXPIRED'")).isEqualTo(1);
    }

    private ResultActions createStudent() throws Exception {
        return mvc.perform(post("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"student\":{\"firstName\":\"Ali\",\"lastName\":\"Valiyev\"}}"));
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}

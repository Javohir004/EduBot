package uz.edubot.platform;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.edubot.support.IntegrationTestSupport;

class PlatformApiIT extends IntegrationTestSupport {

    private static final String CREATE_ALPHA =
            "{\"name\":\"Alpha Academy\",\"ownerPhone\":\"+998901112233\",\"planCode\":\"STANDARD\",\"trialDays\":7}";

    @Test
    void regularUserCannotUsePlatformApi() throws Exception {
        String token = loginAndGetToken(3001L, "User");

        mvc.perform(get("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_ALPHA))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesCenterWithTrialAndOwnerInvite() throws Exception {
        String admin = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");

        mvc.perform(post("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_ALPHA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.center.name").value("Alpha Academy"))
                .andExpect(jsonPath("$.center.status").value("ACTIVE"))
                .andExpect(jsonPath("$.center.planCode").value("STANDARD"))
                .andExpect(jsonPath("$.center.subscriptionStatus").value("TRIAL"))
                .andExpect(jsonPath("$.ownerInvite.link", startsWith("https://t.me/edubot_test_bot?start=inv_")));

        mvc.perform(get("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].subscriptionStatus").value("TRIAL"));
    }

    @Test
    void unknownPlanAndInvalidPhoneAreRejected() throws Exception {
        String admin = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");

        mvc.perform(post("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"ownerPhone\":\"+998901112233\",\"planCode\":\"GOLD\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        mvc.perform(post("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"ownerPhone\":\"12\",\"planCode\":\"START\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHONE"));
        mvc.perform(get("/api/v1/platform/centers").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void suspendedCenterDisappearsFromOwnersMemberships() throws Exception {
        String admin = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");
        String centerId = JsonPath.read(mvc.perform(post("/api/v1/platform/centers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_ALPHA))
                .andReturn().getResponse().getContentAsString(), "$.center.id");

        mvc.perform(patch("/api/v1/platform/centers/" + centerId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void plansAreListed() throws Exception {
        String admin = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");

        mvc.perform(get("/api/v1/platform/plans").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].code").value("START"));
    }
}

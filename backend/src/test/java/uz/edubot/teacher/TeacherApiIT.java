package uz.edubot.teacher;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.support.IntegrationTestSupport;

class TeacherApiIT extends IntegrationTestSupport {

    @Test
    void createsSearchesAndArchivesTeachers() throws Exception {
        String owner = newCenterOwner("Alpha", 5001L, "+998901000101");
        String id = id(create(owner, "{\"fullName\":\"Aziza Karimova\",\"phone\":\"935551122\",\"subjects\":\"IELTS\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+998935551122"))
                .andExpect(jsonPath("$.linked").value(false)));
        create(owner, "{\"fullName\":\"Bekzod Toirov\"}");

        mvc.perform(get("/api/v1/teachers?q=aziza").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(post("/api/v1/teachers/" + id + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void teacherInviteLinksProfileAndCannotBeReissued() throws Exception {
        String owner = newCenterOwner("Alpha", 5002L, "+998901000102");
        String id = id(create(owner, "{\"fullName\":\"Aziza Karimova\",\"phone\":\"+998935551122\"}"));

        String link = JsonPath.read(mvc.perform(post("/api/v1/teachers/" + id + "/invite")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, 5003L, "+998935551122");

        mvc.perform(get("/api/v1/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.linked").value(true));
        mvc.perform(post("/api/v1/teachers/" + id + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_LINKED"));
        mvc.perform(login(5003L, "Teacher"))
                .andExpect(jsonPath("$.me.memberships[0].role").value("TEACHER"));
    }

    @Test
    void inviteWithoutPhoneIsRejected() throws Exception {
        String owner = newCenterOwner("Alpha", 5004L, "+998901000104");
        String id = id(create(owner, "{\"fullName\":\"No Phone\"}"));

        mvc.perform(post("/api/v1/teachers/" + id + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));
    }

    @Test
    void anotherCenterCannotSeeTeachers() throws Exception {
        String ownerA = newCenterOwner("Alpha", 5005L, "+998901000105");
        String ownerB = newCenterOwner("Beta", 5006L, "+998901000106");
        String id = id(create(ownerA, "{\"fullName\":\"Aziza Karimova\",\"phone\":\"+998935551122\"}"));

        mvc.perform(get("/api/v1/teachers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/teachers/" + id + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void teacherLimitIsEnforced() throws Exception {
        String owner = newCenterOwner("Alpha", 5007L, "+998901000107");
        jdbc.update("insert into entitlement_override (center_id, entitlement_key, value) values (?, 'MAX_TEACHERS', 1)",
                centerIdOf(owner));
        create(owner, "{\"fullName\":\"One\"}").andExpect(status().isCreated());

        create(owner, "{\"fullName\":\"Two\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
    }

    private ResultActions create(String token, String json) throws Exception {
        return mvc.perform(post("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}

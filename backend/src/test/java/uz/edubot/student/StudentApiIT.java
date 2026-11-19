package uz.edubot.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.support.IntegrationTestSupport;
import uz.edubot.tenancy.TenantContext;

class StudentApiIT extends IntegrationTestSupport {

    private static final String ALI = """
            {"student":{"firstName":"Ali","lastName":"Valiyev","birthDate":"2012-05-01","phone":"90 111 22 33"},
             "guardians":[{"fullName":"Dilnoza Valiyeva","phone":"+998 93 444 55 66","relation":"MOTHER"}]}""";

    @Autowired
    private StudentRepository studentRepository;

    @Test
    void createsStudentWithGuardianAndNormalizedPhones() throws Exception {
        String owner = newCenterOwner("Alpha", 4001L, "+998901000001");

        createStudent(owner, ALI)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+998901112233"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.guardians", hasSize(1)))
                .andExpect(jsonPath("$.guardians[0].phone").value("+998934445566"))
                .andExpect(jsonPath("$.guardians[0].relation").value("MOTHER"))
                .andExpect(jsonPath("$.guardians[0].linked").value(false));
    }

    @Test
    void searchesByNameAndPhoneAndFiltersArchived() throws Exception {
        String owner = newCenterOwner("Alpha", 4002L, "+998901000002");
        String aliId = id(createStudent(owner, ALI));
        createStudent(owner, student("Bobur", "Karimov"));

        mvc.perform(get("/api/v1/students?q=vali").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].firstName").value("Ali"));
        mvc.perform(get("/api/v1/students?q=1112233").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/v1/students?q=%25").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(post("/api/v1/students/" + aliId + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/students?status=ARCHIVED").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items[0].id").value(aliId));
    }

    @Test
    void siblingsShareOneGuardian() throws Exception {
        String owner = newCenterOwner("Alpha", 4003L, "+998901000003");
        createStudent(owner, ALI);
        createStudent(owner, ALI.replace("\"Ali\"", "\"Vali\""));

        Integer guardians = jdbc.queryForObject("select count(*) from guardian", Integer.class);
        Integer links = jdbc.queryForObject("select count(*) from student_guardian", Integer.class);
        assertThat(guardians).isEqualTo(1);
        assertThat(links).isEqualTo(2);
    }

    @Test
    void updateRequiresCurrentVersion() throws Exception {
        String owner = newCenterOwner("Alpha", 4004L, "+998901000004");
        String id = id(createStudent(owner, ALI));
        String body = "{\"student\":{\"firstName\":\"Ali\",\"lastName\":\"Aliyev\"},\"version\":%s}";

        mvc.perform(put("/api/v1/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("0")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Aliyev"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        mvc.perform(put("/api/v1/students/" + id).header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted("null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));
    }

    @Test
    void planLimitBlocksCreateAndRestore() throws Exception {
        String owner = newCenterOwner("Alpha", 4005L, "+998901000005");
        jdbc.update("insert into entitlement_override (center_id, entitlement_key, value) values (?, 'MAX_STUDENTS', 1)",
                centerIdOf(owner));
        String first = id(createStudent(owner, student("A", "One")));

        createStudent(owner, student("B", "Two"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));

        mvc.perform(post("/api/v1/students/" + first + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(owner)));
        String second = id(createStudent(owner, student("B", "Two")));
        mvc.perform(post("/api/v1/students/" + first + "/restore").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        assertThat(second).isNotBlank();
    }

    @Test
    void anotherCenterCannotReadOrChangeStudents() throws Exception {
        String ownerA = newCenterOwner("Alpha", 4006L, "+998901000006");
        String ownerB = newCenterOwner("Beta", 4007L, "+998901000007");
        String studentOfA = id(createStudent(ownerA, ALI));
        String guardianOfA = JsonPath.read(mvc.perform(get("/api/v1/students/" + studentOfA)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerA))).andReturn().getResponse().getContentAsString(),
                "$.guardians[0].id");
        String b = bearer(ownerB);

        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, b))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/students/" + studentOfA).header(HttpHeaders.AUTHORIZATION, b))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/students/" + studentOfA).header(HttpHeaders.AUTHORIZATION, b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"student\":{\"firstName\":\"X\",\"lastName\":\"Y\"},\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/students/" + studentOfA + "/archive").header(HttpHeaders.AUTHORIZATION, b))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/students/" + studentOfA + "/guardians").header(HttpHeaders.AUTHORIZATION, b)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"phone\":\"+998901234567\",\"relation\":\"FATHER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/students/" + studentOfA + "/guardians/" + guardianOfA)
                        .header(HttpHeaders.AUTHORIZATION, b))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/students/" + studentOfA + "/guardians/" + guardianOfA + "/invite")
                        .header(HttpHeaders.AUTHORIZATION, b))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/students/" + studentOfA).header(HttpHeaders.AUTHORIZATION, bearer(ownerA)))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void repositoryFindByIdIsTenantFiltered() throws Exception {
        String ownerA = newCenterOwner("Alpha", 4008L, "+998901000008");
        String ownerB = newCenterOwner("Beta", 4009L, "+998901000009");
        UUID studentOfA = UUID.fromString(id(createStudent(ownerA, ALI)));

        assertThat(TenantContext.runAs(centerIdOf(ownerB), () -> studentRepository.findById(studentOfA))).isEmpty();
        assertThat(TenantContext.runAs(centerIdOf(ownerA), () -> studentRepository.findById(studentOfA))).isPresent();
        // No tenant bound: fails closed.
        assertThat(studentRepository.findById(studentOfA)).isEmpty();
        assertThat(studentRepository.count()).isZero();
    }

    @Test
    void parentInviteLinksGuardianAccount() throws Exception {
        String owner = newCenterOwner("Alpha", 4010L, "+998901000010");
        String body = createStudent(owner, ALI).andReturn().getResponse().getContentAsString();
        String studentId = JsonPath.read(body, "$.id");
        String guardianId = JsonPath.read(body, "$.guardians[0].id");

        String link = JsonPath.read(mvc.perform(post("/api/v1/students/" + studentId + "/guardians/" + guardianId
                        + "/invite").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, 4011L, "+998934445566");

        mvc.perform(get("/api/v1/students/" + studentId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.guardians[0].linked").value(true));
        mvc.perform(login(4011L, "Parent"))
                .andExpect(jsonPath("$.me.memberships[0].role").value("PARENT"));
    }

    @Test
    void teachersAndParentsCannotUseStudentAdminApi() throws Exception {
        String owner = newCenterOwner("Alpha", 4012L, "+998901000012");
        String teacherLink = JsonPath.read(mvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"phone\":\"+998935550000\"}"))
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(teacherLink, 4013L, "+998935550000");
        String teacher = loginAndGetToken(4013L, "Teacher");

        mvc.perform(get("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isForbidden());
        createStudent(teacher, ALI).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isForbidden());
    }

    private ResultActions createStudent(String token, String json) throws Exception {
        return mvc.perform(post("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String student(String firstName, String lastName) {
        return "{\"student\":{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\"}}";
    }
}

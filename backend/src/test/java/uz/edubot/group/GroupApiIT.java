package uz.edubot.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.support.IntegrationTestSupport;

class GroupApiIT extends IntegrationTestSupport {

    @Test
    void createsGroupWithTeacherScheduleAndMembers() throws Exception {
        String owner = newCenterOwner("Alpha", 6001L, "+998901000201");
        String teacherId = createTeacher(owner, "Aziza Karimova", "+998935551122");
        String groupId = createGroup(owner, "IELTS 7+", teacherId, null);
        String studentId = createStudent(owner, "Ali", "Valiyev");

        mvc.perform(put("/api/v1/groups/" + groupId + "/schedule").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"day\":\"MONDAY\",\"startTime\":\"14:00\",\"endTime\":\"15:30\",\"room\":\"101\"},"
                                + "{\"day\":\"THURSDAY\",\"startTime\":\"14:00\",\"endTime\":\"15:30\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        enroll(owner, groupId, studentId).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.teacherName").value("Aziza Karimova"))
                .andExpect(jsonPath("$.monthlyFee").value(450000.0))
                .andExpect(jsonPath("$.studentCount").value(1))
                .andExpect(jsonPath("$.schedule[0].day").value("MONDAY"));
        mvc.perform(get("/api/v1/groups/" + groupId + "/members").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$[0].lastName").value("Valiyev"));
        mvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.items[0].studentCount").value(1));
        mvc.perform(get("/api/v1/students/" + studentId + "/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$[0].groupName").value("IELTS 7+"));
    }

    @Test
    void enrollmentRulesDuplicatesCapacityAndHistory() throws Exception {
        String owner = newCenterOwner("Alpha", 6002L, "+998901000202");
        String groupId = createGroup(owner, "Small", null, 1);
        String ali = createStudent(owner, "Ali", "Valiyev");
        String vali = createStudent(owner, "Vali", "Aliyev");

        String enrollmentId = JsonPath.read(enroll(owner, groupId, ali).andReturn().getResponse().getContentAsString(), "$.id");
        enroll(owner, groupId, ali).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
        enroll(owner, groupId, vali).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_FULL"));

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/end").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.status").value("ENDED"));
        enroll(owner, groupId, ali).andExpect(status().isCreated());

        Integer rows = jdbc.queryForObject("select count(*) from enrollment", Integer.class);
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void archivingStudentEndsEnrollmentsAndCompletingGroupBlocksEnrollment() throws Exception {
        String owner = newCenterOwner("Alpha", 6003L, "+998901000203");
        String groupId = createGroup(owner, "G1", null, null);
        String ali = createStudent(owner, "Ali", "Valiyev");
        enroll(owner, groupId, ali);

        mvc.perform(post("/api/v1/students/" + ali + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(owner)));
        mvc.perform(get("/api/v1/groups/" + groupId + "/members").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/v1/groups/" + groupId + "/complete").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        enroll(owner, groupId, createStudent(owner, "Vali", "Aliyev"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_COMPLETED"));
    }

    @Test
    void teacherSeesOnlyOwnGroupsAndCannotChangeThem() throws Exception {
        String owner = newCenterOwner("Alpha", 6004L, "+998901000204");
        String teacherId = createTeacher(owner, "Aziza", "+998935551122");
        String own = createGroup(owner, "Own group", teacherId, null);
        String other = createGroup(owner, "Other group", null, null);
        enroll(owner, own, createStudent(owner, "Ali", "Valiyev"));
        String link = JsonPath.read(mvc.perform(post("/api/v1/teachers/" + teacherId + "/invite")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, 6005L, "+998935551122");
        String teacher = loginAndGetToken(6005L, "Aziza");

        mvc.perform(get("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Own group"));
        mvc.perform(get("/api/v1/groups/" + other).header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/groups/" + other + "/members").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/groups/" + own + "/members").header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(groupJson("X", null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherCenterCannotTouchGroupsOrMixData() throws Exception {
        String ownerA = newCenterOwner("Alpha", 6006L, "+998901000206");
        String ownerB = newCenterOwner("Beta", 6007L, "+998901000207");
        String groupOfA = createGroup(ownerA, "A group", null, null);
        String studentOfA = createStudent(ownerA, "Ali", "Valiyev");
        String teacherOfA = createTeacher(ownerA, "Aziza", null);
        String groupOfB = createGroup(ownerB, "B group", null, null);

        mvc.perform(get("/api/v1/groups/" + groupOfA).header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(status().isNotFound());
        enroll(ownerB, groupOfA, createStudent(ownerB, "Bek", "B")).andExpect(status().isNotFound());
        enroll(ownerB, groupOfB, studentOfA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
        mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(ownerB))
                        .contentType(MediaType.APPLICATION_JSON).content(groupJson("X", teacherOfA, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEACHER_NOT_FOUND"));
        mvc.perform(get("/api/v1/students/" + studentOfA + "/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void groupLimitAndScheduleValidation() throws Exception {
        String owner = newCenterOwner("Alpha", 6008L, "+998901000208");
        jdbc.update("insert into entitlement_override (center_id, entitlement_key, value) values (?, 'MAX_GROUPS', 1)",
                centerIdOf(owner));
        String groupId = createGroup(owner, "One", null, null);

        mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(groupJson("Two", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
        mvc.perform(put("/api/v1/groups/" + groupId + "/schedule").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"day\":\"MONDAY\",\"startTime\":\"14:00\",\"endTime\":\"15:00\"},"
                                + "{\"day\":\"MONDAY\",\"startTime\":\"14:00\",\"endTime\":\"16:00\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLOT"));
    }

    private ResultActions enroll(String token, String groupId, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/groups/" + groupId + "/enrollments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":\"" + studentId + "\"}"));
    }

    private String createGroup(String token, String name, String teacherId, Integer capacity) throws Exception {
        return id(mvc.perform(post("/api/v1/groups").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(groupJson(name, teacherId, capacity)))
                .andExpect(status().isCreated()));
    }

    private static String groupJson(String name, String teacherId, Integer capacity) {
        return "{\"name\":\"" + name + "\",\"monthlyFee\":450000,\"startDate\":\"2026-09-01\""
                + (teacherId == null ? "" : ",\"teacherId\":\"" + teacherId + "\"")
                + (capacity == null ? "" : ",\"capacity\":" + capacity) + "}";
    }

    private String createTeacher(String token, String name, String phone) throws Exception {
        return id(mvc.perform(post("/api/v1/teachers").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"" + name + "\"" + (phone == null ? "" : ",\"phone\":\"" + phone + "\"") + "}")));
    }

    private String createStudent(String token, String firstName, String lastName) throws Exception {
        return id(mvc.perform(post("/api/v1/students").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"student\":{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\"}}")));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}

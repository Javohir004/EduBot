package uz.edubot.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import uz.edubot.common.time.BusinessTime;
import uz.edubot.notification.NotificationDispatcher;
import uz.edubot.support.FakeNotificationSender;
import uz.edubot.support.IntegrationTestSupport;

class AttendanceApiIT extends IntegrationTestSupport {

    private static final String PARENT_PHONE = "+998934445566";
    private static final long PARENT_TELEGRAM_ID = 7900L;

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private FakeNotificationSender sender;

    private String owner;
    private String groupId;
    private String aliId;
    private String valiId;

    @BeforeEach
    void setUp() throws Exception {
        sender.reset();
        owner = newCenterOwner("Alpha", 7001L, "+998901000301");
        groupId = id(post(owner, "/api/v1/groups",
                "{\"name\":\"IELTS\",\"monthlyFee\":450000,\"startDate\":\"2026-01-01\"}"));
        aliId = createStudentWithParent("Ali", "Valiyev");
        valiId = id(post(owner, "/api/v1/students", "{\"student\":{\"firstName\":\"Vali\",\"lastName\":\"Aliyev\"}}"));
        enroll(aliId);
        enroll(valiId);
    }

    @Test
    void marksAttendanceAndRemarkingUpdatesTheSameRow() throws Exception {
        String lessonId = openLesson(owner, today(), "14:00");

        mark(owner, lessonId, aliId, "PRESENT").andExpect(status().isOk());
        mark(owner, lessonId, aliId, "LATE")
                .andExpect(jsonPath("$.students", hasSize(2)))
                // Sorted by last name: Aliyev Vali, Valiyev Ali.
                .andExpect(jsonPath("$.students[0].firstName").value("Vali"))
                .andExpect(jsonPath("$.students[0].status").value(nullValue()))
                .andExpect(jsonPath("$.students[1].status").value("LATE"));

        assertThat(count("select count(*) from attendance")).isEqualTo(1);
        // Opening the same lesson again returns the same row.
        assertThat(openLesson(owner, today(), "14:00")).isEqualTo(lessonId);
    }

    @Test
    void concurrentSavesProduceOneMarkPerStudent() throws Exception {
        String lessonId = openLesson(owner, today(), "14:00");
        String body = "[{\"studentId\":\"" + aliId + "\",\"status\":\"PRESENT\"},"
                + "{\"studentId\":\"" + valiId + "\",\"status\":\"ABSENT\"}]";
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> call = () -> {
                start.await();
                return mvc.perform(put("/api/v1/lessons/" + lessonId + "/attendance")
                                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            };
            results.add(pool.submit(call));
        }
        start.countDown();
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(200);
        }
        pool.shutdown();

        assertThat(count("select count(*) from attendance")).isEqualTo(2);
    }

    @Test
    void absenceNotifiesLinkedParentAfterDelayAndCorrectionCancelsIt() throws Exception {
        String lessonId = openLesson(owner, today(), "14:00");

        mark(owner, lessonId, aliId, "ABSENT");
        assertThat(count("select count(*) from notification where status = 'PENDING' and scheduled_at > now()")).isEqualTo(1);
        // Not due yet: nothing is sent.
        dispatcher.dispatchDue();
        assertThat(sender.sent).isEmpty();

        mark(owner, lessonId, aliId, "PRESENT");
        assertThat(count("select count(*) from notification where status = 'CANCELLED'")).isEqualTo(1);

        mark(owner, lessonId, aliId, "ABSENT");
        jdbc.update("update notification set scheduled_at = now() - interval '1 minute'");
        dispatcher.dispatchDue();
        dispatcher.dispatchDue();

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.getFirst().chatId()).isEqualTo(PARENT_TELEGRAM_ID);
        assertThat(sender.sent.getFirst().text()).contains("Ali Valiyev", "IELTS", "kelmadi");
        assertThat(count("select count(*) from notification where status = 'SENT'")).isEqualTo(1);
    }

    @Test
    void blockedBotMarksNotificationFailedAndUserBlocked() throws Exception {
        sender.blocked.add(PARENT_TELEGRAM_ID);
        String lessonId = openLesson(owner, today(), "14:00");
        mark(owner, lessonId, aliId, "ABSENT");
        jdbc.update("update notification set scheduled_at = now() - interval '1 minute'");

        dispatcher.dispatchDue();

        assertThat(count("select count(*) from notification where status = 'FAILED'")).isEqualTo(1);
        assertThat(count("select count(*) from app_user where telegram_id = " + PARENT_TELEGRAM_ID + " and bot_blocked"))
                .isEqualTo(1);
    }

    @Test
    void disabledAbsenceNotificationsCreateNothing() throws Exception {
        jdbc.update("update center_settings set absence_notifications_enabled = false");
        String lessonId = openLesson(owner, today(), "14:00");

        mark(owner, lessonId, aliId, "ABSENT");

        assertThat(count("select count(*) from notification")).isZero();
    }

    @Test
    void rejectsFutureLessonsAndStudentsOutsideTheGroup() throws Exception {
        post(owner, "/api/v1/groups/" + groupId + "/lessons",
                "{\"date\":\"" + today().plusDays(1) + "\",\"startTime\":\"14:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LESSON_IN_FUTURE"));

        String lessonId = openLesson(owner, today(), "14:00");
        String stranger = id(post(owner, "/api/v1/students", "{\"student\":{\"firstName\":\"X\",\"lastName\":\"Y\"}}"));
        mark(owner, lessonId, stranger, "PRESENT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_IN_GROUP"));
    }

    @Test
    void teacherMarksOwnRecentLessonsOnly() throws Exception {
        String teacherId = id(post(owner, "/api/v1/teachers", "{\"fullName\":\"Aziza\",\"phone\":\"+998935551122\"}"));
        String ownGroup = id(post(owner, "/api/v1/groups", "{\"name\":\"Own\",\"monthlyFee\":1,\"startDate\":\"2026-01-01\","
                + "\"teacherId\":\"" + teacherId + "\"}"));
        post(owner, "/api/v1/groups/" + ownGroup + "/enrollments", "{\"studentId\":\"" + aliId + "\",\"joinedOn\":\"2026-01-01\"}");
        acceptInvite(JsonPath.read(post(owner, "/api/v1/teachers/" + teacherId + "/invite", "")
                .andReturn().getResponse().getContentAsString(), "$.link"), 7002L, "+998935551122");
        String teacher = loginAndGetToken(7002L, "Aziza");

        String recent = openLesson(teacher, today(), "09:00", ownGroup);
        mark(teacher, recent, aliId, "PRESENT").andExpect(status().isOk());

        String old = openLesson(owner, today().minusDays(5), "09:00", ownGroup);
        mark(teacher, old, aliId, "ABSENT")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EDIT_WINDOW_CLOSED"));
        mark(owner, old, aliId, "ABSENT").andExpect(status().isOk());

        // Another teacher's group is invisible.
        post(teacher, "/api/v1/groups/" + groupId + "/lessons", "{\"date\":\"" + today() + "\",\"startTime\":\"14:00\"}")
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/lessons/" + openLesson(owner, today(), "14:00") + "/attendance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacher)))
                .andExpect(status().isNotFound());
    }

    @Test
    void todayListsScheduledLessonsAndHistoryShowsMarks() throws Exception {
        String day = today().getDayOfWeek().name();
        mvc.perform(put("/api/v1/groups/" + groupId + "/schedule").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"day\":\"" + day + "\",\"startTime\":\"14:00\",\"endTime\":\"15:30\"}]"));

        mvc.perform(get("/api/v1/lessons/today").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].groupName").value("IELTS"))
                .andExpect(jsonPath("$[0].lessonId").value(nullValue()))
                .andExpect(jsonPath("$[0].studentCount").value(2));

        String lessonId = openLesson(owner, today(), "14:00");
        mark(owner, lessonId, aliId, "PRESENT");
        mvc.perform(get("/api/v1/lessons/today").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$[0].lessonId").value(lessonId))
                .andExpect(jsonPath("$[0].markedCount").value(1));

        mvc.perform(get("/api/v1/groups/" + groupId + "/attendance?month=" + today().toString().substring(0, 7))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.lessons", hasSize(1)))
                .andExpect(jsonPath("$.students", hasSize(2)))
                .andExpect(jsonPath("$.students[?(@.firstName=='Ali')].marks." + lessonId).value("PRESENT"));
    }

    @Test
    void anotherCenterCannotReadOrMarkLessons() throws Exception {
        String lessonId = openLesson(owner, today(), "14:00");
        String ownerB = newCenterOwner("Beta", 7003L, "+998901000303");

        mvc.perform(get("/api/v1/lessons/" + lessonId + "/attendance").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(status().isNotFound());
        mark(ownerB, lessonId, aliId, "ABSENT").andExpect(status().isNotFound());
        post(ownerB, "/api/v1/groups/" + groupId + "/lessons", "{\"date\":\"" + today() + "\",\"startTime\":\"14:00\"}")
                .andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private String createStudentWithParent(String firstName, String lastName) throws Exception {
        String body = post(owner, "/api/v1/students", "{\"student\":{\"firstName\":\"" + firstName + "\",\"lastName\":\""
                + lastName + "\"},\"guardians\":[{\"fullName\":\"Ona\",\"phone\":\"" + PARENT_PHONE
                + "\",\"relation\":\"MOTHER\"}]}").andReturn().getResponse().getContentAsString();
        String studentId = JsonPath.read(body, "$.id");
        String guardianId = JsonPath.read(body, "$.guardians[0].id");
        String link = JsonPath.read(post(owner, "/api/v1/students/" + studentId + "/guardians/" + guardianId + "/invite", "")
                .andReturn().getResponse().getContentAsString(), "$.link");
        acceptInvite(link, PARENT_TELEGRAM_ID, PARENT_PHONE);
        return studentId;
    }

    private void enroll(String studentId) throws Exception {
        post(owner, "/api/v1/groups/" + groupId + "/enrollments",
                "{\"studentId\":\"" + studentId + "\",\"joinedOn\":\"2026-01-01\"}").andExpect(status().isCreated());
    }

    private String openLesson(String token, LocalDate date, String time) throws Exception {
        return openLesson(token, date, time, groupId);
    }

    private String openLesson(String token, LocalDate date, String time, String group) throws Exception {
        return JsonPath.read(post(token, "/api/v1/groups/" + group + "/lessons",
                        "{\"date\":\"" + date + "\",\"startTime\":\"" + time + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.lessonId");
    }

    private ResultActions mark(String token, String lessonId, String studentId, String status) throws Exception {
        return mvc.perform(put("/api/v1/lessons/" + lessonId + "/attendance").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"studentId\":\"" + studentId + "\",\"status\":\"" + status + "\"}]"));
    }

    private ResultActions post(String token, String path, String json) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static LocalDate today() {
        return LocalDate.now(BusinessTime.ZONE);
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}

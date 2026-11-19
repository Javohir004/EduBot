package uz.edubot.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import uz.edubot.center.MembershipRole;
import uz.edubot.common.error.ApiException;
import uz.edubot.support.IntegrationTestSupport;
import uz.edubot.user.TelegramProfile;

class InviteFlowIT extends IntegrationTestSupport {

    private static final String OWNER_PHONE = "+998901112233";

    @Test
    void ownerAcceptsPlatformInviteAndGetsCenterBoundToken() throws Exception {
        String link = createCenter("Alpha", OWNER_PHONE);

        AcceptedInvite accepted = inviteService.accept(startParam(link), profile(2001L, "Owner"), "998901112233");

        assertThat(accepted.centerName()).isEqualTo("Alpha");
        assertThat(accepted.role()).isEqualTo(MembershipRole.OWNER);
        mvc.perform(login(2001L, "Owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.me.memberships", hasSize(1)))
                .andExpect(jsonPath("$.me.memberships[0].role").value("OWNER"))
                .andExpect(jsonPath("$.me.activeMembershipId").isNotEmpty());
    }

    @Test
    void phoneMismatchIsRejectedAndGrantsNothing() throws Exception {
        String link = createCenter("Alpha", OWNER_PHONE);

        assertThatThrownBy(() -> inviteService.accept(startParam(link), profile(2002L, "Eve"), "+998909999999"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVITE_PHONE_MISMATCH");
        assertThat(countMemberships()).isZero();
    }

    @Test
    void inviteCannotBeReusedByAnotherUserButAcceptIsIdempotent() throws Exception {
        String link = createCenter("Alpha", OWNER_PHONE);
        inviteService.accept(startParam(link), profile(2003L, "Owner"), OWNER_PHONE);

        // Same user again (e.g. pressed the link twice): no error, no duplicate membership.
        inviteService.accept(startParam(link), profile(2003L, "Owner"), OWNER_PHONE);
        assertThat(countMemberships()).isEqualTo(1);

        assertThatThrownBy(() -> inviteService.accept(startParam(link), profile(2004L, "Other"), OWNER_PHONE))
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVITE_ALREADY_USED");
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> inviteService.accept("inv_doesnotexist", profile(2005L, "X"), OWNER_PHONE))
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVITE_NOT_FOUND");
    }

    @Test
    void ownerInvitesAdminAndRevokedInviteCannotBeAccepted() throws Exception {
        String ownerToken = ownerOf("Alpha", 2006L, OWNER_PHONE);

        String body = mvc.perform(post("/api/v1/invites").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"phone\":\"90 123 45 67\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+998*****4567"))
                .andExpect(jsonPath("$.link", startsWith("https://t.me/edubot_test_bot?start=inv_")))
                .andReturn().getResponse().getContentAsString();
        String inviteId = JsonPath.read(body, "$.id");
        String link = JsonPath.read(body, "$.link");

        mvc.perform(get("/api/v1/invites").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].link").doesNotExist());

        mvc.perform(delete("/api/v1/invites/" + inviteId).header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        assertThatThrownBy(() -> inviteService.accept(startParam(link), profile(2007L, "Admin"), "+998901234567"))
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo("INVITE_EXPIRED");
    }

    @Test
    void ownerCannotBeInvitedByCenterStaff() throws Exception {
        String ownerToken = ownerOf("Alpha", 2008L, OWNER_PHONE);

        mvc.perform(post("/api/v1/invites").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\",\"phone\":\"+998901234567\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVITE_ROLE_FORBIDDEN"));
    }

    @Test
    void teacherCannotCreateInvites() throws Exception {
        String ownerToken = ownerOf("Alpha", 2009L, OWNER_PHONE);
        String teacherLink = JsonPath.read(mvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"phone\":\"+998935556677\"}"))
                .andReturn().getResponse().getContentAsString(), "$.link");
        inviteService.accept(startParam(teacherLink), profile(2010L, "Teacher"), "+998935556677");
        String teacherToken = loginAndGetToken(2010L, "Teacher");

        mvc.perform(post("/api/v1/invites").header(HttpHeaders.AUTHORIZATION, bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PARENT\",\"phone\":\"+998901234567\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void centerCannotSeeOrRevokeAnotherCentersInvites() throws Exception {
        String ownerA = ownerOf("Alpha", 2011L, OWNER_PHONE);
        String ownerB = ownerOf("Beta", 2012L, "+998977778899");
        String inviteOfA = JsonPath.read(mvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PARENT\",\"phone\":\"+998901234567\"}"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/invites").header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/v1/invites/" + inviteOfA).header(HttpHeaders.AUTHORIZATION, bearer(ownerB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokedMembershipInvalidatesBoundToken() throws Exception {
        String ownerToken = ownerOf("Alpha", 2013L, OWNER_PHONE);
        jdbc.update("update center_membership set status = 'REVOKED'");

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isUnauthorized());
    }

    private String ownerOf(String centerName, long telegramId, String phone) throws Exception {
        String link = createCenter(centerName, phone);
        inviteService.accept(startParam(link), profile(telegramId, "Owner"), phone);
        return loginAndGetToken(telegramId, "Owner");
    }

    private String createCenter(String name, String ownerPhone) throws Exception {
        String adminToken = loginAndGetToken(PLATFORM_ADMIN_ID, "Admin");
        String body = mvc.perform(post("/api/v1/platform/centers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"ownerPhone\":\"" + ownerPhone + "\",\"planCode\":\"START\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ownerInvite.link");
    }

    private static String startParam(String link) {
        return link.substring(link.indexOf("start=") + "start=".length());
    }

    private static TelegramProfile profile(long telegramId, String firstName) {
        return new TelegramProfile(telegramId, firstName, null, null, "uz");
    }

    private int countMemberships() {
        Integer count = jdbc.queryForObject("select count(*) from center_membership", Integer.class);
        return count == null ? 0 : count;
    }
}

package uz.edubot.security.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uz.edubot.support.TelegramInitDataSigner;

class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "123456:TEST-ONLY-TOKEN";
    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    private final TelegramInitDataValidator validator = new TelegramInitDataValidator(
            new TelegramProperties(BOT_TOKEN, "edubot_test_bot", Duration.ofHours(1)),
            Clock.fixed(NOW, ZoneOffset.UTC),
            JsonMapper.builder().build());

    @Test
    void acceptsCorrectlySignedFreshData() {
        Map<String, String> fields = baseFields(NOW.minusSeconds(60));
        fields.put("start_param", "inv_abc");

        TelegramInitData data = validator.validate(TelegramInitDataSigner.sign(BOT_TOKEN, fields));

        assertThat(data.user().id()).isEqualTo(42L);
        assertThat(data.user().firstName()).isEqualTo("Ali");
        assertThat(data.user().languageCode()).isEqualTo("uz");
        assertThat(data.startParam()).isEqualTo("inv_abc");
        assertThat(data.authDate()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void rejectsDataSignedWithAnotherBotToken() {
        String initData = TelegramInitDataSigner.sign("999:OTHER-TOKEN", baseFields(NOW));

        assertInvalid(initData, "signature mismatch");
    }

    @Test
    void rejectsTamperedUser() {
        String initData = TelegramInitDataSigner.sign(BOT_TOKEN, baseFields(NOW))
                .replace("%22id%22%3A42", "%22id%22%3A43");

        assertInvalid(initData, "signature mismatch");
    }

    @Test
    void rejectsMissingHash() {
        String initData = "auth_date=" + NOW.getEpochSecond() + "&user=%7B%7D";

        assertInvalid(initData, "hash is missing");
    }

    @Test
    void rejectsNonHexHash() {
        String initData = "auth_date=" + NOW.getEpochSecond() + "&hash=not-hex";

        assertInvalid(initData, "hash is not hex");
    }

    @Test
    void rejectsExpiredData() {
        String initData = TelegramInitDataSigner.sign(BOT_TOKEN, baseFields(NOW.minus(Duration.ofHours(2))));

        assertInvalid(initData, "initData expired");
    }

    @Test
    void rejectsAuthDateInTheFuture() {
        String initData = TelegramInitDataSigner.sign(BOT_TOKEN, baseFields(NOW.plus(Duration.ofMinutes(5))));

        assertInvalid(initData, "auth_date is in the future");
    }

    @Test
    void rejectsDuplicateKeys() {
        String initData = TelegramInitDataSigner.sign(BOT_TOKEN, baseFields(NOW)) + "&auth_date=1";

        assertInvalid(initData, "duplicate key");
    }

    @Test
    void rejectsMissingUser() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("auth_date", Long.toString(NOW.getEpochSecond()));

        assertInvalid(TelegramInitDataSigner.sign(BOT_TOKEN, fields), "user is missing");
    }

    @Test
    void rejectsBlankInput() {
        assertInvalid("  ", "empty initData");
    }

    private static Map<String, String> baseFields(Instant authDate) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("query_id", "AAHdF6IQAAAAAN0XohDhrOrc");
        fields.put("user", TelegramInitDataSigner.userJson(42, "Ali"));
        fields.put("auth_date", Long.toString(authDate.getEpochSecond()));
        return fields;
    }

    private void assertInvalid(String initData, String expectedReason) {
        assertThatThrownBy(() -> validator.validate(initData))
                .isInstanceOf(InvalidInitDataException.class)
                .extracting(e -> ((InvalidInitDataException) e).getReason())
                .isEqualTo(expectedReason);
    }
}

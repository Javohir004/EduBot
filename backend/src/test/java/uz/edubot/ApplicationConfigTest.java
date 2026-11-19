package uz.edubot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards application.yml: every runtime setting must be mapped to an environment variable, and
 * no secret value may be written into the file.
 */
class ApplicationConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void requiredSettingsAreMappedToEnvironmentVariables() throws Exception {
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            root = new Yaml().load(in);
        }
        Map<String, Object> edubot = (Map<String, Object>) root.get("edubot");

        assertThat(get(edubot, "telegram", "bot-token")).isEqualTo("${TELEGRAM_BOT_TOKEN}");
        assertThat(get(edubot, "telegram", "bot-username")).isEqualTo("${TELEGRAM_BOT_USERNAME}");
        assertThat(get(edubot, "jwt", "secret")).isEqualTo("${JWT_SECRET}");
        assertThat(get(edubot, "app", "public-url")).isEqualTo("${MINI_APP_URL:}");
        assertThat(get(edubot, "bot", "enabled")).isEqualTo("${TELEGRAM_BOT_ENABLED:true}");
        assertThat(get(edubot, "scheduling", "enabled")).isEqualTo("${SCHEDULING_ENABLED:true}");
        assertThat(get(edubot, "platform", "admin-telegram-ids")).isEqualTo("${PLATFORM_ADMIN_TELEGRAM_IDS:}");
    }

    @SuppressWarnings("unchecked")
    private static Object get(Map<String, Object> map, String section, String key) {
        Map<String, Object> sub = (Map<String, Object>) map.get(section);
        assertThat(sub).as("section edubot.%s", section).isNotNull();
        return sub.get(key);
    }
}

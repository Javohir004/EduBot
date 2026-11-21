package uz.edubot.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import uz.edubot.TestcontainersConfiguration;
import uz.edubot.support.FakeNotificationSender;

/** The backend can serve the built Mini App for local development without opening the API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FakeNotificationSender.Config.class})
class SpaServingIT {

    private static final Path DIST = createDist();

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void distDir(DynamicPropertyRegistry registry) {
        registry.add("edubot.frontend.dist-dir", DIST::toString);
    }

    @Test
    void servesIndexAndAssetsAndFallsBackForClientRoutes() throws Exception {
        // MockMvc does not execute forwards; the servlet container serves index.html for it.
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("edubot-spa")));
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk()).andExpect(content().string(containsString("console")));
        mvc.perform(get("/students/123")).andExpect(status().isOk()).andExpect(content().string(containsString("edubot-spa")))
                // Telegram Web embeds Mini Apps in an iframe.
                .andExpect(header().doesNotExist("X-Frame-Options"))
                .andExpect(header().string("Content-Security-Policy", containsString("https://web.telegram.org")));
    }

    @Test
    void apiStaysProtectedAndUnknownApiPathsAreNotTheApp() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/does-not-exist")).andExpect(status().isUnauthorized());
    }

    private static Path createDist() {
        try {
            Path dir = Files.createTempDirectory("edubot-dist");
            Files.writeString(dir.resolve("index.html"), "<!doctype html><title>edubot-spa</title>");
            Files.createDirectories(dir.resolve("assets"));
            Files.writeString(dir.resolve("assets/app.js"), "console.log('app')");
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

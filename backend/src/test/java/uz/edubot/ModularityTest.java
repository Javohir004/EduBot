package uz.edubot;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails the build when a module reaches into another module's internals.
 */
class ModularityTest {

    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(EdubotBackendApplication.class).verify();
    }
}

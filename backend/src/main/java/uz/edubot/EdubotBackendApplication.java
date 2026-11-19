package uz.edubot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EdubotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdubotBackendApplication.class, args);
    }
}

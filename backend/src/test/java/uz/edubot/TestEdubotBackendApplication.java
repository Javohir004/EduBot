package uz.edubot;

import org.springframework.boot.SpringApplication;

public class TestEdubotBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(EdubotBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

package ir.bita.esm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the ESM Backend application.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class EsmBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EsmBackendApplication.class, args);
    }
}

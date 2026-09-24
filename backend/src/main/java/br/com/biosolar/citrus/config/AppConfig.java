package br.com.biosolar.citrus.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class AppConfig {

    /** Relogio injetavel: facilita testes deterministas. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

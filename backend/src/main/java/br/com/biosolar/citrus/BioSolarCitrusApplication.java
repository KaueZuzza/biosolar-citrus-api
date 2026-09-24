package br.com.biosolar.citrus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BioSolarCitrusApplication {

    public static void main(String[] args) {
        SpringApplication.run(BioSolarCitrusApplication.class, args);
    }
}

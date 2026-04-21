package net.spartanb312.grunteon.back;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GruntBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(GruntBackApplication.class, args);
    }
}

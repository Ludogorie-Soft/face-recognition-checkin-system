package org.example.garant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GarantApplication {

    public static void main(String[] args) {
        SpringApplication.run(GarantApplication.class, args);
    }
}

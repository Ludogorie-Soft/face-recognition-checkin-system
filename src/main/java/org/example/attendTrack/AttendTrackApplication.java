package org.example.attendTrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AttendTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendTrackApplication.class, args);
    }
}

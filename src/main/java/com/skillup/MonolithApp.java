package com.skillup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stage 1: monolithic application
 * User, commodity, promotion, and order features run in one process,
 * sharing the skillup database. Stock reservation uses local calls and a local transaction.
 */
@SpringBootApplication
public class MonolithApp {
    // Keep MySQL TIMESTAMP, jOOQ LocalDateTime and lesson fixtures on the same clock.
    static { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")); }
    public static void main(String[] args) {
        SpringApplication.run(MonolithApp.class, args);
    }
}

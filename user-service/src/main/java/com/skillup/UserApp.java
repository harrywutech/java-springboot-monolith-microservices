package com.skillup;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UserApp {
  public static void main(String[] args) {
    SpringApplication.run(UserApp.class, args);
  }
}

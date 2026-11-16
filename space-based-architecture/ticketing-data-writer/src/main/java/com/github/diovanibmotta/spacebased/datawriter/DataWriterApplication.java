package com.github.diovanibmotta.spacebased.datawriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataWriterApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataWriterApplication.class, args);
    }
}

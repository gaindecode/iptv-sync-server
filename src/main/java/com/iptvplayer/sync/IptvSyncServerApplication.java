package com.iptvplayer.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IptvSyncServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IptvSyncServerApplication.class, args);
    }
}

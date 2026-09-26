package com.netscope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * NetScope - read-only network discovery, topology and diagnostics platform.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class NetScopeApplication {
    public static void main(String[] args) {
        SpringApplication.run(NetScopeApplication.class, args);
    }
}

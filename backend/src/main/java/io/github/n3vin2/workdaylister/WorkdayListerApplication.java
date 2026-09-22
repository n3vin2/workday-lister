package io.github.n3vin2.workdaylister;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkdayListerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkdayListerApplication.class, args);
    }
}

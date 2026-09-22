package io.github.n3vin2.workdaylister.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application clock. "Today" for Today's Postings is {@code LocalDate.now(clock)}, so the clock
 * runs in the configured timezone (ADR-0002). Tests replace this bean with a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock(ScraperProperties scraperProperties) {
        return Clock.system(scraperProperties.timezone());
    }
}

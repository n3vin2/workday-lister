package io.github.n3vin2.workdaylister;

import io.github.n3vin2.workdaylister.config.ScraperProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Pins the application clock so "today" is deterministic in integration tests. The clock runs in
 * the configured {@code scraper.timezone}, like the production clock it replaces.
 */
@TestConfiguration
public class PinnedClockConfig {

    /** 2026-09-21 09:00 in America/Regina. */
    public static final Instant PINNED_NOW = Instant.parse("2026-09-21T15:00:00Z");

    /** The calendar date of {@link #PINNED_NOW} in America/Regina: "today" for Today's Postings. */
    public static final LocalDate PINNED_TODAY = LocalDate.of(2026, 9, 21);

    @Bean
    @Primary
    Clock pinnedClock(ScraperProperties scraperProperties) {
        return Clock.fixed(PINNED_NOW, scraperProperties.timezone());
    }
}

package io.github.n3vin2.workdaylister;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Pins the application clock so "today" is deterministic in integration tests. */
@TestConfiguration
public class PinnedClockConfig {

    /** 2026-09-21 09:00 in America/Regina. */
    public static final Instant PINNED_NOW = Instant.parse("2026-09-21T15:00:00Z");

    public static final ZoneId ZONE = ZoneId.of("America/Regina");

    @Bean
    @Primary
    Clock pinnedClock() {
        return Clock.fixed(PINNED_NOW, ZONE);
    }
}

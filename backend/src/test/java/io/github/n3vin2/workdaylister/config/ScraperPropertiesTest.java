package io.github.n3vin2.workdaylister.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ScraperPropertiesTest {

    @EnableConfigurationProperties(ScraperProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultsAre250msPacingThreeRetriesFromOneSecondAndRegina() {
        runner.run(context -> {
            ScraperProperties props = context.getBean(ScraperProperties.class);

            assertThat(props.pacingInterval()).isEqualTo(Duration.ofMillis(250));
            assertThat(props.retryCount()).isEqualTo(3);
            assertThat(props.retryBackoff()).isEqualTo(Duration.ofSeconds(1));
            assertThat(props.timezone()).isEqualTo(ZoneId.of("America/Regina"));
        });
    }

    @Test
    void everyValueCanBeOverriddenFromConfiguration() {
        runner.withPropertyValues(
                        "scraper.pacing-interval=0ms",
                        "scraper.retry-count=5",
                        "scraper.retry-backoff=0ms",
                        "scraper.timezone=UTC")
                .run(context -> {
                    ScraperProperties props = context.getBean(ScraperProperties.class);

                    assertThat(props.pacingInterval()).isZero();
                    assertThat(props.retryCount()).isEqualTo(5);
                    assertThat(props.retryBackoff()).isZero();
                    assertThat(props.timezone()).isEqualTo(ZoneId.of("UTC"));
                });
    }
}

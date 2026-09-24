package io.github.n3vin2.workdaylister.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkdayClientPropertiesTest {

    @EnableConfigurationProperties(WorkdayClientProperties.class)
    static class Config {}

    private static final CareerSite NVIDIA =
            new CareerSite("nvidia", "wd5", "NVIDIAExternalCareerSite");

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void byDefaultEachCareerSiteIsReachedOnItsOwnWorkdayHostWithinTenAndThirtySeconds() {
        runner.run(context -> {
            WorkdayClientProperties props = context.getBean(WorkdayClientProperties.class);

            assertThat(props.baseUrlFor(NVIDIA)).isEqualTo("https://nvidia.wd5.myworkdayjobs.com");
            assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void aConfiguredHostReplacesEveryCareerSitesHostAndTimeoutsCanBeOverridden() {
        runner.withPropertyValues(
                        "workday.client.scheme=http",
                        "workday.client.host=localhost:8089",
                        "workday.client.connect-timeout=1s",
                        "workday.client.read-timeout=2s")
                .run(context -> {
                    WorkdayClientProperties props =
                            context.getBean(WorkdayClientProperties.class);

                    assertThat(props.baseUrlFor(NVIDIA)).isEqualTo("http://localhost:8089");
                    assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }
}

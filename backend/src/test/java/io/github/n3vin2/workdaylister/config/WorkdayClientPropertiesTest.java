package io.github.n3vin2.workdaylister.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkdayClientPropertiesTest {

    @EnableConfigurationProperties(WorkdayClientProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void byDefaultEachCareerSiteIsReachedOnItsOwnWorkdayHost() {
        runner.run(context -> {
            WorkdayClientProperties props = context.getBean(WorkdayClientProperties.class);

            assertThat(props.baseUrlFor("nvidia", "wd5"))
                    .isEqualTo("https://nvidia.wd5.myworkdayjobs.com");
        });
    }

    @Test
    void aConfiguredHostReplacesEveryCareerSitesHost() {
        runner.withPropertyValues(
                        "workday.client.scheme=http", "workday.client.host=localhost:8089")
                .run(context -> {
                    WorkdayClientProperties props =
                            context.getBean(WorkdayClientProperties.class);

                    assertThat(props.baseUrlFor("nvidia", "wd5"))
                            .isEqualTo("http://localhost:8089");
                });
    }
}

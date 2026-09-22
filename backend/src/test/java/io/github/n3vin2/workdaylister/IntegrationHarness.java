package io.github.n3vin2.workdaylister;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base class for backend integration tests: the full Spring context against a Testcontainers
 * MySQL, with a WireMock server standing in for every Workday Career Site, request pacing set
 * to zero, and the clock pinned to {@link PinnedClockConfig#PINNED_NOW}.
 *
 * <p>Tests drive the application only through its HTTP API ({@link #api}) and the Workday stub
 * ({@link #workday}). The MySQL container and the stub server are started once per JVM and shared
 * across test classes so Spring's context cache stays valid.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scraper.pacing-interval=0ms")
@Import(PinnedClockConfig.class)
public abstract class IntegrationHarness {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    /** Stands in for Workday Career Sites; the Workday client's scheme and host point here. */
    protected static final WireMockServer workday =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        MYSQL.start();
        workday.start();
    }

    @DynamicPropertySource
    static void pointWorkdayClientAtStub(DynamicPropertyRegistry registry) {
        registry.add("workday.client.scheme", () -> "http");
        registry.add("workday.client.host", () -> "localhost:" + workday.port());
    }

    @Autowired
    protected TestRestTemplate api;

    @BeforeEach
    void resetWorkdayStub() {
        workday.resetAll();
    }
}

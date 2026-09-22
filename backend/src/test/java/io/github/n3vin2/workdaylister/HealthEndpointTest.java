package io.github.n3vin2.workdaylister;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HealthEndpointTest extends IntegrationHarness {

    @Test
    void healthReportsBackendAndDatabaseUp() {
        ResponseEntity<JsonNode> response = api.getForEntity("/api/health", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(response.getBody().path("components").path("db").path("status").asText())
                .isEqualTo("UP");
    }
}

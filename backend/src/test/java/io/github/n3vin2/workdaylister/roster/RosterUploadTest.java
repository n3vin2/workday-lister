package io.github.n3vin2.workdaylister.roster;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.n3vin2.workdaylister.IntegrationHarness;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Roster replacement through {@code POST /api/roster} and {@code GET /api/companies}. */
class RosterUploadTest extends IntegrationHarness {

    @Test
    void uploadingAValidCsvReplacesTheRosterAndListsCompaniesByName() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        company,url
                        NVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite
                        Acme,https://acme.wd1.myworkdayjobs.com/Careers
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response.getBody().path("companies"))).containsExactly("Acme", "NVIDIA");

        ResponseEntity<JsonNode> list = api.getForEntity("/api/companies", JsonNode.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(list.getBody())).containsExactly("Acme", "NVIDIA");
    }

    @Test
    void anEmptyFileIsRejectedForItsMissingHeader() {
        ResponseEntity<JsonNode> response = uploadRoster("");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errors(response.getBody()))
                .containsExactly(
                        Tuple.tuple(1, "Missing header row: the first line must be \"company,url\""));
    }

    @Test
    void aFileWhoseFirstLineIsNotTheHeaderIsRejected() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        name,link
                        Acme,https://acme.wd1.myworkdayjobs.com/Careers
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errors(response.getBody()))
                .containsExactly(
                        Tuple.tuple(1, "Missing header row: the first line must be \"company,url\""));
    }

    @Test
    void everyInvalidRowIsReportedWithItsLineAndReasonAndNothingIsWritten() {
        uploadRoster(
                """
                company,url
                Before,https://before.wd1.myworkdayjobs.com/Careers
                """);

        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        company,url
                        Acme,https://acme.wd1.myworkdayjobs.com/Careers
                        Beta
                        ,https://blank.wd1.myworkdayjobs.com/Careers
                        Gamma,not a url
                        Delta,https://delta.example.com/Careers
                        Epsilon,https://epsilon.wd1.myworkdayjobs.com/
                        Zeta,https://zeta.wd1.myworkdayjobs.com/Careers,extra
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errors(response.getBody()))
                .containsExactly(
                        Tuple.tuple(3, "Expected 2 columns (company,url) but found 1"),
                        Tuple.tuple(4, "Company name is blank"),
                        Tuple.tuple(5, "URL is malformed"),
                        Tuple.tuple(
                                6,
                                "URL host is not a Workday Career Site"
                                        + " ({tenant}.wd{N}.myworkdayjobs.com)"),
                        Tuple.tuple(7, "URL has no Career Site name after the host"),
                        Tuple.tuple(8, "Expected 2 columns (company,url) but found 3"));
        assertThat(names(api.getForObject("/api/companies", JsonNode.class)))
                .containsExactly("Before");
    }

    @Test
    void aRowWithABlankNameAndABadUrlReportsBothReasons() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        company,url
                        ,https://example.com/Careers
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errors(response.getBody()))
                .containsExactly(
                        Tuple.tuple(2, "Company name is blank"),
                        Tuple.tuple(
                                2,
                                "URL host is not a Workday Career Site"
                                        + " ({tenant}.wd{N}.myworkdayjobs.com)"));
    }

    @Test
    void overlongNamesAndUrlsAreReportedInsteadOfFailingInTheDatabase() {
        String longName = "N".repeat(256);
        String longUrl = "https://acme.wd1.myworkdayjobs.com/Careers?q=" + "x".repeat(2010);
        String longSite = "https://beta.wd1.myworkdayjobs.com/" + "S".repeat(256);

        ResponseEntity<JsonNode> response =
                uploadRoster(
                        "company,url\n"
                                + longName + ",https://acme.wd1.myworkdayjobs.com/Careers\n"
                                + "Acme," + longUrl + "\n"
                                + "Beta," + longSite + "\n");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errors(response.getBody()))
                .containsExactly(
                        Tuple.tuple(2, "Company name is longer than 255 characters"),
                        Tuple.tuple(3, "URL is longer than 2048 characters"),
                        Tuple.tuple(4, "Career Site name is longer than 255 characters"));
    }

    @Test
    void urlVariantsOfOneCareerSiteCollapseToOneCompanyNamedByTheFirstRow() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        company,url
                        NVIDIA,https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite
                        NVIDIA (slash),https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/
                        NVIDIA (locale),https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite
                        NVIDIA (job),https://nvidia.wd5.myworkdayjobs.com/en-US/NVIDIAExternalCareerSite/job/US-CA-Santa-Clara/Engineer_JR1990000
                        NVIDIA (details),https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite/details/Engineer_JR1990000
                        NVIDIA (query),https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite?q=software&locations=abc
                        NVIDIA (host case),https://NVIDIA.WD5.myworkdayjobs.com/NVIDIAExternalCareerSite
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response.getBody().path("companies"))).containsExactly("NVIDIA");
    }

    @Test
    void aTenantOnAnotherPodIsADifferentCareerSite() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        company,url
                        Acme wd1,https://acme.wd1.myworkdayjobs.com/Careers
                        Acme wd5,https://acme.wd5.myworkdayjobs.com/Careers
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response.getBody().path("companies")))
                .containsExactly("Acme wd1", "Acme wd5");
    }

    @Test
    void siteNamesAreCaseSensitive() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        """
                        company,url
                        Acme upper,https://acme.wd1.myworkdayjobs.com/Careers
                        Acme lower,https://acme.wd1.myworkdayjobs.com/careers
                        """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response.getBody().path("companies")))
                .containsExactly("Acme lower", "Acme upper");
    }

    @Test
    void reuploadKeepsMatchingIdsAdoptsNewNamesAndDeletesAbsentCompanies() {
        JsonNode first =
                uploadRoster(
                        """
                        company,url
                        Acme,https://acme.wd1.myworkdayjobs.com/Careers
                        Beta,https://beta.wd3.myworkdayjobs.com/Jobs
                        """)
                        .getBody()
                        .path("companies");
        long acmeId = first.get(0).path("id").asLong();
        long betaId = first.get(1).path("id").asLong();

        ResponseEntity<JsonNode> second =
                uploadRoster(
                        """
                        company,url
                        Acme Corporation,https://acme.wd1.myworkdayjobs.com/en-US/Careers/job/x
                        Gamma,https://gamma.wd1.myworkdayjobs.com/Careers
                        """);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode roster = api.getForObject("/api/companies", JsonNode.class);
        assertThat(names(roster)).containsExactly("Acme Corporation", "Gamma");
        assertThat(roster.get(0).path("id").asLong()).isEqualTo(acmeId);
        assertThat(roster.get(1).path("id").asLong()).isNotEqualTo(betaId);
    }

    @Test
    void aHeaderOnlyFileEmptiesTheRoster() {
        uploadRoster(
                """
                company,url
                Acme,https://acme.wd1.myworkdayjobs.com/Careers
                """);

        ResponseEntity<JsonNode> response = uploadRoster("company,url\n");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("companies")).isEmpty();
        assertThat(api.getForObject("/api/companies", JsonNode.class)).isEmpty();
    }

    @Test
    void quotedFieldsBomAndHeaderCaseAreAcceptedAsSpreadsheetsExportThem() {
        ResponseEntity<JsonNode> response =
                uploadRoster(
                        "\uFEFFCompany,URL\r\n"
                                + "\"Acme, Inc.\",\"https://acme.wd1.myworkdayjobs.com/Careers\"\r\n"
                                + "\r\n"
                                + "\"Say \"\"hi\"\"\",https://hi.wd1.myworkdayjobs.com/Careers\r\n");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(response.getBody().path("companies")))
                .containsExactly("Acme, Inc.", "Say \"hi\"");
    }

    private static List<Tuple> errors(JsonNode body) {
        List<Tuple> errors = new ArrayList<>();
        body.path("errors")
                .forEach(
                        node ->
                                errors.add(
                                        Tuple.tuple(
                                                node.path("line").asInt(),
                                                node.path("reason").asText())));
        return errors;
    }

    private static List<String> names(JsonNode companies) {
        List<String> values = new ArrayList<>();
        companies.forEach(node -> values.add(node.path("name").asText()));
        return values;
    }
}

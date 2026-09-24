package io.github.n3vin2.workdaylister.workday;

import io.github.n3vin2.workdaylister.config.WorkdayClientProperties;
import io.github.n3vin2.workdaylister.roster.CareerSite;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The Workday client over HTTP: {@code POST {base}/wday/cxs/{tenant}/{site}/jobs} for a page and
 * {@code GET {base}/wday/cxs/{tenant}/{site}{externalPath}} for a detail, where {@code base} comes
 * from {@link WorkdayClientProperties} so tests can point every Career Site at a stub. Every request
 * carries {@code Content-Type: application/json} and {@code Accept-Language: en-US} (ADR-0001),
 * bounded by the configured connect and read timeouts.
 *
 * <p>Pacing and retry arrive with a later ticket; an error response is thrown as a
 * {@link org.springframework.web.client.RestClientException}.
 */
@Component
class HttpWorkdayClient implements WorkdayClient {

    private final WorkdayClientProperties properties;
    private final RestClient http;

    HttpWorkdayClient(WorkdayClientProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.http =
                builder.requestFactory(
                                ClientHttpRequestFactoryBuilder.detect()
                                        .build(
                                                ClientHttpRequestFactorySettings.defaults()
                                                        .withConnectTimeout(
                                                                properties.connectTimeout())
                                                        .withReadTimeout(
                                                                properties.readTimeout())))
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .build();
    }

    @Override
    public JobPage listJobs(CareerSite site, int offset) {
        return http.post()
                .uri(careerSiteUrl(site) + "/jobs")
                .body(new ListRequest(Map.of(), PAGE_SIZE, offset, ""))
                .retrieve()
                .body(JobPage.class);
    }

    @Override
    public JobDetail fetchDetail(CareerSite site, String externalPath) {
        DetailResponse response =
                http.get()
                        .uri(careerSiteUrl(site) + externalPath)
                        .retrieve()
                        .body(DetailResponse.class);
        return new JobDetail(response.jobPostingInfo().startDate());
    }

    private String careerSiteUrl(CareerSite site) {
        return properties.baseUrlFor(site) + "/wday/cxs/" + site.tenant() + "/" + site.site();
    }

    /** The body of a jobs request, as the Career Site's own frontend sends it. */
    private record ListRequest(
            Map<String, Object> appliedFacets, int limit, int offset, String searchText) {}

    /** The part of a detail response that is read; everything else is ignored. */
    private record DetailResponse(JobPostingInfo jobPostingInfo) {}

    private record JobPostingInfo(LocalDate startDate) {}
}

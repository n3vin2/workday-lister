package io.github.n3vin2.workdaylister.workday;

import io.github.n3vin2.workdaylister.config.ScraperProperties;
import io.github.n3vin2.workdaylister.config.WorkdayClientProperties;
import io.github.n3vin2.workdaylister.roster.CareerSite;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The Workday client over HTTP: {@code POST {base}/wday/cxs/{tenant}/{site}/jobs} for a page and
 * {@code GET {base}/wday/cxs/{tenant}/{site}{externalPath}} for a detail, where {@code base} comes
 * from {@link WorkdayClientProperties} so tests can point every Career Site at a stub. Every request
 * carries {@code Content-Type: application/json} and {@code Accept-Language: en-US} (ADR-0001), is
 * bounded by the configured connect and read timeouts, and is paced and retried by
 * {@link PacedRetryInterceptor}.
 *
 * <p>Whatever goes wrong surfaces as a {@link WorkdayClient.RequestFailedException} with a short
 * reason: an error status after any retries (from the interceptor), a Career Site that cannot be
 * reached, or a response that is not the endpoint's JSON.
 */
@Component
class HttpWorkdayClient implements WorkdayClient {

    private static final String UNREACHABLE = "Career Site unreachable: ";
    private static final String UNREADABLE = "Career Site sent an unreadable response";

    private final WorkdayClientProperties properties;
    private final RestClient http;

    HttpWorkdayClient(
            WorkdayClientProperties properties,
            ScraperProperties scraperProperties,
            RestClient.Builder builder,
            Clock clock) {
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
                        .requestInterceptor(new PacedRetryInterceptor(scraperProperties, clock))
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .build();
    }

    @Override
    public JobPage listJobs(CareerSite site, int offset) {
        return send(
                () ->
                        http.post()
                                .uri(careerSiteUrl(site) + "/jobs")
                                .body(new ListRequest(Map.of(), PAGE_SIZE, offset, ""))
                                .retrieve()
                                .body(JobPage.class));
    }

    @Override
    public JobDetail fetchDetail(CareerSite site, String externalPath) {
        DetailResponse response =
                send(
                        () ->
                                http.get()
                                        .uri(careerSiteUrl(site) + externalPath)
                                        .retrieve()
                                        .body(DetailResponse.class));
        if (response.jobPostingInfo() == null) {
            throw new RequestFailedException(UNREADABLE);
        }
        return new JobDetail(response.jobPostingInfo().startDate());
    }

    private String careerSiteUrl(CareerSite site) {
        return properties.baseUrlFor(site) + "/wday/cxs/" + site.tenant() + "/" + site.site();
    }

    /**
     * Sends a request and reads its body, turning what the HTTP layer throws into the short reason
     * a failed Company shows. Error statuses have already become that in the interceptor.
     */
    private static <T> T send(Supplier<T> request) {
        T body;
        try {
            body = request.get();
        } catch (ResourceAccessException e) {
            throw new RequestFailedException(UNREACHABLE + causeOf(e));
        } catch (RestClientException e) {
            throw new RequestFailedException(UNREADABLE);
        }
        if (body == null) {
            throw new RequestFailedException(UNREADABLE);
        }
        return body;
    }

    /** Why the Career Site could not be reached, as the I/O layer put it. */
    private static String causeOf(ResourceAccessException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String message = cause.getMessage();
        return (message == null || message.isBlank()) ? cause.getClass().getSimpleName() : message;
    }

    /** The body of a jobs request, as the Career Site's own frontend sends it. */
    private record ListRequest(
            Map<String, Object> appliedFacets, int limit, int offset, String searchText) {}

    /** The part of a detail response that is read; everything else is ignored. */
    private record DetailResponse(JobPostingInfo jobPostingInfo) {}

    private record JobPostingInfo(LocalDate startDate) {}
}

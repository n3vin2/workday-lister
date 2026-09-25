package io.github.n3vin2.workdaylister.workday;

import io.github.n3vin2.workdaylister.config.ScraperProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Paces and retries every request the Workday client sends. Per ADR-0001 Workday rate-limits by
 * source IP across all Career Sites, so consecutive requests are separated by the configured
 * pacing interval, and a throttled (429) or failed (5xx) request is retried up to the configured
 * count with exponential backoff, waiting at least as long as a {@code Retry-After} header asks.
 * Once the last retry has failed too, or on any other error status, the request fails with a
 * {@link WorkdayClient.RequestFailedException} carrying the short reason the Roster screen shows.
 *
 * <p>Requests come from the one Scrape Run thread, one after another, which is what lets the pacing
 * state be a plain field. A cancel request does not cut a wait short: the run notices it at its
 * next check, once the wait and the request it delays are over.
 */
final class PacedRetryInterceptor implements ClientHttpRequestInterceptor {

    private static final String ANSWERED = "Career Site answered HTTP ";

    /** Backoff doubles per retry; past this many the wait would overflow, and no one waits that. */
    private static final int MAX_DOUBLINGS = 30;

    private final Duration pacingInterval;
    private final int retryCount;
    private final Duration retryBackoff;
    private final Clock clock;

    /** When the next request may be sent, on the monotonic clock. */
    private long nextRequestAtNanos = System.nanoTime();

    PacedRetryInterceptor(ScraperProperties properties, Clock clock) {
        this.pacingInterval = properties.pacingInterval();
        this.retryCount = properties.retryCount();
        this.retryBackoff = properties.retryBackoff();
        this.clock = clock;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        for (int attempt = 0; ; attempt++) {
            ClientHttpResponse response = send(request, body, execution);
            HttpStatusCode status = response.getStatusCode();
            if (!status.isError()) {
                return response;
            }
            Duration retryAfter = retryAfter(response.getHeaders());
            response.close();
            if (!isRetryable(status)) {
                throw new WorkdayClient.RequestFailedException(ANSWERED + status.value());
            }
            if (attempt == retryCount) {
                throw new WorkdayClient.RequestFailedException(
                        ANSWERED + status.value() + afterRetries());
            }
            deferNextRequest(max(backoff(attempt), retryAfter));
        }
    }

    /** Sends once the pacing interval since the previous request has passed. */
    private ClientHttpResponse send(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long waitNanos = nextRequestAtNanos - System.nanoTime();
        if (waitNanos > 0) {
            sleep(Duration.ofNanos(waitNanos));
        }
        try {
            return execution.execute(request, body);
        } finally {
            nextRequestAtNanos = System.nanoTime() + pacingInterval.toNanos();
        }
    }

    private void deferNextRequest(Duration wait) {
        nextRequestAtNanos = Math.max(nextRequestAtNanos, System.nanoTime() + wait.toNanos());
    }

    private static boolean isRetryable(HttpStatusCode status) {
        return status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.is5xxServerError();
    }

    /** The exponential backoff before retry number {@code attempt + 1}. */
    private Duration backoff(int attempt) {
        return retryBackoff.multipliedBy(1L << Math.min(attempt, MAX_DOUBLINGS));
    }

    /**
     * What a {@code Retry-After} header asks for: a number of seconds, or an HTTP date measured
     * against the application clock. Zero when absent or unreadable, so the backoff decides.
     */
    private Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Duration.ZERO;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                return max(Duration.ZERO, Duration.between(clock.instant(), at.toInstant()));
            } catch (DateTimeParseException notADate) {
                return Duration.ZERO;
            }
        }
    }

    private String afterRetries() {
        if (retryCount == 0) {
            return "";
        }
        return " after " + retryCount + (retryCount == 1 ? " retry" : " retries");
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Waits, unless the thread is being shut down with the application, in which case the request
     * is abandoned as failed.
     */
    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkdayClient.RequestFailedException("Interrupted while waiting to send");
        }
    }
}

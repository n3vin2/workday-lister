package io.github.n3vin2.workdaylister.config;

import io.github.n3vin2.workdaylister.roster.CareerSite;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where and how the Workday client sends its requests, bound from {@code workday.client.*}.
 *
 * <p>Per ADR-0001 a Career Site lives at {@code https://{tenant}.wd{N}.myworkdayjobs.com}, so by
 * default each Career Site is reached on its own host. Setting {@code host} (for example {@code
 * localhost:8089}) sends every Career Site's requests to that one host instead, which is how tests
 * point the client at a stub server. The {@code /wday/cxs/{tenant}/{site}} path still identifies
 * the Career Site, so a stub can tell them apart.
 *
 * @param scheme {@code https} in production, {@code http} against a local stub
 * @param host a host (and optional port) that replaces every Career Site's own host; blank means
 *     use the real per-tenant host
 * @param connectTimeout how long one request may wait for a connection
 * @param readTimeout how long one request may wait for its response; together with connectTimeout
 *     this bounds a request so a silent Career Site cannot stall the Scrape Run thread
 */
@ConfigurationProperties(prefix = "workday.client")
public record WorkdayClientProperties(
        @DefaultValue("https") String scheme,
        String host,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout) {

    /** Scheme and host, without a trailing slash, at which the given Career Site is reached. */
    public String baseUrlFor(CareerSite site) {
        String resolvedHost = (host == null || host.isBlank()) ? site.publicHost() : host;
        return scheme + "://" + resolvedHost;
    }
}

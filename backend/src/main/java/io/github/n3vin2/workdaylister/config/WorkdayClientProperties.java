package io.github.n3vin2.workdaylister.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the Workday client sends its requests, bound from {@code workday.client.*}.
 *
 * <p>Per ADR-0001 a Career Site lives at {@code https://{tenant}.wd{N}.myworkdayjobs.com}, so by
 * default the host is derived from each Career Site's tenant and pod. Setting {@code host} (for
 * example {@code localhost:8089}) sends every Career Site's requests to that one host instead, which
 * is how tests point the client at a stub server. The {@code /wday/cxs/{tenant}/{site}} path still
 * identifies the Career Site, so a stub can tell them apart.
 *
 * @param scheme {@code https} in production, {@code http} against a local stub
 * @param host a host (and optional port) that replaces every Career Site's own host; blank means
 *     use the real per-tenant host
 */
@ConfigurationProperties(prefix = "workday.client")
public record WorkdayClientProperties(@DefaultValue("https") String scheme, String host) {

    public static final String PUBLIC_HOST_SUFFIX = "myworkdayjobs.com";

    /** Scheme and host, without a trailing slash, for the Career Site on the given tenant and pod. */
    public String baseUrlFor(String tenant, String pod) {
        String resolvedHost =
                (host == null || host.isBlank())
                        ? tenant + "." + pod + "." + PUBLIC_HOST_SUFFIX
                        : host;
        return scheme + "://" + resolvedHost;
    }
}

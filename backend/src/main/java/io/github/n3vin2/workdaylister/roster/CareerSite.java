package io.github.n3vin2.workdaylister.roster;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The coordinates of a Career Site: the public Workday jobs page for one Company, hosted at
 * {@code {tenant}.wd{N}.myworkdayjobs.com/{site}}.
 *
 * <p>Two Career Site URLs point at the same Career Site when they parse to equal coordinates. The
 * tenant and pod are DNS labels and are normalised to lowercase; the pod is kept verbatim otherwise
 * because a tenant on the wrong pod is a hard failure, not a redirect. The site name is case
 * sensitive.
 *
 * @param tenant the first host label, for example {@code nvidia}
 * @param pod the Workday pod, for example {@code wd5}
 * @param site the site name, for example {@code NVIDIAExternalCareerSite}
 */
@Embeddable
public record CareerSite(
        @Column(nullable = false, length = 63) String tenant,
        @Column(nullable = false, length = 15) String pod,
        @Column(nullable = false) String site) {

    /** A URL that does not identify a Career Site; the message is the reason shown to the user. */
    public static final class InvalidUrlException extends RuntimeException {
        InvalidUrlException(String reason) {
            super(reason);
        }
    }

    static final String MALFORMED_URL = "URL is malformed";
    static final String NOT_WORKDAY_HOST =
            "URL host is not a Workday Career Site ({tenant}.wd{N}.myworkdayjobs.com)";
    static final String NO_SITE_NAME = "URL has no Career Site name after the host";

    private static final Pattern HOST = Pattern.compile("([a-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com");
    private static final Pattern LOCALE_SEGMENT = Pattern.compile("[a-z]{2}-[A-Z]{2}");

    /**
     * Parses a public Workday careers URL as pasted from a browser. The query string, an optional
     * leading locale segment such as {@code /en-US/}, and everything after the site name (job
     * paths, detail paths) are ignored.
     *
     * @throws InvalidUrlException when the URL is malformed, its host is not a Workday Career Site
     *     host, or its path has no site name
     */
    public static CareerSite parse(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException(MALFORMED_URL);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new InvalidUrlException(MALFORMED_URL);
        }
        Matcher host = HOST.matcher(uri.getHost().toLowerCase(Locale.ROOT));
        if (!host.matches()) {
            throw new InvalidUrlException(NOT_WORKDAY_HOST);
        }
        List<String> segments =
                Arrays.stream(uri.getPath().split("/")).filter(s -> !s.isEmpty()).toList();
        int siteIndex =
                !segments.isEmpty() && LOCALE_SEGMENT.matcher(segments.get(0)).matches() ? 1 : 0;
        if (siteIndex >= segments.size()) {
            throw new InvalidUrlException(NO_SITE_NAME);
        }
        return new CareerSite(host.group(1), host.group(2), segments.get(siteIndex));
    }
}

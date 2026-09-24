package io.github.n3vin2.workdaylister;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds Workday responses in the shape of the recorded fixtures under
 * {@code src/test/resources/wiremock/__files/workday/}, for tests that need a particular
 * {@code total}, page length, label, or Posting Date rather than what was captured.
 */
public final class WorkdayPages {

    /** Workday's en-US label for a posting that went live today. */
    public static final String POSTED_TODAY = "Posted Today";

    /** Workday's en-US label for a posting that went live yesterday. */
    public static final String POSTED_YESTERDAY = "Posted Yesterday";

    /** Workday's en-US label for a posting older than a month. */
    public static final String POSTED_LONG_AGO = "Posted 30+ Days Ago";

    private WorkdayPages() {}

    /** A single page listing exactly the given postings. */
    public static String postings(String... listings) {
        return page(listings.length, List.of(listings));
    }

    /** One posting in the recorded shape, labelled {@code Posted Today}. */
    public static String listing(String title, String externalPath, String location) {
        return listing(title, externalPath, location, POSTED_TODAY);
    }

    /** One posting in the recorded shape, with the given {@code postedOn} label. */
    public static String listing(
            String title, String externalPath, String location, String postedOn) {
        String requisitionId = externalPath.substring(externalPath.lastIndexOf('_') + 1);
        return """
                {"title":"%s","externalPath":"%s","locationsText":"%s","postedOn":"%s",\
                "bulletFields":["%s"]}"""
                .formatted(title, externalPath, location, postedOn, requisitionId);
    }

    /** A page in the shape of the recorded fixtures: the given total and listings. */
    public static String page(int total, List<String> listings) {
        return "{\"total\":%d,\"jobPostings\":[%s],\"userAuthenticated\":false}"
                .formatted(total, String.join(",", listings));
    }

    /**
     * A page in the shape of the recorded fixtures: {@code count} postings numbered from
     * {@code offset}, with requisition IDs {@code R<n>}, labelled {@code Posted 30+ Days Ago} so
     * they cost no detail request, and the given {@code total}.
     */
    public static String jobsPage(int total, int offset, int count) {
        List<String> postings = new ArrayList<>();
        for (int n = offset; n < offset + count; n++) {
            postings.add(
                    listing(
                            "Engineer " + n,
                            "/job/Regina-SK/Engineer-%d_R%d".formatted(n, n),
                            "Regina, SK",
                            POSTED_LONG_AGO));
        }
        return page(total, postings);
    }

    /**
     * The part of a detail response the scraper reads: {@code jobPostingInfo.startDate}, the
     * Posting Date as a bare calendar date (ADR-0002).
     */
    public static String detail(LocalDate startDate) {
        return "{\"jobPostingInfo\":{\"startDate\":\"%s\"}}".formatted(startDate);
    }
}

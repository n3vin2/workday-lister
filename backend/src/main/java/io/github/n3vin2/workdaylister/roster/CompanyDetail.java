package io.github.n3vin2.workdaylister.roster;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.github.n3vin2.workdaylister.scrape.PostingSummary;
import java.util.List;

/**
 * One Company as the Company screen shows it: the same header as its Roster row, flattened into
 * this object, plus the postings of the requested scope as cards: its Today's Postings, or every
 * Open posting, with the Closed ones among them too when asked for.
 */
public record CompanyDetail(
        @JsonUnwrapped CompanySummary company, List<PostingSummary> postings) {

    static CompanyDetail of(Company company, int todayCount, List<PostingSummary> postings) {
        return new CompanyDetail(CompanySummary.of(company, todayCount), postings);
    }
}

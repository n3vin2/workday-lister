package io.github.n3vin2.workdaylister.roster;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.github.n3vin2.workdaylister.scrape.PostingSummary;
import java.util.List;

/**
 * One Company as the Company screen shows it: the same header as its Roster row, flattened into
 * this object, plus its postings as cards: the Open ones, and the Closed ones too when asked for.
 */
public record CompanyDetail(
        @JsonUnwrapped CompanySummary company, List<PostingSummary> postings) {

    static CompanyDetail of(Company company, List<PostingSummary> postings) {
        return new CompanyDetail(CompanySummary.of(company), postings);
    }
}

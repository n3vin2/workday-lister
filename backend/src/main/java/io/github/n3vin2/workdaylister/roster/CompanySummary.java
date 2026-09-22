package io.github.n3vin2.workdaylister.roster;

/** One Company as the Roster screen lists it. Further columns arrive with Scrape Runs. */
public record CompanySummary(long id, String name, CompanyStatus status) {

    static CompanySummary of(Company company) {
        return new CompanySummary(company.getId(), company.getName(), company.getStatus());
    }
}

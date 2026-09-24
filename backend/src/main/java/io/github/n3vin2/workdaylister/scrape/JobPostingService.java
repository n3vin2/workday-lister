package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the Job Postings that Scrape Runs have stored for a Company, including its Today's
 * Postings: the Open postings whose Posting Date is today in the configured timezone (ADR-0002).
 * "Today" is read from the clock on every call, so a run's results age overnight without a
 * re-scrape.
 */
@Service
public class JobPostingService {

    private final JobPostingRepository postings;
    private final Clock clock;

    JobPostingService(JobPostingRepository postings, Clock clock) {
        this.postings = postings;
        this.clock = clock;
    }

    /** A Company's Open postings by title, with its Closed ones among them when asked. */
    @Transactional(readOnly = true)
    public List<JobPosting> postingsOf(Company company, boolean includeClosed) {
        if (includeClosed) {
            return postings.findAllByCompanyOrderByTitleAscRequisitionIdAsc(company);
        }
        return postings.findAllByCompanyAndStateOrderByTitleAscRequisitionIdAsc(
                company, PostingState.OPEN);
    }

    /**
     * A Company's Today's Postings by title, with the Closed postings that went live today among
     * them when asked.
     */
    @Transactional(readOnly = true)
    public List<JobPosting> todaysPostingsOf(Company company, boolean includeClosed) {
        if (includeClosed) {
            return postings.findAllByCompanyAndPostingDateOrderByTitleAscRequisitionIdAsc(
                    company, today());
        }
        return postings.findAllByCompanyAndStateAndPostingDateOrderByTitleAscRequisitionIdAsc(
                company, PostingState.OPEN, today());
    }

    /** How many Today's Postings a Company has. */
    @Transactional(readOnly = true)
    public int todayCountOf(Company company) {
        return (int)
                postings.countByCompanyAndStateAndPostingDate(
                        company, PostingState.OPEN, today());
    }

    /** How many Today's Postings each Company has, by Company id; a Company with none is absent. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> todayCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (CompanyPostingCount count :
                postings.countByStateAndPostingDateGroupedByCompany(PostingState.OPEN, today())) {
            counts.put(count.companyId(), (int) count.count());
        }
        return counts;
    }

    /** The current calendar date in the configured timezone, which the clock runs in. */
    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

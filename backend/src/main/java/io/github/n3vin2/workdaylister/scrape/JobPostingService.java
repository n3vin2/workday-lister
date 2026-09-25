package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the Job Postings that Scrape Runs have stored for a Company. */
@Service
public class JobPostingService {

    private final JobPostingRepository postings;

    JobPostingService(JobPostingRepository postings) {
        this.postings = postings;
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
}

package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /** Every posting of a Company, Open and Closed, by title. */
    List<JobPosting> findAllByCompanyOrderByTitleAscRequisitionIdAsc(Company company);

    /** A Company's postings in the given state, by title. */
    List<JobPosting> findAllByCompanyAndStateOrderByTitleAscRequisitionIdAsc(
            Company company, PostingState state);

    /** How many of a Company's postings are in the given state. */
    long countByCompanyAndState(Company company, PostingState state);
}

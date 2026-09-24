package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /** Every posting of a Company, Open and Closed, by title. */
    List<JobPosting> findAllByCompanyOrderByTitleAscRequisitionIdAsc(Company company);

    /** A Company's postings in the given state, by title. */
    List<JobPosting> findAllByCompanyAndStateOrderByTitleAscRequisitionIdAsc(
            Company company, PostingState state);

    /** A Company's postings with the given Posting Date, Open and Closed, by title. */
    List<JobPosting> findAllByCompanyAndPostingDateOrderByTitleAscRequisitionIdAsc(
            Company company, LocalDate postingDate);

    /** A Company's postings in the given state with the given Posting Date, by title. */
    List<JobPosting> findAllByCompanyAndStateAndPostingDateOrderByTitleAscRequisitionIdAsc(
            Company company, PostingState state, LocalDate postingDate);

    /** How many of a Company's postings are in the given state. */
    long countByCompanyAndState(Company company, PostingState state);

    /** How many of a Company's postings are in the given state with the given Posting Date. */
    long countByCompanyAndStateAndPostingDate(
            Company company, PostingState state, LocalDate postingDate);

    /**
     * How many postings of each Company are in the given state with the given Posting Date; a
     * Company with none is absent.
     */
    @Query(
            "SELECT new io.github.n3vin2.workdaylister.scrape.CompanyPostingCount("
                    + "p.company.id, COUNT(p)) FROM JobPosting p"
                    + " WHERE p.state = :state AND p.postingDate = :postingDate"
                    + " GROUP BY p.company.id")
    List<CompanyPostingCount> countByStateAndPostingDateGroupedByCompany(
            PostingState state, LocalDate postingDate);
}

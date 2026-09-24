package io.github.n3vin2.workdaylister.scrape;

import io.github.n3vin2.workdaylister.roster.Company;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    List<JobPosting> findAllByCompanyOrderByTitleAscRequisitionIdAsc(Company company);

    long countByCompany(Company company);
}

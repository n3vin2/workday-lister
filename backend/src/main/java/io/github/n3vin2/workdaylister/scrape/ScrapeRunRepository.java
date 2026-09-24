package io.github.n3vin2.workdaylister.scrape;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScrapeRunRepository extends JpaRepository<ScrapeRun, Long> {}

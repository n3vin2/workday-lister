package io.github.n3vin2.workdaylister.roster;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An employer the user is tracking: a display name from the CSV and exactly one Career Site. The
 * Career Site coordinates are unique across the Roster (see {@code V2__company.sql}); the id is
 * stable across Roster uploads that keep the same Career Site, so anything that later hangs off a
 * Company survives a re-upload.
 */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** The URL as supplied in the CSV, before parsing. */
    @Column(name = "career_site_url", nullable = false, length = 2048)
    private String careerSiteUrl;

    @Embedded private CareerSite careerSite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyStatus status;

    protected Company() {}

    public Company(String name, String careerSiteUrl, CareerSite careerSite) {
        this.name = name;
        this.careerSiteUrl = careerSiteUrl;
        this.careerSite = careerSite;
        this.status = CompanyStatus.NEVER_SCRAPED;
    }

    /** Adopts the name and URL from a newer CSV row for the same Career Site. */
    void adopt(String name, String careerSiteUrl) {
        this.name = name;
        this.careerSiteUrl = careerSiteUrl;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCareerSiteUrl() {
        return careerSiteUrl;
    }

    public CareerSite getCareerSite() {
        return careerSite;
    }

    public CompanyStatus getStatus() {
        return status;
    }
}

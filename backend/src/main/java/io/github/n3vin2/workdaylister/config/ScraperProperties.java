package io.github.n3vin2.workdaylister.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for Scrape Runs, bound from {@code scraper.*}.
 *
 * @param pacingInterval pause between consecutive requests to Workday (ADR-0001: rate limiting is
 *     per source IP across all tenants)
 * @param retryCount how many times a throttled or failed request is retried with backoff
 * @param timezone the zone in which "today" is evaluated for Today's Postings (ADR-0002)
 */
@ConfigurationProperties(prefix = "scraper")
public record ScraperProperties(
        @DefaultValue("250ms") Duration pacingInterval,
        @DefaultValue("3") int retryCount,
        @DefaultValue("America/Regina") ZoneId timezone) {}

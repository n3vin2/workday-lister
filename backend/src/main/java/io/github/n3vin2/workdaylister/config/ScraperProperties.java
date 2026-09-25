package io.github.n3vin2.workdaylister.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for Scrape Runs, bound from {@code scraper.*}.
 *
 * @param pacingInterval pause between consecutive requests to Workday (ADR-0001: rate limiting is
 *     per source IP across all Career Sites)
 * @param retryCount how many times a throttled (429) or failed (5xx) request is retried with
 *     backoff before the Company is marked failed
 * @param retryBackoff the wait before the first retry, doubled for each retry after it; a {@code
 *     Retry-After} header asking for longer is honoured instead
 * @param timezone the zone in which "today" is evaluated for Today's Postings (ADR-0002)
 */
@ConfigurationProperties(prefix = "scraper")
public record ScraperProperties(
        @DefaultValue("250ms") Duration pacingInterval,
        @DefaultValue("3") int retryCount,
        @DefaultValue("1s") Duration retryBackoff,
        @DefaultValue("America/Regina") ZoneId timezone) {}

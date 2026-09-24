package io.github.n3vin2.workdaylister.workday;

import java.time.LocalDate;

/**
 * What Workday's detail endpoint adds to a listing: the Posting Date, a bare calendar date with no
 * timezone (ADR-0002).
 *
 * @param startDate the posting's {@code startDate}
 */
public record JobDetail(LocalDate startDate) {}

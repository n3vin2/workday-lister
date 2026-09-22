package io.github.n3vin2.workdaylister.roster;

/**
 * One valid row of a Roster CSV: the Company's display name, its Career Site URL as supplied, and
 * the coordinates that URL parses to.
 */
public record RosterEntry(String name, String careerSiteUrl, CareerSite careerSite) {}

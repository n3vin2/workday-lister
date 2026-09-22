package io.github.n3vin2.workdaylister.roster;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses and validates a Roster CSV: a header row {@code company,url} followed by one row per
 * Company. Every rule is checked on every row so the user can fix the whole file in one pass; a
 * file with any failing row yields no entries.
 *
 * <p>Fields may be double-quoted (so a name may contain a comma); blank lines are skipped and a
 * leading byte-order mark is ignored. Line numbers are physical line numbers in the file, starting
 * at 1 for the header.
 */
public final class RosterCsv {

    /** A failing row, reported by its physical line number. */
    public record RowError(int line, String reason) {}

    /** Either the entries of a valid file, or the errors of an invalid one; never both. */
    public record Result(List<RosterEntry> entries, List<RowError> errors) {
        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    static final String MISSING_HEADER = "Missing header row: the first line must be \"company,url\"";
    static final String BLANK_NAME = "Company name is blank";

    static String wrongColumnCount(int found) {
        return "Expected 2 columns (company,url) but found " + found;
    }

    private static final int EXPECTED_COLUMNS = 2;

    private RosterCsv() {}

    public static Result parse(String text) {
        List<RosterEntry> entries = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        List<String> lines = text.lines().toList();

        if (lines.isEmpty() || !isHeader(splitLine(lines.get(0)))) {
            errors.add(new RowError(1, MISSING_HEADER));
        }
        for (int i = 1; i < lines.size(); i++) {
            int line = i + 1;
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> cells = splitLine(lines.get(i));
            if (cells.size() != EXPECTED_COLUMNS) {
                errors.add(new RowError(line, wrongColumnCount(cells.size())));
                continue;
            }
            String name = cells.get(0).trim();
            String url = cells.get(1).trim();
            boolean valid = true;
            if (name.isBlank()) {
                errors.add(new RowError(line, BLANK_NAME));
                valid = false;
            }
            CareerSite careerSite = null;
            try {
                careerSite = CareerSite.parse(url);
            } catch (CareerSite.InvalidUrlException e) {
                errors.add(new RowError(line, e.getMessage()));
                valid = false;
            }
            if (valid) {
                entries.add(new RosterEntry(name, url, careerSite));
            }
        }
        return errors.isEmpty() ? new Result(entries, List.of()) : new Result(List.of(), errors);
    }

    private static boolean isHeader(List<String> cells) {
        return cells.size() == EXPECTED_COLUMNS
                && stripBom(cells.get(0)).trim().equalsIgnoreCase("company")
                && cells.get(1).trim().equalsIgnoreCase("url");
    }

    private static String stripBom(String cell) {
        return cell.startsWith("﻿") ? cell.substring(1) : cell;
    }

    /** Splits one line on commas, honouring double-quoted fields with {@code ""} as an escaped quote. */
    private static List<String> splitLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') {
                    cell.append(c);
                } else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}

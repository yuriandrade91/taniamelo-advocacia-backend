package com.lawfirm.law.firm.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContributionTimeParser {
    private static final Pattern YEARS =
            Pattern.compile("(\\d+)\\s*anos?", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTHS =
            Pattern.compile("(\\d+)\\s*mes(?:es)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAYS =
            Pattern.compile("(\\d+)\\s*dias?", Pattern.CASE_INSENSITIVE);

    private ContributionTimeParser() {}

    /**
     * Parse a contribution time string like "3 anos, 10 meses, 22 dias" into months. Rule: months =
     * years*12 + months + (days >= 15 ? 1 : 0) Returns null if input is null/blank.
     */
    public static Integer toMonths(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;

        int y = 0, m = 0, d = 0;
        Matcher my = YEARS.matcher(t);
        if (my.find()) {
            try {
                y = Integer.parseInt(my.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher mm = MONTHS.matcher(t);
        if (mm.find()) {
            try {
                m = Integer.parseInt(mm.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher md = DAYS.matcher(t);
        if (md.find()) {
            try {
                d = Integer.parseInt(md.group(1));
            } catch (NumberFormatException ignored) {
            }
        }

        int total = (y * 12) + m + (d >= 15 ? 1 : 0);
        return total;
    }
}

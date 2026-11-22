package io.github.zahediparsa.reviewstreak;

import java.time.LocalDate;
import java.util.TreeSet;

public final class StreakTest {
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 3, 30);
        check(today, 0, 0, false);
        check(today, 1, 1, true, today, today);
        check(today, 2, 2, false, today.minusDays(2), today.minusDays(1));
        check(today, 0, 2, false, today.minusDays(4), today.minusDays(3));
        check(today, 1, 2, true, today.minusDays(4), today.minusDays(3), today);
        check(today, 0, 0, false, today.plusDays(1));
        check(today, 3, 3, true, today.minusDays(2), today.minusDays(1), today);
        System.out.println("7 streak cases passed");
    }

    private static void check(LocalDate today, int current, int best, boolean studied, LocalDate... dates) {
        TreeSet<LocalDate> unique = new TreeSet<>(java.util.Arrays.asList(dates));
        Streak result = new Streak(unique, today);
        if (result.current != current || result.best != best || result.studiedToday != studied) {
            throw new AssertionError("Incorrect streak for " + unique);
        }
    }
}

package io.github.zahediparsa.reviewstreak;

import java.time.LocalDate;
import java.util.NavigableSet;

final class Streak {
    final int current;
    final int best;
    final boolean studiedToday;

    Streak(NavigableSet<LocalDate> dates, LocalDate today) {
        studiedToday = dates.contains(today);
        int run = 0;
        int longest = 0;
        LocalDate prior = null;
        for (LocalDate date : dates.headSet(today, true)) {
            run = prior != null && prior.plusDays(1).equals(date) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prior = date;
        }
        best = longest;
        current = today.equals(prior) || today.minusDays(1).equals(prior) ? run : 0;
    }
}

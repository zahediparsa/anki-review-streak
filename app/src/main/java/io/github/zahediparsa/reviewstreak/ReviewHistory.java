package io.github.zahediparsa.reviewstreak;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Log;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

final class ReviewHistory {
    static final String ANKI = "com.ichi2.anki";
    static final String PERMISSION = ANKI + ".permission.READ_WRITE_DATABASE";
    private static final Uri CARDS = Uri.parse("content://" + ANKI + ".flashcards/cards");
    private static final String REVIEW_TIME = "last_review_time_secs";
    private static final String DATES = "review_dates";

    enum Status { READY, MISSING, PERMISSION_REQUIRED, UNAVAILABLE }

    static Status connection(Context context) {
        if (context.getPackageManager().getLaunchIntentForPackage(ANKI) == null) {
            return Status.MISSING;
        }
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            return Status.PERMISSION_REQUIRED;
        }
        return Status.READY;
    }

    static Status refresh(Context context, CancellationSignal signal) {
        Status status = connection(context);
        if (status != Status.READY) {
            return status;
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant now = Instant.now();
        Set<String> observed = new HashSet<>();
        // The Anki search bounds the number of card rows the provider needs to return.
        try (Cursor cursor = context.getContentResolver().query(
                CARDS, new String[]{REVIEW_TIME}, "rated:31", null, null, signal)) {
            if (cursor == null) {
                return Status.UNAVAILABLE;
            }
            int column = cursor.getColumnIndexOrThrow(REVIEW_TIME);
            while (cursor.moveToNext()) {
                signal.throwIfCanceled();
                long seconds = cursor.getLong(column);
                if (seconds > 0 && seconds <= now.getEpochSecond()) {
                    LocalDate reviewed = Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate();
                    observed.add(reviewed.toString());
                }
            }
        } catch (android.os.OperationCanceledException canceled) {
            throw canceled;
        } catch (RuntimeException unavailable) {
            Log.w("ReviewStreak", "Could not read the AnkiDroid review timestamp", unavailable);
            return Status.UNAVAILABLE;
        }
        signal.throwIfCanceled();
        merge(context, observed);
        return Status.READY;
    }

    private static synchronized void merge(Context context, Set<String> observed) {
        SharedPreferences preferences = preferences(context);
        Set<String> merged = new HashSet<>(preferences.getStringSet(DATES, Collections.emptySet()));
        if (merged.addAll(observed)) {
            preferences.edit().putStringSet(DATES, merged).apply();
        }
    }

    static NavigableSet<LocalDate> days(Context context) {
        NavigableSet<LocalDate> days = new TreeSet<>();
        for (String saved : preferences(context).getStringSet(DATES, Collections.emptySet())) {
            days.add(LocalDate.parse(saved));
        }
        return days;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("streak", Context.MODE_PRIVATE);
    }
}

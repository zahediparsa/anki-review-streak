package io.github.zahediparsa.reviewstreak;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.NavigableSet;

public final class Dashboard extends Activity {
    private static final int PAGE = Color.rgb(246, 245, 237);
    private static final int INK = Color.rgb(24, 45, 36);
    private static final int GREEN = Color.rgb(23, 98, 73);
    private static final int LIME = Color.rgb(227, 245, 150);
    private static final int MUTED = Color.rgb(74, 91, 80);
    private static final int PALE = Color.rgb(225, 231, 220);
    private static final int REQUEST_PERMISSION = 1;
    private final DateTimeFormatter weekday = DateTimeFormatter.ofPattern("EEEEE");
    private final DateTimeFormatter fullDate = DateTimeFormatter.ofPattern("EEEE, MMMM d");

    private TextView count;
    private TextView todayLine;
    private TextView best;
    private TextView status;
    private Button ankiButton;
    private Button refreshButton;
    private LinearLayout week;
    private CancellationSignal pending;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAGE);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        LinearLayout page = column();
        page.setPadding(dp(24), dp(26), dp(24), dp(24));
        scroll.addView(page);
        setContentView(scroll);

        add(page, text("Review Streak", 32, INK, true), 0);
        add(page, text("Daily practice with AnkiDroid", 16, MUTED, false), 4);

        LinearLayout hero = column();
        hero.setBackground(shape(INK, 26));
        hero.setPadding(dp(22), dp(22), dp(22), dp(24));
        add(page, hero, 24);
        add(hero, text("Current streak", 17, Color.WHITE, true), 0);
        count = text("", 56, LIME, true);
        add(hero, count, 2);
        todayLine = text("", 16, Color.WHITE, false);
        add(hero, todayLine, 4);

        LinearLayout history = column();
        history.setPadding(dp(16), dp(16), dp(16), dp(18));
        history.setBackground(shape(Color.WHITE, 22));
        add(page, history, 16);
        add(history, text("Last 7 days", 18, INK, true), 0);
        week = new LinearLayout(this);
        add(history, week, 10);
        best = text("", 16, MUTED, true);
        add(history, best, 12);

        ankiButton = button("Open AnkiDroid", GREEN, Color.WHITE);
        ankiButton.setOnClickListener(view -> openAnki());
        add(page, ankiButton, 20);
        refreshButton = button("Refresh streak", PALE, INK);
        add(page, refreshButton, 6);
        status = text("", 14, MUTED, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(page, status, 6);
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    @Override public void onStop() {
        super.onStop();
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }

    private void openAnki() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(ReviewHistory.ANKI);
        if (launch != null) {
            startActivity(launch);
        }
    }

    private void requestAccess() {
        if (getPreferences(MODE_PRIVATE).getBoolean("asked", false)
                && !shouldShowRequestPermissionRationale(ReviewHistory.PERMISSION)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean("asked", true).apply();
        requestPermissions(new String[]{ReviewHistory.PERMISSION}, REQUEST_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == REQUEST_PERMISSION && grants.length > 0) {
            refresh();
        }
    }

    private void installAnki() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/ankidroid/Anki-Android/releases/latest")));
        } catch (ActivityNotFoundException ignored) {
            status.setText("Install AnkiDroid from Google Play or the official GitHub releases.");
        }
    }

    private void refresh() {
        if (pending != null) {
            return;
        }
        drawHistory();
        ReviewHistory.Status connection = ReviewHistory.connection(this);
        showConnection(connection);
        if (connection != ReviewHistory.Status.READY) {
            return;
        }
        ReviewJob.schedule(this);
        status.setText("Checking AnkiDroid…");
        refreshButton.setEnabled(false);
        CancellationSignal signal = new CancellationSignal();
        pending = signal;
        new Thread(() -> {
            try {
                ReviewHistory.Status result = ReviewHistory.refresh(getApplicationContext(), signal);
                runOnUiThread(() -> {
                    if (pending == signal) {
                        pending = null;
                        drawHistory();
                        showConnection(result);
                    }
                });
            } catch (OperationCanceledException ignored) {
                // The dashboard left the foreground during this refresh.
            }
        }, "streak-dashboard").start();
    }

    private void showConnection(ReviewHistory.Status connection) {
        ankiButton.setVisibility(connection == ReviewHistory.Status.MISSING ? View.GONE : View.VISIBLE);
        refreshButton.setEnabled(true);
        refreshButton.setText("Refresh streak");
        refreshButton.setOnClickListener(view -> refresh());
        switch (connection) {
            case MISSING:
                refreshButton.setText("Get AnkiDroid");
                refreshButton.setOnClickListener(view -> installAnki());
                status.setText("Install AnkiDroid to start tracking your reviews.");
                break;
            case PERMISSION_REQUIRED:
                refreshButton.setText("Connect AnkiDroid");
                refreshButton.setOnClickListener(view -> requestAccess());
                status.setText("Allow AnkiDroid access so Review Streak can read review dates.");
                break;
            case UNAVAILABLE:
                status.setText("Couldn’t read review dates. Open AnkiDroid, finish setup and update it if needed, then refresh.");
                break;
            case READY:
                status.setText("Connected · refreshes in the background when Android allows.");
                break;
        }
    }

    private void drawHistory() {
        LocalDate today = LocalDate.now();
        NavigableSet<LocalDate> dates = ReviewHistory.days(this);
        Streak streak = new Streak(dates, today);
        count.setText(days(streak.current));
        todayLine.setText(streak.studiedToday ? "Today’s review counted" : "Answer a card to count today");
        best.setText("Best streak · " + days(streak.best));
        week.removeAllViews();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            boolean studied = dates.contains(date);
            TextView day = text(studied ? "✓\n" + weekday.format(date) : "·\n" + weekday.format(date),
                    16, studied ? Color.WHITE : INK, offset == 0);
            day.setGravity(Gravity.CENTER);
            day.setPadding(0, dp(6), 0, dp(6));
            GradientDrawable pill = shape(studied ? GREEN : PALE, 10);
            if (offset == 0) {
                pill.setStroke(dp(2), INK);
            }
            day.setBackground(pill);
            day.setContentDescription(fullDate.format(date) + (studied ? ", reviewed" : ", no review recorded"));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            cell.setMarginEnd(offset == 0 ? 0 : dp(4));
            week.addView(day, cell);
        }
    }

    private String days(int value) {
        return value + (value == 1 ? " day" : " days");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void add(LinearLayout parent, View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(margin);
        parent.addView(view, params);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        }
        return text;
    }

    private Button button(String label, int color, int ink) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(ink);
        button.setMinHeight(dp(50));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(0x33000000), shape(color, 14), null));
        return button;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

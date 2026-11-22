# Version 1.0.0 verification

Tests ran on November 22, 2026. The test vocabulary cards were created in the official, unmodified AnkiDroid 2.25.3 release. The companion used the same application ID and signing key as the published APK.

## Android 11 (API 30) virtual phone

| Check | Result |
| --- | --- |
| Connect through the Android permission prompt | The dashboard showed that AnkiDroid was connected and started at **0 days**. |
| Reveal a card without grading it | A forced background check saved no date. |
| Grade **der Apfel** in AnkiDroid | A forced background check saved the date while AnkiDroid was in the foreground. The dashboard showed **1 day** and **Today’s review counted**. |
| Grade **das Brot** on the same day | Only one date was saved. The current and best streaks both stayed at **1 day**. |
| Force-stop and reopen the companion | The saved day and streak persisted. |
| Advance the virtual phone clock by one day | The current streak stayed at **1 day** and the dashboard prompted for today’s review. |
| Advance the virtual phone clock by a second day | The current streak reset to **0 days** and the best streak stayed at **1 day**. The clock was restored after the test. |
| Install the final signed build over the test build | The saved day and connection remained available. |

## Android 15 (API 35) virtual phone

The final signed APK installed and opened with the dashboard clear of the system bars. With AnkiDroid disabled on this virtual phone, it showed **Get AnkiDroid** and explained how to connect. After enabling and setting up AnkiDroid, granting the companion access, and grading **die Zeit**, reopening Review Streak showed **1 day** and **Today’s review counted**. The README screenshot captures this result.

## Local checks

- Seven streak calculation cases passed. They cover empty history, repeat reviews on the same date, consecutive days, yesterday’s grace period, a missed day, best streak, and future dates.
- Android Build Tools verified the signed APK.

To repeat the background query after connecting the companion on an emulator, leave AnkiDroid in the foreground and run:

```sh
adb shell cmd jobscheduler run -f io.github.zahediparsa.reviewstreak 1
```

This invokes the same job Android schedules automatically. System timing varies with battery and idle conditions.

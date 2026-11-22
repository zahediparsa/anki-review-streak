package io.github.zahediparsa.reviewstreak;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;

import java.util.concurrent.TimeUnit;

public final class ReviewJob extends JobService {
    private static final int JOB_ID = 1;
    private CancellationSignal running;

    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler.getPendingJob(JOB_ID) == null) {
            scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, ReviewJob.class))
                    .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                    .setPersisted(true)
                    .build());
        }
    }

    @Override public boolean onStartJob(JobParameters params) {
        CancellationSignal signal = new CancellationSignal();
        running = signal;
        new Thread(() -> {
            try {
                ReviewHistory.refresh(this, signal);
            } catch (OperationCanceledException ignored) {
                // Android stopped the job before the provider query finished.
            } finally {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (running == signal) {
                        running = null;
                        jobFinished(params, false);
                    }
                });
            }
        }, "streak-background").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        if (running != null) {
            running.cancel();
            running = null;
        }
        return false;
    }
}

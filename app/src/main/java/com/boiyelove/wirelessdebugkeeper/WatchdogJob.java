package com.boiyelove.wirelessdebugkeeper;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class WatchdogJob extends JobService {
    @Override public boolean onStartJob(JobParameters params) {
        KeeperService.restoreIfNeeded(this);
        return false;
    }

    @Override public boolean onStopJob(JobParameters params) { return false; }
}

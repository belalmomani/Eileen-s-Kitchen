package com.eileenskitchen.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PollWorker extends Worker {
    public PollWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull @Override public Result doWork() {
        return Notifier.pollOnce(getApplicationContext()) ? Result.success() : Result.retry();
    }
}

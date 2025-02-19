package com.reeman.delige.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import javax.xml.transform.Result;

public class ReturningWorker extends Worker {
    public ReturningWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Background task logic (e.g., keep monitoring network, state updates)
        return Result.success();
    }
}
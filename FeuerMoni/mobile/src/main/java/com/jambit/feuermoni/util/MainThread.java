package com.jambit.feuermoni.util;

import android.os.Handler;
import android.os.Looper;

/**
 * Can be used to post runnables to the UI thread.
 */
public class MainThread {
    private Handler handler;

    public MainThread() {
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void post(Runnable runnable) {
        handler.post(runnable);
    }
}
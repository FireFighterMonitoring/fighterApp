package com.jambit.feuermoni.util;

import android.os.Handler;
import android.os.Looper;

/**
 * Created by tschroep on 16.02.16.
 */
public class BackgroundThread {
    private Handler handler;

    public BackgroundThread() {
        Thread thread = new Thread() {
            public void run() {
                Looper.prepare();
                handler = new Handler();
                Looper.loop();
            }
        };
        thread.start();
    }

    public void post(Runnable runnable) {
        handler.post(runnable);
    }

    public Handler getHandler() {
        return handler;
    }
}

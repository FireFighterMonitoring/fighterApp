package com.jambit.feuermoni.backend;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jambit.feuermoni.backend.model.MonitoringStatus;
import com.jambit.feuermoni.util.BackgroundThread;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by tschroep on 19.02.16.
 */
public class BackendConnection {
    private static final String TAG = BackendConnection.class.getSimpleName();

    /** Media Type used for POST requests */
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String BASE_URL = "https://feuer-moni-backend.herokuapp.com/api/v1";
    private static final String REST_PATH_DATA = "/data";

    private static BackendConnection ourInstance = new BackendConnection();

    private MonitoringStatus monitoringStatus;
    private ScheduledExecutorService scheduler;

    private BackgroundThread backgroundThread = new BackgroundThread();

    /** HTTP client */
    private final OkHttpClient client = new OkHttpClient();

    public static BackendConnection getInstance() {
        return ourInstance;
    }

    private BackendConnection() {
    }

    public void login(String ffId) {
        if (monitoringStatus != null) {
            Log.w(TAG, "YOU ARE ALREADY LOGGED IN - logout fist!");
            return;
        }
        stopScheduler();

        monitoringStatus = new MonitoringStatus(ffId);
        monitoringStatus.status = MonitoringStatus.Status.CONNECTED;
        postStatus(monitoringStatus);
        monitoringStatus.status = MonitoringStatus.Status.NO_DATA;

        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                postStatus(monitoringStatus);
            }
        }, 0, 5, TimeUnit.SECONDS);;
    }

    public boolean isLoggedIn() {
        return monitoringStatus != null;
    }

    public MonitoringStatus getMonitoringStatus() {
        return monitoringStatus;
    }

    public void logout() {
        stopScheduler();

        if (monitoringStatus == null) {
            return;
        }

        monitoringStatus.setVitalSigns(null);
        monitoringStatus.status = MonitoringStatus.Status.DISCONNECTED;
        postStatus(monitoringStatus);

        monitoringStatus = null;
    }

    private void stopScheduler() {
        if (scheduler != null) {
            Log.d(TAG, "stopping scheduler");
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /**
     * Posts JSON data to the FireMoni backend service
     */
    private void postStatus(MonitoringStatus status) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        final String postBody = gson.toJson(status);

        final Request request = new Request.Builder()
                .url(BASE_URL + REST_PATH_DATA)
                .post(RequestBody.create(MEDIA_TYPE_JSON, postBody))
                .build();
        backgroundThread.post(new Runnable() {
            @Override
            public void run() {
                Response response = null;

                try {
                    Log.d(TAG, "POSTing JSON: " + postBody + " to Host: " + BASE_URL);
                    response = client.newCall(request).execute();

                    if (!response.isSuccessful()) {
                        Log.e(TAG, "REQUEST FAILED! (CODE: " + response.code() + " - " + response.body().string());
                    } else {
                        try {
                            Log.d(TAG, response.body().string());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

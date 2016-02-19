package com.jambit.feuermoni.ble;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Created by tschroep on 19.02.16.
 */
public class MonitoringService extends Service {

    private static final String TAG = MonitoringService.class.getSimpleName();

    private final MonitoringServiceBinder serviceBinder = new MonitoringServiceBinder();
    private BluetoothDiscovery bluetoothDiscovery;
    private HeartrateBluetoothDevice observedHeartrateDevice;

    public final PublishSubject<Integer> heartrateObservable = PublishSubject.create();

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class MonitoringServiceBinder extends Binder {
        public MonitoringService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MonitoringService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");

        bluetoothDiscovery = new BluetoothDiscovery(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return serviceBinder;
    }

    public Observable<BluetoothDevice> scanningForHeartrateDevices() {
        Log.d(TAG, "startScanning()");
        return bluetoothDiscovery.scan();
    }

    public void observeHeartrate(HeartrateBluetoothDevice heartrateBluetoothDevice) {

        heartrateBluetoothDevice.stopObservingHeartrate();

        heartrateBluetoothDevice.observeHeartrate()
                .subscribeOn(Schedulers.io())
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer heartrate) {
                        heartrateObservable.onNext(heartrate);
                    }
                });
    }
}

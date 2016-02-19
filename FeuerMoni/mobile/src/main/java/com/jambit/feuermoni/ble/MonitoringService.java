package com.jambit.feuermoni.ble;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Created by tschroep on 19.02.16.
 */
public class MonitoringService extends Service {
    public enum SensorStatus {
        CONNECTED, DISCONNECTED;
    }

    private static final String TAG = MonitoringService.class.getSimpleName();

    private final MonitoringServiceBinder serviceBinder = new MonitoringServiceBinder();
    private BluetoothDiscovery bluetoothDiscovery;
    private HeartrateBluetoothDevice observedHeartrateDevice;

    public final PublishSubject<Integer> heartrateObservable = PublishSubject.create();
    public final PublishSubject<SensorStatus> heartrateSensorStatusObservable = PublishSubject.create();

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

    public Observable<BluetoothDevice> startScanningForHeartrateDevices() {
        Log.d(TAG, "startScanning()");
        return bluetoothDiscovery.scan();
    }

    public void observeHeartrate(final HeartrateBluetoothDevice heartrateBluetoothDevice) {
        if (observedHeartrateDevice != null) {
            heartrateBluetoothDevice.stopObservingHeartrate();
        }

        heartrateSensorStatusObservable.onNext(SensorStatus.CONNECTED);

        observedHeartrateDevice = heartrateBluetoothDevice;
        heartrateBluetoothDevice.observeHeartrate()
                .subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<Integer>() {
                    @Override
                    public void onCompleted() {
                        Log.d(TAG, "observing the device: " + heartrateBluetoothDevice.getName() + " has completed!");
                        heartrateSensorStatusObservable.onNext(SensorStatus.DISCONNECTED);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "error occurred! observing the device: " + heartrateBluetoothDevice.getName() + " has completed!");
                        heartrateSensorStatusObservable.onNext(SensorStatus.DISCONNECTED);
                    }

                    @Override
                    public void onNext(Integer integer) {
                        heartrateObservable.onNext(integer);
                    }
                });
    }
}

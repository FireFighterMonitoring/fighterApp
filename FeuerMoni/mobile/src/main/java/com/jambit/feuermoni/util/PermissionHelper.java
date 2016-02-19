package com.jambit.feuermoni.util;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

/**
 * Created by tschroep on 19.02.16.
 */
public class PermissionHelper {

    public static final int REQUEST_ENABLE_BT = 0;
    public static final int REQUEST_LOACTION_PERMISSION = 1;
    public static final int REQUEST_BODY_SENSORS_PERMISSION = 2;

    /** Static class -> hide constructor... */
    private PermissionHelper() {
    }

    public static boolean isBluetoothAvailable(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public static void requestBluetoothPermission(Activity activity) {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    public static boolean requestLocationPermission(Activity activity) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(activity, "I need location permission!", Toast.LENGTH_LONG).show();

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_LOACTION_PERMISSION);
            }

            return false;
        } else {
            return true;
        }
    }

    public static boolean hasBodySensorsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestBodySensorsPermission(Activity activity) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.BODY_SENSORS)) {
                Toast.makeText(activity, "I need permission to access body sensors!", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BODY_SENSORS}, REQUEST_BODY_SENSORS_PERMISSION);
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.BODY_SENSORS}, REQUEST_BODY_SENSORS_PERMISSION);
            }
        }
    }
}

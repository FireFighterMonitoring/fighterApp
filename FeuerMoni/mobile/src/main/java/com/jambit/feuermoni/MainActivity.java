package com.jambit.feuermoni;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jambit.feuermoni.model.Firefighter;
import com.jambit.feuermoni.util.BackgroundThread;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements MessageApi.MessageListener {

    /** The tag used for logging. */
    private static final String TAG = MainActivity.class.getSimpleName();

    /** Key used to identify the heartrate value in a DataMap */
    private static final String HEARTRATE_KEY = "com.jambit.feuermoni.key.heartrate";

    private static final int MY_PERMISSIONS_REQUEST_BODY_SENSORS = 42;

    /** Media Type used for POST requests */
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String BASE_URL = "http://192.168.232.112:8080/api/v1";
    private static final String REST_PATH_DATA = "/data";

    /** HTTP client */
    private final OkHttpClient client = new OkHttpClient();

    private BackgroundThread backgroundThread = new BackgroundThread();

    /** Google API client, used to connect to Wearable devices... */
    private GoogleApiClient apiClient;

    private ExecutorService executorService;

    /** TextView to change the ffId */
    private EditText ffidTextView;

    /** Button to trigger connection to a wearable device. */
    private Button connectWatchButton;

    /** Button to post JSON data to the FeuerMoni backend service. */
    private Button postJSONButton;

    /** Holds the "state" */
    private int currentMessageValue = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        connectWatchButton = (Button) findViewById(R.id.connect_button);
        connectWatchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Connect Watch button pressed!");
                openWearableConnection();
            }
        });

        postJSONButton = (Button) findViewById(R.id.post_json);
        postJSONButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "POST JSON DATA HERE!");
                postJSONButtonPressed();
            }
        });

        ffidTextView = (EditText) findViewById(R.id.ffid_textview);

        SensorManager sensorManager = ((SensorManager)getSystemService(SENSOR_SERVICE));
        Sensor heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if(heartRateSensor == null) {
            Log.d(TAG, "heart rate sensor is null");
        }

        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor aSensor : sensors) {
            Log.d(TAG, aSensor.getName() + ": " + aSensor.getStringType());
        }

    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "A message has been received!");

        DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
        currentMessageValue = dataMap.getInt(HEARTRATE_KEY);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_BODY_SENSORS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "BODY_SENSORS permission was granted!");
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Without BODY_SENSORS permission this app won't work!", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // other 'case' lines to check for other permissions this app might request
        }
    }

    /**
     * Opens the connection to a wearable device.
     */
    private void openWearableConnection() {
        if (!hasBodySensorsPermission(this)) {
            Log.e(TAG, "Cannot access body sensors!");

            this.requestBodySensorsPermission(this);
            return;
        }

        apiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    public void onConnected(Bundle bundle) {
                        Log.d(TAG, "onConnected()...");
                    }

                    public void onConnectionSuspended(int i) {
                        Log.d(TAG, "ConnectionCallback onConnectionSuspended");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Log.d(TAG, "ConnectionCallback onConnectionFailed");
                    }
                }).build();
        apiClient.connect();

        Wearable.MessageApi.addListener(apiClient, this);
    }

    /**
     * Posts JSON data to the FireMoni backend service
     */
    private void postJSONButtonPressed() {
        Log.d(TAG, "POST JSON here!");

        String ffId = String.valueOf(ffidTextView.getText());

        if (ffId == null || ffId.equals("")) {
            Toast.makeText(this, "Please enter a FFID first!", Toast.LENGTH_LONG).show();
            return;
        }

        Firefighter theFigher = new Firefighter();
        theFigher.ffId = ffId;
        theFigher.heartRate = 666;

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        final String postBody = gson.toJson(theFigher);

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
                        Log.e(TAG, "REQUEST FAILED!");
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

    private void onSendMessagePressed() {
        Log.d(TAG, "Sending message...");
        sendMessageFromBackgroundThread();
    }

    private void sendMessageFromBackgroundThread() {
        this.executorService = Executors.newCachedThreadPool();
        this.executorService.execute(new Runnable() {
            public void run() {
                sendMessage();
            }
        });
    }

    private void sendMessage() {
        DataMap dataMap = new DataMap();
        dataMap.putInt(HEARTRATE_KEY, currentMessageValue);
        PendingResult<NodeApi.GetConnectedNodesResult> nodes = Wearable.NodeApi.getConnectedNodes(apiClient);

        NodeApi.GetConnectedNodesResult nodesResult = nodes.await();
        List<Node> nodeList = nodesResult.getNodes();

        for (Node node : nodeList) {
            Wearable.MessageApi.sendMessage(apiClient, node.getId(), "accumulator", dataMap.toByteArray()).await();
        }
    }

    private boolean hasBodySensorsPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBodySensorsPermission(Activity activity) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(activity,
                Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.BODY_SENSORS)) {

                Toast.makeText(activity, "I need permission to access body sensors!", Toast.LENGTH_LONG).show();

                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, MY_PERMISSIONS_REQUEST_BODY_SENSORS);
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, MY_PERMISSIONS_REQUEST_BODY_SENSORS);
            }
        }
    }
}

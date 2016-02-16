package com.jambit.feuermoni;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
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

    /** Media Type used for POST requests */
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    /** HTTP client */
    private final OkHttpClient client = new OkHttpClient();

    private BackgroundThread backgroundThread = new BackgroundThread();

    /** Google API client, used to connect to Wearable devices... */
    private GoogleApiClient apiClient;

    private ExecutorService executorService;

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
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "A message has been received!");

        DataMap dataMap = DataMap.fromByteArray(messageEvent.getData());
        currentMessageValue = dataMap.getInt(HEARTRATE_KEY);
    }

    /**
     * Opens the connection to a wearable device.
     */
    private void openWearableConnection() {
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

        String postBody = ""
                + "Releases\n"
                + "--------\n"
                + "\n"
                + " * _1.0_ May 6, 2013\n"
                + " * _1.1_ June 15, 2013\n"
                + " * _1.2_ August 11, 2013\n";

        final Request request = new Request.Builder()
                .url("http://192.168.43.146:3000/data")
                .post(RequestBody.create(MEDIA_TYPE_JSON, postBody))
                .build();
        backgroundThread.post(new Runnable() {
            @Override
            public void run() {
                Response response = null;

                try {
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
}

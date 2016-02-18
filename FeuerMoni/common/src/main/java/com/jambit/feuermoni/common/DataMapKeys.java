package com.jambit.feuermoni.common;

/**
 * Created by tschroep on 17.02.16.
 */
public class DataMapKeys {

    /** Key used to identify the heartrate value in a DataMap */
    public static final String HEARTRATE_KEY = "com.jambit.feuermoni.key.heartrate";

    /** Key used to identify the step count value in a DataMap */
    public static final String STEPCOUNT_KEY = "com.jambit.feuermoni.key.stepcount";

    public static final String START_MONITORING_COMMAND = "com.jambit.feuermoni.command.startMonitoring";
    public static final String STOP_MONITORING_COMMAND = "com.jambit.feuermoni.command.stopMonitoring";

    public static final long CONNECTION_TIME_OUT_MS = 100;

    /** Static class - hide constructor... */
    private DataMapKeys() {

    }
}

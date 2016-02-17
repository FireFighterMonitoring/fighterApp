package com.jambit.feuermoni.model;

import com.google.gson.annotations.SerializedName;

/**
 * Created by tschroep on 17.02.16.
 */
public class VitalSigns {

    /** The fire fighter's last known heart rate */
    public final int heartRate;

    /** The fire fighter's step count. */
    public final int stepCount;

    public VitalSigns(int heartRate, int stepCount) {
        this.heartRate = heartRate;
        this.stepCount = stepCount;

    }
}

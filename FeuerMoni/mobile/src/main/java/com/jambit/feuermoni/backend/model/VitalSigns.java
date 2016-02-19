package com.jambit.feuermoni.backend.model;

/**
 * Created by tschroep on 17.02.16.
 */
public class VitalSigns {

    /** The fire fighter's last known heart rate */
    public int heartRate;

    /** The fire fighter's step count. */
    public int stepCount;

    public VitalSigns(int heartRate, int stepCount) {
        this.heartRate = heartRate;
        this.stepCount = stepCount;
    }
}

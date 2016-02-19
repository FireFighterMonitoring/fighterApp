package com.jambit.feuermoni.backend.model;

/**
 {
    "ffId": "rafal",
    "status": "OK",
    "vitalSigns": {
        "heartRate": 210,
        "stepCount": 99
    }
 }
 {
    "ffId": "rafal",
    "status": "NO_DATA"
 }
 */
public class MonitoringStatus {
    public enum Status {
        CONNECTED,

        /** Vital sign monitoring is OK. */
        OK,

        /** Vital sign monitoring is not generating any useful data. */
        NO_DATA,

        /** User has "logged out" from the app */
        DISCONNECTED
    }

    /**  The fire fighter's unique ID */
    public String ffId;

    /** The fire fighter's current status */
    public Status status;

    /** Last konwn vital sign values. */
    private VitalSigns vitalSigns;

    public MonitoringStatus(String ffId) {
        this.ffId = ffId;
        this.status = Status.NO_DATA;
    }

    public void setVitalSigns(VitalSigns vitalSigns) {
        if (vitalSigns == null) {
            status = Status.NO_DATA;
        } else {
            status = Status.OK;
        }

        this.vitalSigns = vitalSigns;
    }

    public VitalSigns getVitalSigns() {
        return vitalSigns;
    }

    public void updateHeartRate(int heartrate) {
        if (vitalSigns != null) {
            vitalSigns.heartRate = heartrate;
        } else {
            setVitalSigns(new VitalSigns(heartrate, -1));
        }
    }

    public void updateSteps(int steps) {
        if (vitalSigns != null) {
            vitalSigns.stepCount = steps;
        } else {
            setVitalSigns(new VitalSigns(-1, steps));
        }
    }
}

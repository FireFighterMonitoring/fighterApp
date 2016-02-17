package com.jambit.feuermoni.model;

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
        /** Vital sign monitoring is OK. */
        OK,

        /** Vital sign monitoring is not generating any useful data. */
        NO_DATA
    }

    /**  The fire fighter's unique ID */
    public String ffId;

    /** The fire fighter's current status */
    public Status status;

    /** Last konwn vital sign values. */
    public final VitalSigns vitalSigns;

    public MonitoringStatus(String ffId) {
        this.ffId = ffId;
        this.status = Status.NO_DATA;
        this.vitalSigns = new VitalSigns();
    }
}

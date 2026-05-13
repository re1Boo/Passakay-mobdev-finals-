package com.usc.passakay;

public class AssignmentResult {
    private int shuttleId;
    private String plateNumber;
    private String stopName;
    private int queuePosition;
    private int etaMinutes;

    public AssignmentResult(int shuttleId, String plateNumber,
                            String stopName, int queuePosition,
                            int etaMinutes) {
        this.shuttleId     = shuttleId;
        this.plateNumber   = plateNumber;
        this.stopName      = stopName;
        this.queuePosition = queuePosition;
        this.etaMinutes    = etaMinutes;
    }

    public int getShuttleId() { return shuttleId; }
    public String getPlateNumber() { return plateNumber; }
    public String getStopName() { return stopName; }
    public int getQueuePosition() { return queuePosition; }
    public int getEtaMinutes() { return etaMinutes; }
}

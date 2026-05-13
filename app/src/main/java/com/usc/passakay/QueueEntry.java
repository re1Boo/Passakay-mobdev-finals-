package com.usc.passakay;

public class QueueEntry {
    private String uid;
    private String stopName;
    private long timestamp;
    private int assignedShuttleId;
    private String assignedPlate;
    private String status;
    private int queuePosition;

    public QueueEntry() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getStopName() { return stopName; }
    public void setStopName(String stopName) { this.stopName = stopName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getAssignedShuttleId() { return assignedShuttleId; }
    public void setAssignedShuttleId(int id) { this.assignedShuttleId = id; }

    public String getAssignedPlate() { return assignedPlate; }
    public void setAssignedPlate(String plate) { this.assignedPlate = plate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getQueuePosition() { return queuePosition; }
    public void setQueuePosition(int pos) { this.queuePosition = pos; }
}

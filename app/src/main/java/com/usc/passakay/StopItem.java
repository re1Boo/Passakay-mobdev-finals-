package com.usc.passakay;

public class StopItem {
    private String stopName;
    private int waitingCount;
    private int distanceMeters;
    private boolean hasWaiting; // controls yellow vs gray Pick up button

    public StopItem(String stopName, int waitingCount, int distanceMeters) {
        this.stopName       = stopName;
        this.waitingCount   = waitingCount;
        this.distanceMeters = distanceMeters;
        this.hasWaiting     = waitingCount > 0;
    }

    public String getStopName()      { return stopName; }
    public int getWaitingCount()     { return waitingCount; }
    public int getDistanceMeters()   { return distanceMeters; }
    public boolean isHasWaiting()    { return hasWaiting; }

    public void setWaitingCount(int waitingCount) {
        this.waitingCount = waitingCount;
        this.hasWaiting = waitingCount > 0;
    }

    public void setDistanceMeters(int distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
}
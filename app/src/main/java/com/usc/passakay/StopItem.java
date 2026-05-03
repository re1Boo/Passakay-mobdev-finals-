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
    public void setStopName(String stopName) { this.stopName = stopName; }
    
    public int getWaitingCount()     { return waitingCount; }
    
    public void setWaitingCount(int waitingCount) {
        this.waitingCount = waitingCount;
        this.hasWaiting = waitingCount > 0;
    }
    
    public int getDistanceMeters()   { return distanceMeters; }
    public void setDistanceMeters(int distanceMeters) { this.distanceMeters = distanceMeters; }

    public boolean isHasWaiting()    { return hasWaiting; }
}

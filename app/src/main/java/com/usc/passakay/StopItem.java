package com.usc.passakay;

public class StopItem {
    private String stopName;
    private int waitingCount;
    private int distanceMeters;

    public StopItem(String stopName, int waitingCount, int distanceMeters) {
        this.stopName       = stopName;
        this.waitingCount   = waitingCount;
        this.distanceMeters = distanceMeters;
    }

    public String getStopName()      { return stopName; }
    public void setStopName(String stopName) { this.stopName = stopName; }
    
    public int getWaitingCount()     { return waitingCount; }
    public void setWaitingCount(int waitingCount) { this.waitingCount = waitingCount; }
    
    public int getDistanceMeters()   { return distanceMeters; }
    public void setDistanceMeters(int distanceMeters) { this.distanceMeters = distanceMeters; }
}
package com.usc.passakay;

public class Shuttle {
    private int shuttleId;
    private int capacity;
    private String plateNumber;
    private String status = "Standby"; // Default value
    private String driverName = "No driver"; // Default value
    private String driverId = ""; // Default value
    private boolean active;
    private String currentDriverId;
    private double currentLat;
    private double currentLng;
    private int currentPassengers;
    private String lastUpdated;

    public Shuttle() {}

    public Shuttle(int shuttleId, String plateNumber) {
        this.shuttleId = shuttleId;
        this.plateNumber = plateNumber;
        this.status = "Standby";
        this.driverName = "No driver";
        this.driverId = "";
        this.active = false;
    }

    public int getShuttleId() { return shuttleId; }
    public void setShuttleId(int shuttleId) { this.shuttleId = shuttleId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity){ this.capacity = capacity; }

    public String getStatus() { 
        return (status == null || status.isEmpty()) ? "Standby" : status; 
    }
    public void setStatus(String status) { this.status = status; }

    public String getDriverName() { 
        return (driverName == null || driverName.isEmpty()) ? "No driver" : driverName; 
    }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCurrentDriverId() { return currentDriverId; }
    public void setCurrentDriverId(String currentDriverId) { this.currentDriverId = currentDriverId; }

    public double getCurrentLat() { return currentLat; }
    public void setCurrentLat(double currentLat) { this.currentLat = currentLat; }

    public double getCurrentLng() { return currentLng; }
    public void setCurrentLng(double currentLng) { this.currentLng = currentLng; }

    public int getCurrentPassengers() { return currentPassengers; }
    public void setCurrentPassengers(int currentPassengers) { this.currentPassengers = currentPassengers; }

    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
}

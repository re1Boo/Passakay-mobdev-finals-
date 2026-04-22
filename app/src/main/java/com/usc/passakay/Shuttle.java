package com.usc.passakay;

public class Shuttle {
    private int shuttleId;
    private int capacity;
    private String plateNumber;
    private String status; // "Deployed", "Standby", "Unavailable"
    private String driverName;
    private String driverId; // To track which driver is currently using this shuttle

    public Shuttle() {}

    public Shuttle(int shuttleId, String plateNumber) {
        this.shuttleId = shuttleId;
        this.plateNumber = plateNumber;
        this.status = "Standby";
        this.driverName = "No driver";
        this.driverId = "";
    }

    public int getShuttleId() { return shuttleId; }
    public void setShuttleId(int shuttleId) { this.shuttleId = shuttleId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity){ this.capacity = capacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
}

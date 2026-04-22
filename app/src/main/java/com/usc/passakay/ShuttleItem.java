package com.usc.passakay;

public class ShuttleItem {
    private String shuttleId;
    private String busName;
    private String driverName;
    private String plateNumber;
    private int eta;
    private boolean isAvailable;
    private double driverLat;
    private double driverLng;
    private int capacity;
    private int currentPassengers;
    private String lastUpdated;

    public ShuttleItem() {}

    public ShuttleItem(String shuttleId, String busName, String driverName,
                       String plateNumber, int eta, boolean isAvailable,
                       double driverLat, double driverLng) {
        this.shuttleId   = shuttleId;
        this.busName     = busName;
        this.driverName  = driverName;
        this.plateNumber = plateNumber;
        this.eta         = eta;
        this.isAvailable = isAvailable;
        this.driverLat   = driverLat;
        this.driverLng   = driverLng;
        this.capacity    = 30;
        this.currentPassengers = 0;
        this.lastUpdated = "Offline";
    }

    public String getShuttleId() { return shuttleId; }
    public String getBusName() { return busName; }
    public String getDriverName() { return driverName; }
    public String getPlateNumber() { return plateNumber; }
    public int getEta() { return eta; }
    public boolean isAvailable() { return isAvailable; }
    public double getDriverLat() { return driverLat; }
    public double getDriverLng() { return driverLng; }
    public int getCapacity() { return capacity; }
    public int getCurrentPassengers() { return currentPassengers; }
    public String getLastUpdated() { return lastUpdated; }

    public void setShuttleId(String shuttleId) { this.shuttleId = shuttleId; }
    public void setBusName(String busName) { this.busName = busName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public void setEta(int eta) { this.eta = eta; }
    public void setAvailable(boolean available) { isAvailable = available; }
    public void setDriverLat(double driverLat) { this.driverLat = driverLat; }
    public void setDriverLng(double driverLng) { this.driverLng = driverLng; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setCurrentPassengers(int currentPassengers) { this.currentPassengers = currentPassengers; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
}

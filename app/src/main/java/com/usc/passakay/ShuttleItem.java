package com.usc.passakay;

public class ShuttleItem {
    private String shuttleId;
    private String busName;
    private String driverName;
    private String plateNumber;
    private int eta;
    private boolean isAvailable;
    private boolean isStandby;   // NEW: green "Standby" button when true
    private double driverLat;
    private double driverLng;

    public ShuttleItem() {}

    /** Backward-compatible constructor (isStandby defaults to false). */
    public ShuttleItem(String shuttleId, String busName, String driverName,
                       String plateNumber, int eta, boolean isAvailable,
                       double driverLat, double driverLng) {
        this(shuttleId, busName, driverName, plateNumber, eta,
                isAvailable, false, driverLat, driverLng);
    }

    /** Full constructor including standby flag. */
    public ShuttleItem(String shuttleId, String busName, String driverName,
                       String plateNumber, int eta, boolean isAvailable,
                       boolean isStandby,
                       double driverLat, double driverLng) {
        this.shuttleId   = shuttleId;
        this.busName     = busName;
        this.driverName  = driverName;
        this.plateNumber = plateNumber;
        this.eta         = eta;
        this.isAvailable = isAvailable;
        this.isStandby   = isStandby;
        this.driverLat   = driverLat;
        this.driverLng   = driverLng;
    }

    public String getShuttleId()  { return shuttleId; }
    public String getBusName()    { return busName; }
    public String getDriverName() { return driverName; }
    public String getPlateNumber(){ return plateNumber; }
    public int getEta()           { return eta; }
    public boolean isAvailable()  { return isAvailable; }
    public boolean isStandby()    { return isStandby; }
    public double getDriverLat()  { return driverLat; }
    public double getDriverLng()  { return driverLng; }

    public void setShuttleId(String shuttleId)   { this.shuttleId = shuttleId; }
    public void setBusName(String busName)        { this.busName = busName; }
    public void setDriverName(String driverName)  { this.driverName = driverName; }
    public void setPlateNumber(String p)          { this.plateNumber = p; }
    public void setEta(int eta)                   { this.eta = eta; }
    public void setAvailable(boolean available)   { isAvailable = available; }
    public void setStandby(boolean standby)       { isStandby = standby; }
    public void setDriverLat(double driverLat)    { this.driverLat = driverLat; }
    public void setDriverLng(double driverLng)    { this.driverLng = driverLng; }
}
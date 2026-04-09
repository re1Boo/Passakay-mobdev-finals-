package com.usc.passakay;

public class Trip {
    private int tripId;
    private int driverId;
    private int passengerId;
    private int pickupId;
    private int destinationId;
    private int shuttleId;
    private String date;
    private String time;

    public Trip() {}

    public Trip(int tripId, int driverId, int passengerId,
                int pickupId, int destinationId,
                int shuttleId, String date, String time) {
        this.tripId = tripId;
        this.driverId = driverId;
        this.passengerId = passengerId;
        this.pickupId = pickupId;
        this.destinationId = destinationId;
        this.shuttleId = shuttleId;
        this.date = date;
        this.time = time;
    }

    public int getTripId() { return tripId; }
    public void setTripId(int tripId) { this.tripId = tripId; }

    public int getDriverId() { return driverId; }
    public void setDriverId(int driverId) { this.driverId = driverId; }

    public int getPassengerId() { return passengerId; }
    public void setPassengerId(int passengerId) { this.passengerId = passengerId; }

    public int getPickupId() { return pickupId; }
    public void setPickupId(int pickupId) { this.pickupId = pickupId; }

    public int getDestinationId() { return destinationId; }
    public void setDestinationId(int destinationId) { this.destinationId = destinationId; }

    public int getShuttleId() { return shuttleId; }
    public void setShuttleId(int shuttleId) { this.shuttleId = shuttleId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}

package com.usc.passakay;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    private int userId;
    private int departmentId;
    private int courseId;
    private String firstName;
    private String lastName;
    private String role;
    private String status;
    private String email;
    private String studentId;
    private String profileImageUrl;
    private boolean isWaiting;
    private String lastScannedStop;
    private int assignedShuttleId;
    private double currentLat;
    private double currentLng;
    private String waitingAt;

    public User() {}

    public User(int userId, int departmentId, int courseId, String firstName, String lastName,
                String role, String status, String email, String studentId, String profileImageUrl) {
        this.userId = userId;
        this.departmentId = departmentId;
        this.courseId = courseId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.status = status;
        this.email = email;
        this.studentId = studentId;
        this.profileImageUrl = profileImageUrl;
        this.isWaiting = false;
        this.lastScannedStop = "";
        this.assignedShuttleId = -1;
    }

    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getDepartmentId() { return departmentId; }
    public void setDepartmentId(int departmentId) { this.departmentId = departmentId; }
    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public boolean isWaiting() { return isWaiting; }
    public void setWaiting(boolean waiting) { isWaiting = waiting; }
    public String getLastScannedStop() { return lastScannedStop; }
    public void setLastScannedStop(String lastScannedStop) { this.lastScannedStop = lastScannedStop; }
    public int getAssignedShuttleId() { return assignedShuttleId; }
    public void setAssignedShuttleId(int assignedShuttleId) { this.assignedShuttleId = assignedShuttleId; }
    public double getCurrentLat() { return currentLat; }
    public void setCurrentLat(double currentLat) { this.currentLat = currentLat; }
    public double getCurrentLng() { return currentLng; }
    public void setCurrentLng(double currentLng) { this.currentLng = currentLng; }
    public String getWaitingAt() { return waitingAt; }
    public void setWaitingAt(String waitingAt) { this.waitingAt = waitingAt; }
}

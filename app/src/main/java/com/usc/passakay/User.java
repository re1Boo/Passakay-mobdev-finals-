package com.usc.passakay;

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
    }

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
}
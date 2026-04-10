package com.usc.passakay;

public class User {
    private int userId;
    private int departmentId;
    private int courseId;
    private String role;
    private String status;
    private String email;       // ← add this
    private String studentId;   // ← add this

    public User() {}

    public User(int userId, int departmentId, int courseId,
                String role, String status,
                String email, String studentId) {
        this.userId = userId;
        this.departmentId = departmentId;
        this.courseId = courseId;
        this.role = role;
        this.status = status;
        this.email = email;
        this.studentId = studentId;
    }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getDepartmentId() { return departmentId; }
    public void setDepartmentId(int departmentId) { this.departmentId = departmentId; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
}
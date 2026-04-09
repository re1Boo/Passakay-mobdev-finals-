package com.usc.passakay;

import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DataSeeder {

    private final DatabaseReference db;
    private static final String TAG = "DataSeeder";

    public DataSeeder() {
        db = FirebaseDatabase.getInstance("https://passakay-c787c-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference();
    }

    public void seedAll() {
        seedDepartments();
        seedCourses();
        seedShuttles();
        seedShuttleStops();
    }

    private void seedDepartments() {
        Department[] departments = {
                new Department(1, "School of Arts and Sciences"),
                new Department(2, "School of Engineering"),
                new Department(3, "School of Business and Economics"),
                new Department(4, "School of Fine Arts and Design"),
                new Department(5, "School of Health Care Professions"),
                new Department(6, "School of Law and Governance")
        };
        for (Department dept : departments) {
            db.child("departments").child(String.valueOf(dept.getDepartmentId())).setValue(dept)
                    .addOnSuccessListener(a -> Log.d(TAG, "Department seeded: " + dept.getDepartmentName()))
                    .addOnFailureListener(e -> Log.e(TAG, "Error: " + e.getMessage()));
        }
    }

    private void seedCourses() {
        Course[] courses = {
                new Course(1,  "BS Computer Engineering"),
                new Course(2,  "BS Civil Engineering"),
                new Course(3,  "BS Electrical Engineering"),
                new Course(4,  "BS Electronics Engineering"),
                new Course(5,  "BS Mechanical Engineering"),
                new Course(6,  "BS Chemical Engineering"),
                new Course(7,  "BS Industrial Engineering"),

                // College of Computer Studies
                new Course(8,  "BS Computer Science"),
                new Course(9,  "BS Information Technology"),
                new Course(10, "BS Information Systems"),

                // College of Business and Economics
                new Course(11, "BS Accountancy"),
                new Course(12, "BS Business Administration - Financial Management"),
                new Course(13, "BS Business Administration - Marketing Management"),
                new Course(14, "BS Business Administration - Human Resource Management"),
                new Course(15, "BS Economics"),
                new Course(16, "BS Entrepreneurship"),

                // College of Architecture and Fine Arts
                new Course(17, "BS Architecture"),
                new Course(18, "BS Fine Arts"),
                new Course(19, "BS Interior Design"),

                // College of Education
                new Course(20, "Bachelor of Elementary Education"),
                new Course(21, "Bachelor of Secondary Education - English"),
                new Course(22, "Bachelor of Secondary Education - Mathematics"),
                new Course(23, "Bachelor of Secondary Education - Science"),
                new Course(24, "Bachelor of Secondary Education - Filipino"),
                new Course(25, "Bachelor of Physical Education"),

                // College of Law
                new Course(26, "Juris Doctor"),

                // College of Nursing
                new Course(27, "BS Nursing"),

                // College of Pharmacy
                new Course(28, "BS Pharmacy"),

                // College of Sciences
                new Course(29, "BS Biology"),
                new Course(30, "BS Chemistry"),
                new Course(31, "BS Physics"),
                new Course(32, "BS Mathematics"),
                new Course(33, "BS Environmental Science"),

                // College of Arts and Sciences
                new Course(34, "AB Communication"),
                new Course(35, "AB English Language Studies"),
                new Course(36, "AB Filipino"),
                new Course(37, "AB History"),
                new Course(38, "AB Philosophy"),
                new Course(39, "AB Political Science"),
                new Course(40, "AB Psychology"),
                new Course(41, "AB Sociology"),

                // College of Social Work
                new Course(42, "BS Social Work"),

                // College of Theology
                new Course(43, "Bachelor of Sacred Theology"),
                new Course(44, "AB Theology")
        };
        for (Course course : courses) {
            db.child("courses").child(String.valueOf(course.getCourseId())).setValue(course)
                    .addOnSuccessListener(a -> Log.d(TAG, "Course seeded: " + course.getCourseName()))
                    .addOnFailureListener(e -> Log.e(TAG, "Error: " + e.getMessage()));
        }
    }

    private void seedShuttles() {
        Shuttle[] shuttles = {
                new Shuttle(1, "ABC 1234"),
                new Shuttle(2, "XYZ 5678")
        };
        for (Shuttle shuttle : shuttles) {
            db.child("shuttles").child(String.valueOf(shuttle.getShuttleId())).setValue(shuttle)
                    .addOnSuccessListener(a -> Log.d(TAG, "Shuttle seeded: " + shuttle.getPlateNumber()))
                    .addOnFailureListener(e -> Log.e(TAG, "Error: " + e.getMessage()));
        }
    }

    private void seedShuttleStops() {
        ShuttleStop[] stops = {
                new ShuttleStop(1, "Bunzel",        10.351988679046595, 123.91350931757974),
                new ShuttleStop(2, "Portal Terminal", 10.353133958676839, 123.91395314438697),
                new ShuttleStop(3, "USC Dormitory",     10.354723899012651, 123.91212867070087),
                new ShuttleStop(4, "PE Bulding",          10.355426033259947, 123.91097955144363),
                new ShuttleStop(6, "SHCP Building",          10.355352813567361, 123.91040719449803),
                new ShuttleStop(7, "LRC Building",          10.353962840499877, 123.9091986435636),
                new ShuttleStop(8, "MR Building",          10.35344784070996, 123.90988370368238),
                new ShuttleStop(9, "SAFAD Building",          10.352892209800075, 123.91050896252874),
                new ShuttleStop(10, "Chapel",          10.352712231386858, 123.91142525690631)
        };
        for (ShuttleStop stop : stops) {
            db.child("shuttleStops").child(String.valueOf(stop.getStopId())).setValue(stop)
                    .addOnSuccessListener(a -> Log.d(TAG, "Stop seeded: " + stop.getStopName()))
                    .addOnFailureListener(e -> Log.e(TAG, "Error: " + e.getMessage()));
        }
    }
}
package com.usc.passakay;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {

    private static final String TAG = "ScannerActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;

    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean isScanning = false;
    private String destination = "";

    // Hardcoded stop coordinates for verification (Synced with DataSeeder)
    private final Map<String, LatLng> stopCoords = new HashMap<String, LatLng>() {{
        put("Bunzel", new LatLng(10.351988679046595, 123.91350931757974));
        put("Portal Terminal", new LatLng(10.353133958676839, 123.91395314438697));
        put("USC Dormitory", new LatLng(10.354723899012651, 123.91212867070087));
        put("PE Building", new LatLng(10.355426033259947, 123.91097955144363));
        put("SHCP Building", new LatLng(10.355352813567361, 123.91040719449803));
        put("LRC Building", new LatLng(10.353962840499877, 123.9091986435636));
        put("MR Building", new LatLng(10.35344784070996, 123.90988370368238));
        put("SAFAD Building", new LatLng(10.352892209800075, 123.91050896252874));
        put("Chapel", new LatLng(10.352712231386858, 123.91142525690631));
        put("AMONG BALAY", new LatLng(10.352712231386858, 123.91142525690631));
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        destination = getIntent().getStringExtra("destination");
        if (destination == null) destination = "";

        previewView = findViewById(R.id.previewView);
        ImageButton btnClose = findViewById(R.id.btnClose);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (checkPermissions()) {
            startCamera();
        } else {
            requestPermissions();
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
        }, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera and Location permissions are required.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                BarcodeScanner scanner = BarcodeScanning.getClient();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    if (isScanning) {
                        image.close();
                        return;
                    }

                    @SuppressWarnings("UnsafeOptInUsageError")
                    android.media.Image mediaImage = image.getImage();
                    if (mediaImage != null) {
                        InputImage inputImage = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());

                        scanner.process(inputImage)
                                .addOnSuccessListener(barcodes -> {
                                    if (barcodes.size() > 0 && !isScanning) {
                                        isScanning = true;
                                        String qrContent = barcodes.get(0).getRawValue();
                                        Log.d(TAG, "Barcode detected: " + qrContent);
                                        onQRScanned(qrContent);
                                    }
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                                .addOnCompleteListener(task -> image.close());
                    } else {
                        image.close();
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onQRScanned(String qrContent) {
        if (qrContent == null) {
            isScanning = false;
            return;
        }

        String stopName = normalizeStopName(qrContent);
        
        // Block if pick-up and destination are the same
        if (!destination.isEmpty() && stopName.equalsIgnoreCase(destination)) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Pick-up and Destination cannot be the same!", Toast.LENGTH_LONG).show();
                isScanning = false;
            });
            return;
        }

        // Location verification
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            isScanning = false;
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng stopLatLng = stopCoords.get(stopName);
                if (stopLatLng != null) {
                    float[] results = new float[1];
                    Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                            stopLatLng.latitude, stopLatLng.longitude, results);
                    
                    if (results[0] > 50) {
                        Toast.makeText(this, "You must be physically at the stop to join the queue.", Toast.LENGTH_SHORT).show();
                        isScanning = false;
                        return;
                    }
                }
                proceedToJoinQueue(stopName);
            } else {
                Toast.makeText(this, "Unable to get current location. Please ensure GPS is on.", Toast.LENGTH_SHORT).show();
                isScanning = false;
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isScanning = false;
        });
    }

    private void proceedToJoinQueue(String stopName) {
        QueueManager queueManager = new QueueManager();
        runOnUiThread(() -> Toast.makeText(this, "Finding your shuttle at " + stopName + "...", Toast.LENGTH_SHORT).show());

        queueManager.joinQueueAndAllocate(stopName, destination,
                result -> {
                    AIShuttleManager aiManager = new AIShuttleManager(this);
                    aiManager.runAIManagement();

                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "✅ Shuttle Assigned!\n" +
                                        "Bus " + result.getShuttleId() +
                                        " • " + result.getPlateNumber() +
                                        "\nQueue #" + result.getQueuePosition() +
                                        "\nETA: ~" + result.getEtaMinutes() + " mins",
                                Toast.LENGTH_LONG).show();
                        finish();
                    });
                },
                error -> runOnUiThread(() -> {
                    if ("No available shuttles right now".equals(error)) {
                        Toast.makeText(this,
                                "You joined the queue at " + stopName + " going to " + destination +
                                        "\nWaiting for a shuttle to become available...",
                                Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
                        isScanning = false;
                    }
                })
        );
    }

    private String normalizeStopName(String raw) {
        if (raw == null) return "Unknown";
        String lower = raw.toLowerCase().trim();
        if (lower.contains("bunzel")) return "Bunzel";
        if (lower.contains("safad")) return "SAFAD Building";
        if (lower.contains("pe")) return "PE Building";
        if (lower.contains("mr")) return "MR Building";
        if (lower.contains("lrc")) return "LRC Building";
        if (lower.contains("shcp")) return "SHCP Building";
        if (lower.contains("portal")) return "Portal Terminal";
        if (lower.contains("dorm")) return "USC Dormitory";
        if (lower.contains("chapel")) return "Chapel";
        if (lower.contains("among")) return "AMONG BALAY";
        return raw.replace(".com", "").trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}

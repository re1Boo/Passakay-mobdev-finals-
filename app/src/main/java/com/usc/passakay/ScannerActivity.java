package com.usc.passakay;

import android.Manifest;
import android.content.pm.PackageManager;
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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {

    private static final String TAG = "ScannerActivity";
    private static final int PERMISSION_CODE_CAMERA = 1;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;
    private boolean isScanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        previewView = findViewById(R.id.previewView);
        ImageButton btnClose = findViewById(R.id.btnClose);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CODE_CAMERA);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE_CAMERA) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
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
        QueueManager queueManager = new QueueManager();

        runOnUiThread(() -> Toast.makeText(this, "Finding your shuttle at " + stopName + "...", Toast.LENGTH_SHORT).show());

        queueManager.joinQueueAndAllocate(stopName,
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
                                "You joined the queue at " + stopName +
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

        // Remove .com if present
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

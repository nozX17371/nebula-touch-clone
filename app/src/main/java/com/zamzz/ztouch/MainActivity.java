package com.zamzzz.ztouch;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 80, 40, 40);

        TextView title = new TextView(this);
        title.setText("Z TOUCH - FULL VERSION");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 40);
        root.addView(title);

        Button btnAccessibility = new Button(this);
        btnAccessibility.setText("1. Aktifkan Aksesibilitas");
        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        root.addView(btnAccessibility);

        Button btnOverlay = new Button(this);
        btnOverlay.setText("2. Izinkan Overlay");
        btnOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQ);
                } else {
                    Toast.makeText(this, "Overlay sudah diizinkan", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnOverlay);

        Button btnStart = new Button(this);
        btnStart.setText("3. Jalankan Z Touch");
        btnStart.setOnClickListener(v -> {
            if (!isAccessibilityEnabled()) {
                Toast.makeText(this, "Aktifkan aksesibilitas dulu!", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Z Touch siap. Buka game lo.", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        });
        root.addView(btnStart);

        TextView info = new TextView(this);
        info.setText("\n\nCara pakai:\n" +
                "- Drag tombol overlay buat pindah posisi\n" +
                "- Tombol CROSSHAIR: pilih titik tap\n" +
                "- Tombol TAP: mulai/stop auto tap\n" +
                "- Tombol SWIPE: mulai/stop auto swipe\n" +
                "- Tombol REC: rekam macro\n" +
                "- Tombol PLAY: jalankan macro\n" +
                "- Tombol SET: atur kecepatan\n" +
                "- Tombol X: tutup overlay");
        info.setTextSize(12);
        root.addView(info);

        setContentView(root);
    }

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" + TouchService.class.getCanonicalName();
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabled != null && enabled.contains(service);
        } catch (Exception e) {
            return false;
        }
    }
                            }
